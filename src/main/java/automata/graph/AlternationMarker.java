package automata.graph;

/**
 * Marker for encoding prioritized choice in an NFA.
 *
 * <p>The {@code PLUS} path in the NFA must be tried "before" the {@code MINUS}
 * path as witnessed by content in capture groups.
 *
 * <p>For example, given the regular expression {@code (?:a([cb]+)d|a(bcd))}
 * matching against the input string {@code "abcd"}, we expect the first
 * alternative to be the one that ends up matching such that the first capture
 * group is {@code "bc"} and the second capture group is absent.
 *
 * @author Alec Theriault
 */
public enum AlternationMarker implements PathMarker {
  // Note: order of the cases matters since that determines `Comparable` ordering

  /** Alternative to try first. */
  PLUS {
    @Override
    public String dotLabel() {
      return "+";
    }
  },

  /** Alternative to try second. */
  MINUS {
    @Override
    public String dotLabel() {
      return "&minus;";
    }
  };
}
