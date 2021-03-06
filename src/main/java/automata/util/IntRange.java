package automata.util;

import java.util.Iterator;
import java.util.stream.IntStream;

/**
 * Inclusive (and therefore non-empty) range of integers.
 *
 * <p>Unless otherwise stated, {@code null} values of {@link IntRange} get
 * treated as empty ranges.
 *
 * @author Alec Theriault
 * @param lowerBound smallest integer in the range
 * @param upperBound largest integer in the range
 */
public record IntRange(
  int lowerBound,
  int upperBound
) implements Comparable<IntRange>, Iterable<Integer> {

  /**
   * Range containing all {@code int} values.
   */
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

  /**
   * Returns a compact string representation of the range.
   *
   * @return compact string representation
   */
  public String compactString() {
    return (lowerBound == upperBound) ? "" + lowerBound : "" + lowerBound + ".." + upperBound;
  }

  /**
   * Returns a stream of all of the integers in this range.
   *
   * <p>Indices are returned in order and the size of the stream matches with
   * the cardinality returned by {@code size} method.
   *
   * @return stream of integers in the range
   */
  public IntStream stream() {
    return IntStream.rangeClosed(lowerBound, upperBound);
  }

  /**
   * Counts up the number of integers contained in this range.
   *
   * @return number of integers in the range
   */
  public int size() {
    return upperBound - lowerBound + 1;
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
   * <p>If the ranges are not overlapping or consecutive, returns {@code null}
   * to signify failure
   *
   * @param other other range
   * @return merged ranged, or else {@code null} if the ranges cannot be merged
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
   * If the ranges are not overlapping or consecutive, returns {@code null}
   * to signify failure.
   *
   * @param other other range
   * @return intersected range, or {@code null} if there is no intersection
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
