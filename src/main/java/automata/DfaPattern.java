package automata;

import automata.codegen.CompiledDfa;
import automata.graph.Tdfa;
import automata.graph.Tnfa;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.PatternSyntaxException;
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
    final var nfa = Tnfa.parse(regex, true);

    var captureDfa = Tdfa.fromTnfa(nfa);
    while (captureDfa.simplifyTagCommands());
    captureDfa = captureDfa.minimized();

    final var checkDfa = captureDfa.minimizeWithoutTagCommands();

    final String className = "automata/DfaPattern$Compiled";
    final boolean debugInfo = false;
    final int classFlags = Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
    final byte[] classBytes = CompiledDfa
      .generateDfaPatternSubclass(
        regex,
        checkDfa,
        captureDfa,
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
   * Check if an input string matches the DFA pattern.
   *
   * @param input character sequence to match
   * @return whether the entire input matches the pattern
   */
  public default boolean checkMatch(CharSequence input) {
    return checkMatch(input, 0, input.length());
  }

  /**
   * Check if a subsequence of an input string matches the DFA pattern.
   *
   * @param input character sequence to match
   * @param startOffset offset in the input where the pattern starts matching
   * @param endOffset offset in the input where the pattern ends matching
   * @return whether the subsequence of the input matches the pattern
   */
  public boolean checkMatch(CharSequence input, int startOffset, int endOffset);

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
    public final Tnfa nfa;
    public final Tdfa dfa;

    public String pattern() {
      return pattern;
    }

    public boolean checkMatch(CharSequence input, int startOffset, int endOffset) {
      return dfa.checkSimulate(input, startOffset, endOffset, printDebugInfo);
    }

    public ArrayMatchResult captureMatch(CharSequence input, int startOffset, int endOffset) {
      return dfa.captureSimulate(input, startOffset, endOffset, printDebugInfo);
    }

    public int groupCount() {
      return dfa.groupCount;
    }

    public InterpretableDfaPattern(String pattern, boolean printDebugInfo) throws PatternSyntaxException {
      this.pattern = pattern;
      this.printDebugInfo = printDebugInfo;
      this.nfa = Tnfa.parse(this.pattern, true);

      var dfa = Tdfa.fromTnfa(this.nfa);
      while (dfa.simplifyTagCommands());
      this.dfa = dfa.minimized();
    }

    public InterpretableDfaPattern(String pattern) throws PatternSyntaxException {
      this(pattern, false);
    }

    @Override
    public String toString() {
      return "DfaPattern.InterpretableDfaPattern(" + pattern() + ")";
    }
  }
}
