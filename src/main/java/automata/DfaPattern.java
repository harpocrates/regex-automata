package automata;

import automata.codegen.CompiledDfa;
import automata.graph.MatchMode;
import automata.graph.Tdfa;
import automata.graph.Tnfa;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;

/**
 * DFA-backed regular expression pattern.
 *
 * <p>This aspires to have an interface and semantics similar to {@code Pattern}
 * and {@code Matcher} from the JDK's {@code java.util.regex}, but with better
 * algorithmic complexity and much faster constant overheads. On the flip side,
 * compilation of the pattern may take longer, it may use more memory, and some
 * backtracking features are not supported.
 *
 * @author Alec Theriault
 */
abstract public class DfaPattern {

  private final String pattern;

  protected DfaPattern(String pattern) {
    this.pattern = pattern;
  }

  /**
   * Compiles the given regular expression into an efficient pattern.
   *
   * <p>The output will generate a fresh subclass where the matching methods use
   * DFAs that encode state transitions directly with {@code GOTO}.
   *
   * @param regex source of the pattern
   * @return compiled DFA pattern
   */
  public static DfaPattern compile(String regex)
  throws java.io.IOException, PatternSyntaxException, IllegalAccessException, NoSuchMethodException, InstantiationException, InvocationTargetException {
    return new CompiledDfaPattern(regex);
  }

  /**
   * Compiles the given regular expression into an efficient, but interpreted
   * pattern.
   *
   * <p>This functions like {@link #compile(String regex)}, but the DFAs are
   * walked inside a (small and still efficient) interpreter. No bytecode is
   * generated.
   *
   * @param regex source of the pattern
   * @return compiled DFA pattern
   */
  public static InterpretableDfaPattern interpreted(String regex) throws PatternSyntaxException {
    return new InterpretableDfaPattern(regex);
  }

  /**
   * Returns initial regular expression from which the pattern was derived.
   *
   * @return source of the pattern
   */
  public String pattern() {
    return pattern;
  }

  /**
   * Create a matcher for the matching the current pattern against the input.
   *
   * @param input string against which to match
   * @return matcher for the pattern against the input
   */
  public abstract DfaMatcher matcher(CharSequence input);

  /**
   * Compute the number of groups in the pattern.
   *
   * @return number of capture groups in the pattern
   */
  public abstract int groupCount();


  final static public class CompiledDfaPattern extends DfaPattern {

    // Method handle for creating the matcher from an input
    private final MethodHandle constructMatcher;
    public final int groupCount;

    @Override
    public String toString() {
      return "DfaPattern.CompiledDfaPattern(" + pattern() + ")";
    }

    @Override
    public int groupCount() {
      // Any of the 3 DFAs would return the same count
      return groupCount;
    }

    @Override
    public DfaMatcher matcher(CharSequence input) {
      try {
        return (DfaMatcher)constructMatcher.invoke((DfaPattern)this, input);
      } catch (Throwable error) {
        throw new IllegalStateException("Failed to construct matcher", error);
      }
    }

    public CompiledDfaPattern(
      String pattern,
      int flags,
      boolean printDebugInfo,
      boolean optimized
    ) throws PatternSyntaxException, IllegalAccessException, NoSuchMethodException {
      super(pattern);

      // NFAs
      final Tnfa nfaWithoutWildcard = Tnfa.parse(pattern, flags, true, false);
      final Tnfa nfaWithWildcard = Tnfa.parse(pattern, flags, true, true);

      // DFAs
      final Tdfa matchesDfa = Tdfa.fromTnfa(nfaWithoutWildcard, MatchMode.FULL, optimized);
      final Tdfa lookingAtDfa = Tdfa.fromTnfa(nfaWithoutWildcard, MatchMode.PREFIX, optimized);
      final Tdfa findDfa = Tdfa.fromTnfa(nfaWithWildcard, MatchMode.PREFIX, optimized);

      final String className = "automata/DfaMatcher$Compiled";
      final int classFlags = Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
      final byte[] classBytes = CompiledDfa
        .generateDfaMatcherSubclass(
          matchesDfa,
          lookingAtDfa,
          findDfa,
          className,
          classFlags,
          printDebugInfo
        )
        .toByteArray();

      // Load the class and get a handle on the constructor
      final MethodHandles.Lookup lookup = MethodHandles
        .lookup()
        .defineHiddenClass(classBytes, true);
      this.constructMatcher = lookup.findConstructor(
        lookup.lookupClass(),
        MethodType.methodType(void.class, DfaPattern.class, CharSequence.class)
      );

      // Any of the 3 DFAs would return the same count
      this.groupCount = matchesDfa.groupCount();
    }

    public CompiledDfaPattern(
      String pattern
    ) throws PatternSyntaxException, IllegalAccessException, NoSuchMethodException {
      this(pattern, 0, false, true);
    }
  }

  final static public class InterpretableDfaPattern extends DfaPattern {
    private final boolean printDebugInfo;

    // Intermediate states
    public final Tdfa matchesDfa;
    public final Tdfa lookingAtDfa;
    public final Tdfa findDfa;
    public final int groupCount;

    @Override
    public String toString() {
      return "DfaPattern.InterpretableDfaPattern(" + pattern() + ")";
    }

    @Override
    public int groupCount() {
      return groupCount;
    }

    @Override
    public DfaMatcher matcher(CharSequence input) {
      return new DfaMatcher(this, input) {

        @Override
        public boolean matches() {
          this.successfulMatch = false;
          Arrays.fill(this.groups, -1);
          return postMatchUpdate(matchesDfa.captureSimulate(
            input,
            this.groups,
            this.currentStart,
            this.regionEnd,
            printDebugInfo
          ));
        }

        @Override
        public boolean lookingAt() {
          this.successfulMatch = false;
          Arrays.fill(this.groups, -1);
          return postMatchUpdate(lookingAtDfa.captureSimulate(
            input,
            this.groups,
            this.currentStart,
            this.regionEnd,
            printDebugInfo
          ));
        }

        @Override
        public boolean find() {
          this.successfulMatch = false;
          Arrays.fill(this.groups, -1);
          return postMatchUpdate(findDfa.captureSimulate(
            input,
            this.groups,
            this.currentStart,
            this.regionEnd,
            printDebugInfo
          ));
        }
      };
    }

    public InterpretableDfaPattern(
      String pattern,
      int flags,
      boolean printDebugInfo,
      boolean optimized
    ) throws PatternSyntaxException {
      super(pattern);
      this.printDebugInfo = printDebugInfo;

      final Tnfa nfaWithoutWildcard = Tnfa.parse(pattern, flags, true, false);
      final Tnfa nfaWithWildcard = Tnfa.parse(pattern, flags, true, true);

      this.matchesDfa = Tdfa.fromTnfa(nfaWithoutWildcard, MatchMode.FULL, optimized);
      this.lookingAtDfa = Tdfa.fromTnfa(nfaWithoutWildcard, MatchMode.PREFIX, optimized);
      this.findDfa = Tdfa.fromTnfa(nfaWithWildcard, MatchMode.PREFIX, optimized);

      // Any of the 3 DFAs would return the same count
      this.groupCount = matchesDfa.groupCount();
    }

    public InterpretableDfaPattern(String pattern) throws PatternSyntaxException {
      this(pattern, 0, false, true);
    }
  }
}
