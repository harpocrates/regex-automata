package automata;

/* TODO: add other zero-width stuff here:
 *
 *   - `^` at the start of the line
 *   - `$` at the end of the line
 *   - `\b` at a word boundary
 */
interface RegexMarker {

  /**
   * Start of a capture group
   */
  public static record GroupStart(int groupIndex) implements RegexMarker { }

  /**
   * End of a capture group
   */
  public static record GroupEnd(int groupIndex) implements RegexMarker { }
}
