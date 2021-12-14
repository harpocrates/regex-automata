package automata;

import java.util.Iterator;
import java.util.stream.IntStream;

/**
 * Inclusive (and therefore non-empty) range of integers.
 *
 * Unless otherwise stated, `null` values of `IntRange` get treated as
 * empty ranges.
 *
 * @param lowerBound smallest integer in the range
 * @param upperBound largest integer in the range
 */
public record IntRange(
  int lowerBound,
  int upperBound
) implements Comparable<IntRange>, Iterable<Integer> {

  public static final IntRange FULL =
    IntRange.between(Integer.MIN_VALUE, Integer.MAX_VALUE);

  /**
   * Make a range (equivalent to the constructor, but more informatively named).
   *
   * @param lowerBound smallest integer in the range
   * @param upperBound largest integer in the range
   */
  public static IntRange between(int lowerBound, int upperBound) {
    return new IntRange(lowerBound, upperBound);
  }

  /**
   * Make a range containing only a single integer.
   *
   * @param integer integer in the range
   */
  public static IntRange single(int integer) {
    return new IntRange(integer, integer);
  }

  public IntRange {
    if (lowerBound > upperBound) {
      throw new IllegalArgumentException(
        "Range lower bound " + lowerBound + " exceeds upper bound " + upperBound
      );
    }
  }

  @Override
  public String toString() {
    return "IntRange(" + compactString() + ")";
  }

  public String compactString() {
    return (lowerBound == upperBound) ? "" + lowerBound : "" + lowerBound + ".." + upperBound;
  }

  public IntStream stream() {
    return IntStream.rangeClosed(lowerBound, upperBound);
  }

  /**
   * Does this range contain the integer?
   *
   * @param integer integer
   * @return whether the integer is in this range
   */
  public boolean contains(int integer) {
    return lowerBound <= integer && integer <= upperBound;
  }

  @Override
  public int compareTo(IntRange other) {
    int lowCompare = Integer.compare(lowerBound, other.lowerBound);
    return lowCompare != 0 ? lowCompare : Integer.compare(upperBound, other.upperBound);
  }

  @Override
  public Iterator<Integer> iterator() {
    return stream().iterator();
  }

  /**
   * Try to merge this range with another range.
   *
   * If the ranges are not overlapping or consecutive, returns `null` to signify failure
   *
   * @param other other range (`null` gets treated as an empty range)
   * @return merged ranged, or else `null` if the ranges cannot be merged
   */
  public IntRange merge(IntRange other) {
    if (other == null) {
      return this;
    } else if (overlapsWith(other)) {
      return new IntRange(
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
  public IntRange intersect(IntRange other) {
    if (overlapsWith(other)) {
      return new IntRange(
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
  public boolean overlapsWith(IntRange other) {
    return other != null && lowerBound <= other.upperBound && other.lowerBound <= upperBound;
  }
}
