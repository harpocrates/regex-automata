package automata;

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
  throws java.io.IOException, IllegalAccessException, NoSuchMethodException, InstantiationException, InvocationTargetException {
    final var m1 = M1.fromRe(Re.parse(regex));
    final var m2 = M2.fromM1(m1);
    final var m3 = M3.fromM2(m2);
    final var m4 = M4.fromM2M3(m2, m3);

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
  public static InterpretableDfaPattern interpreted(String regex) {
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

  final static public class InterpretableDfaPattern implements DfaPattern {
    public final String pattern;
    public final Re parsed;
    public final M1<Integer> m1;
    public final M2 m2;
    public final M3 m3;
    public final M4 m4;

    public String pattern() {
      return pattern;
    }

    public boolean checkMatch(CharSequence input) {
      return m3.checkSimulate(input);
    }

    public ArrayMatchResult captureMatch(CharSequence input) {
      var m3Path = m3.captureSimulate(input);
      if (m3Path.isEmpty()) return null;
      var result = m4.simulate(input.toString(), m3Path.get(), false);
      return (result.isEmpty()) ? null : result.get();
    }

    public InterpretableDfaPattern(String pattern) {
      this.pattern = pattern;
      this.parsed = Re.parse(pattern);
      this.m1 = M1.fromRe(this.parsed);
      this.m2 = M2.fromM1(this.m1);
      this.m3 = M3.fromM2(this.m2);
      this.m4 = M4.fromM2M3(this.m2, this.m3);
    }

    @Override
    public String toString() {
      return "DfaPattern.InterpretableDfaPattern(" + pattern() + ")";
    }
  }
}
