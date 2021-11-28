package automata;

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
  public static DfaPattern compile(String regex) {
    throw new RuntimeException("unimplemented");
  }

  /**
   * Compiles the given regular expression into an efficient, but interpretted
   * pattern.
   *
   * This functions like {@link #compile(String regex)}, but the DFAs are
   * walked inside a (small and still efficient) interpreter. No bytecode is
   * generated.
   *
   * @param regex source of the pattern
   * @return compiled DFA pattern
   */
  public static DfaPattern interpretted(String regex) {
    return CompiledRe.apply(regex, true);
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
}
