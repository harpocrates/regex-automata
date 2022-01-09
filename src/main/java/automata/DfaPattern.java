package automata;

import java.text.ParseException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import org.objectweb.asm.Opcodes;

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
  throws java.io.IOException, ParseException, IllegalAccessException, NoSuchMethodException, InstantiationException, InvocationTargetException {
    final var m1 = M1Dfa.parse(regex);
    final var m2 = M2Nfa.fromM1(m1);
    final var m3 = M3Dfa.fromM2(m2);
    final var m4 = M4Dfa.fromM2M3(m2, m3);

    final String className = "automata/DfaPattern$Compiled";
    final boolean debugInfo = false;
    final int classFlags = Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
    final byte[] classBytes = CompiledDfaCodegen
      .generateDfaPatternSubclass(
        regex,
        m3,
        m4,
        m4.groupCount(),
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
  public static InterpretableDfaPattern interpreted(String regex) throws ParseException {
    return new InterpretableDfaPattern(regex);
  }

  /**
   * Match an input string to the DFA pattern and extract capture groups
   *
   * @param input character sequence to match
   * @return extracted capture groups, or `null` if the pattern didn't match
   */
  public ArrayMatchResult captureMatch(CharSequence input);

  /**
   * Check if an input string matches the DFA pattern
   *
   * @param input character sequence to match
   * @return whether the input matches the pattern
   */
  public boolean checkMatch(CharSequence input);

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
    public final M1Dfa m1;
    public final TDFA dfa;

    public String pattern() {
      return pattern;
    }

    public boolean checkMatch(CharSequence input) {
      return dfa.checkSimulate(input, printDebugInfo);
    }

    public ArrayMatchResult captureMatch(CharSequence input) {
      return dfa.captureSimulate(input, printDebugInfo);
    }

    public int groupCount() {
      return dfa.groupCount;
    }

    public InterpretableDfaPattern(String pattern, boolean printDebugInfo) throws ParseException {
      this.pattern = pattern;
      this.printDebugInfo = printDebugInfo;
      this.m1 = M1Dfa.parse(this.pattern);
      this.dfa = TDFA.fromTNFA(this.m1);
    }

    public InterpretableDfaPattern(String pattern) throws ParseException {
      this(pattern, false);
    }

    @Override
    public String toString() {
      return "DfaPattern.InterpretableDfaPattern(" + pattern() + ")";
    }
  }
}
