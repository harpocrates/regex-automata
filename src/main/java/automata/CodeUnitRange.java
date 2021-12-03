package automata;

/**
 * Inclusive (and therefore non-empty) range of 16-bit code units.
 *
 * Unless otherwise stated, `null` values of `CodeUnitRange` get treated as
 * empty ranges.
 *
 * @param lowerBound smallest (unsigned) 16-bit code unit in the range
 * @param upperBound largest (unsigned) 16-bit code unit in the range
 */
public record CodeUnitRange(
  char lowerBound,
  char upperBound
) implements Comparable<CodeUnitRange> {

  public static final CodeUnitRange FULL =
    CodeUnitRange.between(Character.MIN_VALUE, Character.MAX_VALUE);

  /**
   * Make a range (equivalent to the constructor, but more informatively named)
   *
   * @param lowerBound smallest 16-bit code unit in the range
   * @param upperBound largest 16-bit code unit in the range
   */
  public static CodeUnitRange between(char lowerBound, char upperBound) {
    return new CodeUnitRange(lowerBound, upperBound);
  }

  /**
   * Make a range containing only a single unit
   *
   * @param singleUnit 16-bit code unit in the range
   */
  public static CodeUnitRange single(char singleUnit) {
    return new CodeUnitRange(singleUnit, singleUnit);
  }

  public CodeUnitRange {
    if (lowerBound > upperBound) {
      throw new IllegalArgumentException(
        "Code unit range lower bound " + (int)lowerBound + " exceeds upper bound " + (int)upperBound
      );
    }
  }

  @Override
  public String toString() {
    return "CodeUnitRange(" + compactString() + ")";
  }

  public String compactString() {
    final int lo = (int)lowerBound;
    final int hi = (int)upperBound;
    return (lo == hi) ? "" + lo : "" + lo + "-" + hi;
  }

  /**
   * Does this range contain the code unit?
   *
   * @param codeUnit 16-bit code unit
   * @return whether the code unit is in this range
   */
  public boolean contains(char codeUnit) {
    return lowerBound <= codeUnit && codeUnit <= upperBound;
  }

  @Override
  public int compareTo(CodeUnitRange other) {
    int lowCompare = Character.compare(lowerBound, other.lowerBound);
    return lowCompare != 0 ? lowCompare : Character.compare(upperBound, other.upperBound);
  }

  /**
   * Try to merge this range with another range.
   *
   * If the ranges are not overlapping or consecutive, returns `null` to signify failure
   *
   * @param other other range (`null` gets treated as an empty range)
   * @return merged ranged, or else `null` if the ranges cannot be merged
   */
  public CodeUnitRange merge(CodeUnitRange other) {
    if (other == null) {
      return this;
    } else if (overlapsWith(other)) {
      return new CodeUnitRange(
        lowerBound > other.lowerBound ? other.lowerBound : lowerBound,
        upperBound > other.upperBound ? upperBound : other.upperBound
      );
    } else {
      return null;
    }
  }

  /**
   * Try to intersect this range with another range.
   *
   * If the ranges are not overlapping or consecutive, returns `null` to signify failure
   *
   * @param other other range (`null` gets treated as an empty range)
   * @return intersected ranged, or else `null` if the ranges have no intersection
   */
  public CodeUnitRange intersect(CodeUnitRange other) {
    if (overlapsWith(other)) {
      return new CodeUnitRange(
        lowerBound < other.lowerBound ? lowerBound : other.lowerBound,
        upperBound < other.upperBound ? other.upperBound : upperBound
      );
    } else {
      return null;
    }
  }

  /**
   * Does this range overlap with another range?
   *
   * @param other other range
   * @return whether the ranges overlap
   */
  public boolean overlapsWith(CodeUnitRange other) {
    return other != null && lowerBound <= other.upperBound && other.lowerBound <= upperBound;
  }
}
