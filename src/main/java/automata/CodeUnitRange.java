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
  short lowerBound,
  short upperBound
) implements Comparable<CodeUnitRange> {

  public static final short MIN_BOUND = 0;
  public static final short MAX_BOUND = -1;

  public static final CodeUnitRange FULL = CodeUnitRange.between(MIN_BOUND, MAX_BOUND);

  /**
   * Make a range (equivalent to the constructor, but more informatively named)
   *
   * @param lowerBound smallest 16-bit code unit in the range
   * @param upperBound largest 16-bit code unit in the range
   */
  public static CodeUnitRange between(short lowerBound, short upperBound) {
    return new CodeUnitRange(lowerBound, upperBound);
  }

  /**
   * Make a range containing only a single unit
   *
   * @param singleUnit 16-bit code unit in the range
   */
  public static CodeUnitRange single(short singleUnit) {
    return new CodeUnitRange(singleUnit, singleUnit);
  }

  public CodeUnitRange {
    if (Short.compareUnsigned(upperBound, lowerBound) < 0) {
      throw new IllegalArgumentException(
        "Code unit range lower bound " + lowerBound + " exceeds upper bound " + upperBound
      );
    }
  }

  @Override
  public String toString() {
    return "CodeUnitRange(" + compactString() + ")";
  }

  public String compactString() {
    final int lo = Short.toUnsignedInt(lowerBound);
    final int hi = Short.toUnsignedInt(upperBound);
    return (lo == hi) ? "" + lo : "" + lo + "-" + hi;
  }

  /**
   * Does this range contain the code unit?
   *
   * @param codeUnit 16-bit code unit
   * @return whether the code unit is in this range
   */
  public boolean contains(short codeUnit) {
    return Short.compareUnsigned(lowerBound, codeUnit) <= 0 &&
      Short.compareUnsigned(upperBound, codeUnit) >= 0;
  }

  @Override
  public int compareTo(CodeUnitRange other) {
    int lowCompare = Short.compareUnsigned(lowerBound, other.lowerBound);
    return lowCompare != 0 ? lowCompare : Short.compareUnsigned(upperBound, other.upperBound);
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
      boolean thisLowerBigger = Short.compareUnsigned(lowerBound, other.lowerBound) > 0;
      boolean thisUpperBigger = Short.compareUnsigned(upperBound, other.upperBound) > 0;
      return new CodeUnitRange(
        thisLowerBigger ? other.lowerBound : lowerBound,
        thisUpperBigger ? upperBound : other.upperBound
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
      boolean thisLowerBigger = Short.compareUnsigned(lowerBound, other.lowerBound) > 0;
      boolean thisUpperBigger = Short.compareUnsigned(upperBound, other.upperBound) > 0;
      return new CodeUnitRange(
        thisLowerBigger ? lowerBound : other.lowerBound,
        thisUpperBigger ? other.upperBound : upperBound
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
    return other != null &&
      Short.compareUnsigned(upperBound, other.lowerBound) >= 0 &&
      Short.compareUnsigned(other.upperBound, lowerBound) >= 0;
  }

  /**
   * Is this range of values strictly greater than the other range of values.
   *
   * More rigourously: there exists no value which is in this range which is
   * smaller than or equal to a value in the other range.
   *
   * @param other other range
   * @return whether this range is strictly less than the other range
   */
  public boolean isStrictlyGreaterThan(CodeUnitRange other) {
    return other == null || Short.compareUnsigned(other.upperBound, lowerBound) < 0;
  }
}
