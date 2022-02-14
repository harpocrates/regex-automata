package automata;

import automata.codegen.CompiledDfa;
import automata.graph.MatchMode;
import automata.graph.Tdfa;
import automata.graph.Tnfa;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;

/**
 * DFA-backed regular expression pattern.
 *
 * This aspires to have an interface and semantics similar to {@code Pattern}
 * and {@code Matcher} from the JDK's {@code java.util.regex}, but with better
 * algorithmic complexity and much faster constant overheads. On the flip side,
 * compilation of the pattern may take longer, it may use more memory, and some
 * backtracking features are not supported.
 *
 * TODO: make this more like the Java API (with a `Matcher` intermediate object
 *       that carries capture groups as fields)
 *
 * @author Alec Theriault
 */
public interface DfaPattern {

  /**
   * Compiles the given regular expression into an efficient pattern.
   *
   * The output will generate a fresh subclass where the matching methods use
   * DFAs that encode state transitions directly with `GOTO`.
   *
   * @param regex source of the pattern
   * @return compiled DFA pattern
   */
  public static DfaPattern compile(String regex)
  throws java.io.IOException, PatternSyntaxException, IllegalAccessException, NoSuchMethodException, InstantiationException, InvocationTargetException {
    final boolean optimized = true;
    final boolean debugInfo = false;

    // NFAs
    final Tnfa nfaWithoutWildcard = Tnfa.parse(regex, true, false);
    final Tnfa nfaWithWildcard = Tnfa.parse(regex, true, true);

    // DFAs
    final Tdfa matchesDfa = Tdfa.fromTnfa(nfaWithoutWildcard, MatchMode.FULL, optimized);
    final Tdfa lookingAtDfa = Tdfa.fromTnfa(nfaWithoutWildcard, MatchMode.PREFIX, optimized);
    final Tdfa findDfa = Tdfa.fromTnfa(nfaWithWildcard, MatchMode.PREFIX, optimized);

    final String className = "automata/DfaPattern$Compiled";
    final int classFlags = Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
    final byte[] classBytes = CompiledDfa
      .generateDfaPatternSubclass(
        regex,
        matchesDfa,
        lookingAtDfa,
        findDfa,
        className,
        classFlags,
        debugInfo
      )
      .toByteArray();
    final Object compiled = MethodHandles
      .lookup()
      .defineHiddenClass(classBytes, true)
      .lookupClass()
      .getDeclaredConstructor()
      .newInstance();

    return (DfaPattern)compiled;
  }

  /**
   * Compiles the given regular expression into an efficient, but interpreted
   * pattern.
   *
   * This functions like {@link #compile(String regex)}, but the DFAs are
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
   * Match an input string to the DFA pattern and extract capture groups
   *
   * @param input character sequence to match
   * @return extracted capture groups, or `null` if the pattern didn't match
   */
  public default ArrayMatchResult captureMatch(CharSequence input) {
    return captureMatch(input, 0, input.length());
  }

  /**
   * Match an input string to the DFA pattern and extract capture groups
   *
   * @param input character sequence to match
   * @param startOffset offset in the input where the pattern starts matching
   * @param endOffset offset in the input where the pattern ends matching
   * @return extracted capture groups, or `null` if the pattern didn't match
   */
  public ArrayMatchResult captureMatch(CharSequence input, int startOffset, int endOffset);

  /**
   * Match a prefix of the input string to the DFA pattern and extract capture
   * groups
   *
   * @param input character sequence to match
   * @param startOffset offset in the input where the pattern starts matching
   * @param endOffset offset in the input where the pattern ends matching
   * @return extracted capture groups, or `null` if the pattern didn't match
   */
  public ArrayMatchResult captureLookingAt(CharSequence input, int startOffset, int endOffset);

  /**
   * Match a prefix of the input string to the DFA pattern and extract capture
   * groups
   *
   * @param input character sequence to match
   * @return extracted capture groups, or `null` if the pattern didn't match
   */
  public default ArrayMatchResult captureLookingAt(CharSequence input) {
    return captureLookingAt(input, 0, input.length());
  }

