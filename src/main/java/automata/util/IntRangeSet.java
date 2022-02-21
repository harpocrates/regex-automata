package automata.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Set of integers, tracked using ranges.
 *
 * The constraint on ranges being non-overlapping, non-contiguous, and sorted
 * ensures that there is always exactly one canonical instance for any logical
 * set of integers. If the input set of integers is not already in this
 * format, construct the set using {@link #unionOf}.
 *
 * @author Alec Theriault
 * @param ranges non-overlapping, non-contiguous, and sorted ranges
 */
public record IntRangeSet(
  List<IntRange> ranges
) implements Iterable<Integer> {

  public IntRangeSet(List<IntRange> ranges) {

    // Check that the ranges really are sorted
    IntRange previousRange = null;
    for (IntRange range : ranges) {
      if (previousRange != null && previousRange.upperBound() + 1 >= range.lowerBound()) {
        throw new IllegalArgumentException(
          "Ranges are overlapping or not sorted: " + previousRange + " and " + range
        );
      }
      previousRange = range;
    }

    // Make a defensive copy of the list
    this.ranges = List.<IntRange>copyOf(ranges);
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
  public static IntRangeSet of(IntRange... ranges) {
    return new IntRangeSet(Arrays.asList(ranges));
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
  public static IntRangeSet unionOf(IntRange... ranges) {
    return union(Arrays.stream(ranges).map(range -> IntRangeSet.of(range)).toList());
  }

  /**
   * Construct a set that is all integers in a range meeting a condition.
   *
   * @param range input range
   * @param condition predicate that must be satisfied for inclusion in output
   * @return subset of range which satisfies the condition
   */
  public static IntRangeSet matching(IntRange range, IntPredicate condition) {
    if (range == null) {
      return IntRangeSet.EMPTY;
    }

    final var outputRanges = new ArrayList<IntRange>();
    final int rangeUpperBound = range.upperBound();
    int nextInt = range.lowerBound();

    do {
      // Skip past non-matching integers
      while (nextInt <= rangeUpperBound && !condition.test(nextInt)) {
        nextInt++;
      }

      // Collect the next range
      final int startRange = nextInt;
      while (nextInt <= rangeUpperBound && condition.test(nextInt)) {
        nextInt++;
      }
      if (nextInt > startRange) {
        outputRanges.add(IntRange.between(startRange, nextInt - 1));
      }
    } while (nextInt <= rangeUpperBound);

    return new IntRangeSet(outputRanges);
  }

  @Override
  public String toString() {
    final var builder = new StringBuilder("IntRangeSet.of(");
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

  public IntStream stream() {
    return ranges.stream().flatMapToInt(IntRange::stream);
  }

  @Override
  public Iterator<Integer> iterator() {
    return stream().iterator();
  }

  /**
   * Empty set.
   *
   * Contains no integers.
   */
  public static final IntRangeSet EMPTY = new IntRangeSet(List.<IntRange>of());

  /**
   * Universal set.
   *
   * Contains every integer.
   */
  public static final IntRangeSet FULL = new IntRangeSet(List.<IntRange>of(IntRange.FULL));

  /**
   * Whether this range contain the integer.
   *
   * The complexity is {@code O(log(M))} for {@code M} ranges in the set,
   * provided the set is backed by a random access list. If not, the fallback
   * is an {@code O(M)} linear scan.
   *
   * @param integer integer
   * @return whether the integer is in this range
   */
  public boolean contains(int integer) {
    int rangeIndex = Collections.binarySearch(
      ranges,
      IntRange.single(integer),
      RANGE_BY_LOWER
    );
    if (rangeIndex == -1) {
      return false;
    } else if (rangeIndex < 0) {
      rangeIndex = -rangeIndex - 2;
    }
    return ranges.get(rangeIndex).contains(integer);
  }

  private static final Comparator<IntRange> RANGE_BY_LOWER = new Comparator<>() {
    public int compare(IntRange r1, IntRange r2) {
      return Integer.compare(r1.lowerBound(), r2.lowerBound());
    }
  };

  /**
   * Check if this set is empty.
   *
   * @return whether this set is empty
   */
  public boolean isEmpty() {
    return ranges.isEmpty();
  }

  /**
   * Compute the union of a collection of sets.
   *
   * The compexity is {@code O(N * M * log(N))} for {@code N} input sets with
   * {@code M} ranges in them.
   *
   * @param sets collections of integer sets
   * @return union of set
   */
  public static IntRangeSet union(Collection<IntRangeSet> sets) {
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
  public IntRangeSet union(IntRangeSet other) {
    return IntRangeSet.union(List.of(this, other));
  }

  /**
   * Compute the intersection of a collection of sets.
   *
   * The compexity is {@code O(N * M * log(N))} for {@code N} input sets with
   * {@code M} ranges in them.
   *
   * @param sets collections of integer sets
   * @return intersection of sets
   */
  public static IntRangeSet intersection(Collection<IntRangeSet> sets) {
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
  public IntRangeSet intersection(IntRangeSet other) {
    return IntRangeSet.intersection(List.of(this, other));
  }

  /**
   * Compute the symmetric difference of a collection of sets.
   *
   * The compexity is {@code O(N * M * log(N))} for {@code N} input sets with
   * {@code M} ranges in them.
   *
   * @param sets collections of integer sets
   * @return symmetric difference of sets
   */
  public static IntRangeSet symmetricDifference(Collection<IntRangeSet> sets) {
    final int inputSetCount = sets.size();
    return aggregateSets((int n) -> n % 2 == 1, sets);
  }

  /**
   * Take the symmetric difference of two sets.
   *
   * The compexity is {@code O(M)} for input sets with {@code M} ranges in them.
   *
   * @param other set to intersect with `this`
   * @return symmetric difference of sets
   */
  public IntRangeSet symmetricDifference(IntRangeSet other) {
    return IntRangeSet.symmetricDifference(List.of(this, other));
  }

  /**
   * Take the complement of a set.
   *
   * The compexity is {@code O(M)} for input sets with {@code M} ranges in them.
   *
   * @return complement of set
   */
  public IntRangeSet complement() {
    return aggregateSets((int n) -> n == 0, List.of(this));
  }

  /**
   * Take the difference with another set.
   *
   * The compexity is {@code O(M)} for input sets with {@code M} ranges in them.
   *
   * @param other set to subtract from `this`
   * @return set difference
   */
  public IntRangeSet difference(IntRangeSet other) {
    return this.intersection(other.complement());
  }

  /**
   * Aggregate a collection of sets into one set.
   *
   * Criteria for an integer being in the output set is whether that integer
   * is in an accepted number of input sets. This covers most set operations
   * uniformly
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
  private static IntRangeSet aggregateSets(
    IntPredicate pointInOutput,
    Iterable<IntRangeSet> inputSets
  ) {

   final var endpoints = new PriorityQueue<EndpointIterator<Void>>(RANGE_ITERATOR_COMPARATOR);
    for (IntRangeSet set : inputSets) {
      final var ranges = set.ranges;
      if (!ranges.isEmpty()) {
        endpoints.add(new EndpointIterator<Void>(true, ranges.listIterator(), null));
        endpoints.add(new EndpointIterator<Void>(false, ranges.listIterator(), null));
      }
    }

    // List of output ranges
    final var outputRanges = new ArrayList<IntRange>();
    int activeLower = Integer.MIN_VALUE;
    int previousActiveUpper = Integer.MIN_VALUE;

    int openRanges = 0;
    boolean inActiveRange = pointInOutput.test(openRanges);

    boolean firstRange = inActiveRange;
    boolean lastRange = false;

    while (!endpoints.isEmpty()) {

      final EndpointIterator<Void> nextEndpointsIter = endpoints.poll();
      final boolean isUpperEndpoint = nextEndpointsIter.isUpper();
      final int endpoint = nextEndpointsIter.nextEndpoint();

      // Update the count of open ranges and check for start/end of output range
      openRanges += isUpperEndpoint ? -1 : 1;

      if (inActiveRange != pointInOutput.test(openRanges)) {
        inActiveRange = !inActiveRange;
        if (inActiveRange) {

          // If this was an upper endpoint, the first point satisfying the condition is the next one
          activeLower = isUpperEndpoint ? endpoint + 1 : endpoint;
          if (isUpperEndpoint && endpoint == Integer.MAX_VALUE) {
            lastRange = true;
          }

          // Merge contiguous intervals
          if (previousActiveUpper + 1 == activeLower && !outputRanges.isEmpty()) {
            activeLower = outputRanges.remove(outputRanges.size() - 1).lowerBound();
          }
        } else {
          // If this was a lower endpoint, the last point satisfying the condition is the previous one
          previousActiveUpper = isUpperEndpoint ? endpoint : endpoint - 1;

          // Check for situations where the closed range is actually empty
          if (!(firstRange && endpoint == Integer.MIN_VALUE) && previousActiveUpper >= activeLower) {
            outputRanges.add(IntRange.between(activeLower, previousActiveUpper));
          }
        }
        firstRange = false;
      }

      // Re-insert the iterator if it still has more endpoints
      if (nextEndpointsIter.ranges().hasNext()) {
        endpoints.add(nextEndpointsIter);
      }
    }

    // Close out a trailing active range
    if (!lastRange && inActiveRange) {
      outputRanges.add(IntRange.between(activeLower, Integer.MAX_VALUE));
    }

    return new IntRangeSet(outputRanges);
  }

  /**
   * Convert a set of ranges into a set of disjoint partition sets.
   *
   * The range sets returned should be disjoint and keyed by all of the input
   * sets containing that partition. Additionally, any of the input sets should
   * be recoverable by taking the union of all of the values whose keys contain
   * the input set's key.
   *
   * Note that this does not include the empty set as a key.
   *
   * @param sets input sets, keyed with some unique identifier
   * @return mapping from sets containing a partition subset to the partition subset
   */
  public static <K> Map<Set<K>, IntRangeSet> disjointPartition(Map<K, IntRangeSet> sets) {

   final var endpoints = new PriorityQueue<EndpointIterator<K>>(RANGE_ITERATOR_COMPARATOR);
    for (Map.Entry<K, IntRangeSet> entry : sets.entrySet()) {
      final var ranges = entry.getValue().ranges;
      final K key = entry.getKey();
      if (!ranges.isEmpty()) {
        endpoints.add(new EndpointIterator<K>(true, ranges.listIterator(), key));
        endpoints.add(new EndpointIterator<K>(false, ranges.listIterator(), key));
      }
    }

    // Lists of output ranges
    final var outputRangesMap = new HashMap<Set<K>, ArrayList<IntRange>>();
    int currentLeftEndpoint = 0;
    final Set<K> openRanges = new HashSet<K>();

    while (!endpoints.isEmpty()) {

      final EndpointIterator<K> nextEndpointsIter = endpoints.poll();
      final int endpoint = nextEndpointsIter.nextEndpoint();

      if (nextEndpointsIter.isUpper()) {
        if (currentLeftEndpoint <= endpoint) {
          outputRangesMap
            .computeIfAbsent(new HashSet<K>(openRanges), k -> new ArrayList<>())
            .add(IntRange.between(currentLeftEndpoint, endpoint));
        }
        openRanges.remove(nextEndpointsIter.tag());
        currentLeftEndpoint = endpoint + 1;
        if (endpoint == Integer.MAX_VALUE) {
          break;
        }
      } else {
        if (!openRanges.isEmpty() && currentLeftEndpoint < endpoint) {
          outputRangesMap
            .computeIfAbsent(new HashSet<K>(openRanges), k -> new ArrayList<>())
            .add(IntRange.between(currentLeftEndpoint, endpoint - 1));
        }
        openRanges.add(nextEndpointsIter.tag());
        currentLeftEndpoint = endpoint;
      }

      // Re-insert the iterator if it still has more endpoints
      if (nextEndpointsIter.ranges().hasNext()) {
        endpoints.add(nextEndpointsIter);
      }
    }

    return outputRangesMap
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> new IntRangeSet(e.getValue())));
  }

  /**
   * Compares (non-empty) iterators based on
   *
   *   - the lower bound of their first range if the boolean is false
   *   - the upper bound of their first range if the boolean is true
   *
   * Lower bounds take priority over upper bounds if there is a tie.
   *
   * Behaviour on empty endpoint iterators is undefined.
   */
  private static final Comparator<EndpointIterator<?>> RANGE_ITERATOR_COMPARATOR =
    Comparator
      .comparingInt(EndpointIterator<?>::peekEndpoint)
      .thenComparing(EndpointIterator<?>::isUpper);

  private record EndpointIterator<T>(boolean isUpper, ListIterator<IntRange> ranges, T tag) {

    int nextEndpoint() {
      final IntRange next = ranges.next();
      return isUpper ? next.upperBound() : next.lowerBound();
    }

    int peekEndpoint() {
      final int next = nextEndpoint();
      ranges.previous(); // roll iterator back
      return next;
    }
  }

}

