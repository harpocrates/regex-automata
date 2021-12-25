package automata;

/**
 * State transition for a set of code units.
 *
 * @param codeUnitSet code units permitted in the transition
 */
public record CodeUnitTransition(IntRangeSet codeUnitSet) implements M1Transition {

  public static CodeUnitTransition ofChar(char c) {
    return new CodeUnitTransition(IntRangeSet.of(IntRange.single(c)));
  }

  public String dotLabel() {
    final var builder = new StringBuilder();
    boolean needsSpace = false;
    for (var range : codeUnitSet.ranges()) {
      if (needsSpace) {
        builder.append(", ");
      } else {
        needsSpace = true;
      }
      builder.append(codeUnitString(range.lowerBound()));
      if (range.lowerBound() != range.upperBound()) {
        builder.append('-');
        builder.append(codeUnitString(range.upperBound()));
      }
    }
    return builder.toString();
  }

  /**
   * Print a code unit as a string.
   *
   * Prints alphanumeric ascii characters as themselves and everything else
   * escaped.
   */
  private String codeUnitString(int codeUnit) {
    /* Note: "courier" is a monospaced font. Using just "monospace" leads to
     * alignment issues: https://gitlab.com/graphviz/graphviz/-/issues/1426
     */
    if (codeUnit <= 127 && Character.isLetterOrDigit(codeUnit)) {
      return String.format("<font face=\"courier\">%c</font>", codeUnit);
    } else if (Character.MIN_VALUE <= codeUnit && codeUnit <= Character.MAX_VALUE) {
      return String.format("<font face=\"courier\">\\\\u%04X</font>", codeUnit);
    } else {
      return String.format("<font face=\"courier\">\\\\U%08X</font>", codeUnit);
    }
  }
}