  /**
   * Find the first instance of the DFA pattern inside the input and extract
   * capture groups
   *
   * @param input character sequence to match
   * @param startOffset offset in the input where the pattern starts matching
   * @param endOffset offset in the input where the pattern ends matching
   * @return extracted capture groups, or `null` if the pattern didn't match
   */
  public ArrayMatchResult captureFind(CharSequence input, int startOffset, int endOffset);

  /**
   * Find the first instance of the DFA pattern inside the input and extract
   * capture groups
   *
   * @param input character sequence to match
   * @return extracted capture groups, or `null` if the pattern didn't match
   */
  public default ArrayMatchResult captureFind(CharSequence input) {
    return captureFind(input, 0, input.length());
  }

  public default Stream<ArrayMatchResult> results(CharSequence input) {
    final int endOffset = input.length();
    return Stream.iterate(
      captureFind(input, 0, endOffset),
      result -> result != null,
      result -> captureFind(input, result.end(), endOffset)
    );
  }

  public default String replaceAll(CharSequence input, String replacement) {
    final StringBuilder replaced = new StringBuilder();

    int startOffset = 0;
    final int endOffset = input.length();
    while (true) {
      final var result = captureFind(input, startOffset, endOffset);
      if (result == null) {
        replaced.append(input.subSequence(startOffset, endOffset));
        break;
      } else {
        if (startOffset != result.start()) {
          replaced.append(input.subSequence(startOffset, result.start()));
        }
        replaced.append(replacement);
        startOffset = result.end();
      }
    }

    return replaced.toString();
  }

  /**
   * Returns initial regular expression from which the pattern was derived.
   *
   * @return source of the pattern
   */
  public String pattern();

  /**
   * Compute the number of groups in the pattern.
   *
   * @return number of capture groups in the pattern
   */
  public int groupCount();

  final static public class InterpretableDfaPattern implements DfaPattern {
    public final String pattern;
    private final boolean printDebugInfo;

    // Intermediate states
    public final Tdfa matchesDfa;
    public final Tdfa lookingAtDfa;
    public final Tdfa findDfa;
    public final int groupCount;

    public String pattern() {
      return pattern;
    }

    public ArrayMatchResult captureMatch(CharSequence input, int startOffset, int endOffset) {
      return matchesDfa.captureSimulate(input, startOffset, endOffset, printDebugInfo);
    }

    public ArrayMatchResult captureLookingAt(CharSequence input, int startOffset, int endOffset) {
      return lookingAtDfa.captureSimulate(input, startOffset, endOffset, printDebugInfo);
    }

    public ArrayMatchResult captureFind(CharSequence input, int startOffset, int endOffset) {
      return findDfa.captureSimulate(input, startOffset, endOffset, printDebugInfo);
    }

    public int groupCount() {
      // Any of the 3 DFAs would return the same count
      return groupCount;
    }

    public InterpretableDfaPattern(
      String pattern,
      boolean printDebugInfo,
      boolean optimized
    ) throws PatternSyntaxException {
      this.pattern = pattern;
      this.printDebugInfo = printDebugInfo;

      final Tnfa nfaWithoutWildcard = Tnfa.parse(this.pattern, true, false);
      final Tnfa nfaWithWildcard = Tnfa.parse(this.pattern, true, true);

      this.matchesDfa = Tdfa.fromTnfa(nfaWithoutWildcard, MatchMode.FULL, optimized);
      this.lookingAtDfa = Tdfa.fromTnfa(nfaWithoutWildcard, MatchMode.PREFIX, optimized);
      this.findDfa = Tdfa.fromTnfa(nfaWithWildcard, MatchMode.PREFIX, optimized);

      this.groupCount = matchesDfa.groupCount();
    }

    public InterpretableDfaPattern(String pattern) throws PatternSyntaxException {
      this(pattern, false, true);
    }

    @Override
    public String toString() {
      return "DfaPattern.InterpretableDfaPattern(" + pattern() + ")";
    }
  }
}
