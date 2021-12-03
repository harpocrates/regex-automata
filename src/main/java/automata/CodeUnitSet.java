package automata;

import java.util.*;
import java.util.function.IntPredicate;
import static java.util.AbstractMap.SimpleImmutableEntry;

/**
 * Set of 16-bit code units.
 *
 * The constraint on ranges being non-overlapping, non-contiguous, and sorted
 * ensures that there is always exactly one canonical instance for any logical
 * set of codepoints. If the input set of code units is not already in this
 * format, construct the set using {@link #unionOf}.
 *
 * @param ranges non-overlapping, non-contiguous, and sorted ranges
 */
public record CodeUnitSet(
  List<CodeUnitRange> ranges
) {

  public CodeUnitSet(List<CodeUnitRange> ranges) {

    // Check that the ranges really are sorted
    CodeUnitRange previousRange = null;
    for (CodeUnitRange range : ranges) {
      if (!range.isStrictlyGreaterThan(previousRange)) {
        throw new IllegalArgumentException(
          "Code unit ranges are overlapping or not sorted: " + previousRange + " and " + range
        );
      }
      previousRange = range;
    }

    // Make a defensive copy of the list
    this.ranges = List.<CodeUnitRange>copyOf(ranges);
  }

  /**
   * Construct a set with the following non-overlapping, non-contiguous, and
   * sorted ranges.
   *
   * The compexity is {@code O(M)} for {@code M} input ranges. If the ranges
   * are possibly overlapping, contiguous, or unsorted, use {@link #unionOf}.
   *
   * @param ranges input ranges
   * @return set containing the ranges
   */
  public static CodeUnitSet of(CodeUnitRange... ranges) {
    return new CodeUnitSet(Arrays.asList(ranges));
  }

  /**
   * Construct a set that is the union of the following ranges.
   *
   * Unlike {@link #of}, this will never throw an exception, although the
   * complexity is {@code O(M)} for {@code M} input ranges.
   *
   * @param ranges input ranges
   * @return set containing the ranges
   */
  public static CodeUnitSet unionOf(CodeUnitRange... ranges) {
    return union(Arrays.stream(ranges).map(range -> CodeUnitSet.of(range)).toList());
  }

  @Override
  public String toString() {
    final var builder = new StringBuilder("CodeUnitSet.of(");
    boolean needsSpace = false;
    for (var range : ranges) {
      if (needsSpace) {
        builder.append(", ");
      } else {
        needsSpace = true;
      }
      builder.append(range.compactString());
    }
    builder.append(')');
    return builder.toString();
  }

  /**
   * Empty set.
   *
   * Contains no 16-bit code unit.
   */
  public static final CodeUnitSet EMPTY = new CodeUnitSet(List.<CodeUnitRange>of());

  /**
   * Universal set.
   *
   * Contains every 16-bit code unit.
   */
  public static final CodeUnitSet FULL = new CodeUnitSet(List.<CodeUnitRange>of(CodeUnitRange.FULL));

  /**
   * Whether this range contain the code unit.
   *
   * The complexity is {@code O(log(M))} for {@code M} ranges in the set,
   * provided the set is backed by a random access list. If not, the fallback
   * is an {@code O(M)} linear scan.
   *
   * @param codeUnit 16-bit code unit
   * @return whether the code unit is in this range
   */
  public boolean contains(short codeUnit) {
    int rangeIndex = Collections.binarySearch(
      ranges,
      CodeUnitRange.single(codeUnit),
      RANGE_BY_LOWER
    );
    if (rangeIndex == -1) {
      return false;
    } else if (rangeIndex < 0) {
      rangeIndex = -rangeIndex - 2;
    }
    return ranges.get(rangeIndex).contains(codeUnit);
  }

  /**
   * Compute the union of a collection of sets.
   *
   * The compexity is {@code O(N * M * log(N))} for {@code N} input sets with
   * {@code M} ranges in them.
   *
   * @param sets collections of code unit sets
   * @return union of set
   */
  public static CodeUnitSet union(Collection<CodeUnitSet> sets) {
    return aggregateSets((int n) -> n >= 1, sets);
  }

  /**
   * Take the union of two sets.
   *
   * The compexity is {@code O(M)} for input sets with {@code M} ranges in them.
   *
   * @param other set to union with `this`
   * @return union of sets
   */
  public CodeUnitSet union(CodeUnitSet other) {
    return CodeUnitSet.union(List.of(this, other));
  }

  /**
   * Compute the intersection of a collection of sets.
   *
   * The compexity is {@code O(N * M * log(N))} for {@code N} input sets with
   * {@code M} ranges in them.
   *
   * @param sets collections of code unit sets
   * @return intersection of sets
   */
  public static CodeUnitSet intersection(Collection<CodeUnitSet> sets) {
    final int inputSetCount = sets.size();
    return aggregateSets((int n) -> n == inputSetCount, sets);
  }

  /**
   * Take the intersection of two sets.
   *
   * The compexity is {@code O(M)} for input sets with {@code M} ranges in them.
   *
   * @param other set to intersect with `this`
   * @return intersection of sets
   */
  public CodeUnitSet intersection(CodeUnitSet other) {
    return CodeUnitSet.intersection(List.of(this, other));
  }

  /**
   * Take the complement of a set.
   *
   * The compexity is {@code O(M)} for input sets with {@code M} ranges in them.
   *
   * @return complement of set
   */
  public CodeUnitSet complement() {
    return aggregateSets((int n) -> n == 0, List.of(this));
  }

  /**
   * Aggregate a collection of sets into one set.
   *
   * Criteria for a code unit being in the output set is whether that code
   * point is in an accepted number of input sets. This covers most set
   * operations uniformly
   *
   *  - Union with {@code (int n) -> n >= 1}
   *  - Intersection with {@code (int n) -> n == N} (for {@code N} input sets)
   *  - Symmetric difference with {@code (int n) -> n % 2 == 1}
   *  - Complement with {@code (int n) -> n == 0} (with one input)
   *
   * The compexity is {@code O(N * M * log(N))} for {@code N} input sets with
   * {@code M} ranges in them.
   *
   * <h1>Algorithm</h1>
   *
   * The main idea is to scan from left-to-right all the lower and upper
   * endpoints of ranges in the sets. While doing this, we can keep a running
   * counter of how many intervals are open at any given point in time.
   *
   * Whenever this number changes, we can use the predicate function to
   * determine whether we've just started or ended a range for the output set.
   *
   * Since we know intervals are already sorted within a set, we can do all of
   * this using a priority queue that contains {@code 2 * N} iterators: each
   * set adds an iterator tracking the lower bounds of its ranges and a set
   * tracking the upper bounds of those same ranges. The priority given to the
   * elements is the position of their first upper or lower bound (see the
   * {@code RANGE_ITERATOR_COMPARATOR} comparator). Every time we poll a set
   * from the priority queue, we advance to the next range and re-insert the
   * set into the queue if it is non-empty.
   *
   * @param pointInOutput if this many input sets contain the point, does the
   *                      output set contain it?
   * @param inputSets input sets
   */
  private static CodeUnitSet aggregateSets(
    IntPredicate pointInOutput,
    Iterable<CodeUnitSet> inputSets
  ) {

   final PriorityQueue<SimpleImmutableEntry<Boolean, ListIterator<CodeUnitRange>>> endpoints =
      new PriorityQueue<>(RANGE_ITERATOR_COMPARATOR);
    for (CodeUnitSet set : inputSets) {
      final var ranges = set.ranges;
      if (!ranges.isEmpty()) {
        endpoints.add(new SimpleImmutableEntry<>(true, ranges.listIterator()));
        endpoints.add(new SimpleImmutableEntry<>(false, ranges.listIterator()));
      }
    }

    // List of output ranges
    final var outputRanges = new ArrayList<CodeUnitRange>();
    int openRanges = 0;
    short activeLower = CodeUnitRange.MIN_BOUND;
    short previousActiveUpper = CodeUnitRange.MIN_BOUND;
    boolean inActiveRange = pointInOutput.test(openRanges);

    while (!endpoints.isEmpty()) {

      final var nextEndpoint = endpoints.poll();
      final boolean isUpperEndpoint = nextEndpoint.getKey();
      final ListIterator<CodeUnitRange> ranges = nextEndpoint.getValue();
      final CodeUnitRange nextRange = ranges.next();

      // Update the count of open ranges and check for start/end of output range
      openRanges += isUpperEndpoint ? -1 : 1;
      if (inActiveRange != pointInOutput.test(openRanges)) {
        inActiveRange = !inActiveRange;
        if (inActiveRange) {
          activeLower = nextRange.lowerBound();

          // Merge contiguous intervals
          if (previousActiveUpper + 1 == activeLower && !outputRanges.isEmpty()) {
            activeLower = outputRanges.remove(outputRanges.size() - 1).lowerBound();
          }
        } else {
          previousActiveUpper = nextRange.upperBound();
          outputRanges.add(CodeUnitRange.between(activeLower, previousActiveUpper));
        }
      }

      // Re-insert the iterator if it still has more endpoints
      if (ranges.hasNext()) {
        endpoints.add(nextEndpoint);
      }
    }

    // Close out a trailing active range
    if (inActiveRange) {
      outputRanges.add(CodeUnitRange.between(activeLower, CodeUnitRange.MAX_BOUND));
    }

    return new CodeUnitSet(outputRanges);
  }

  private static final Comparator<CodeUnitRange> RANGE_BY_LOWER = new Comparator<>() {
    public int compare(CodeUnitRange r1, CodeUnitRange r2) {
      return Short.compareUnsigned(r1.lowerBound(), r2.lowerBound());
    }
  };

  /**
   * Compares (non-empty) iterators based on
   *
   *   - the lower bound of their first range if the boolean is false
   *   - the upper bound of their first range if the boolean is true
   *
   * Lower bounds take priority over upper bounds if there is a tie
   */
  private static final Comparator<SimpleImmutableEntry<Boolean, ListIterator<CodeUnitRange>>> RANGE_ITERATOR_COMPARATOR =
    Comparator
      .comparingInt((SimpleImmutableEntry<Boolean, ListIterator<CodeUnitRange>> e) -> {
        final CodeUnitRange head = e.getValue().next();
        e.getValue().previous(); // roll iterator back
        return Short.toUnsignedInt(e.getKey() ? head.upperBound() : head.lowerBound());
      })
      .thenComparing(SimpleImmutableEntry::getKey);
}

