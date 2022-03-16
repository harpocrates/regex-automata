package automata;

import java.util.Iterator;
import java.util.regex.MatchResult;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Array-backed match result.
 *
 * <p>This is designed to be minimal: it includes only a reference to the
 * initial source text and an array of the starts and ends of capture groups.
 */
public class ArrayMatchResult implements MatchResult {

  /**
   * Source text against which the regular expression was run.
   *
   * <p>This is not defensively copied on construction, so it will reflect
   * updates that occur after the match result is constructed.
   */
  protected final CharSequence input;

  /**
   * Offsets of start/end groups in the source text.
   *
   * <p>Length is equal to {@code 2 * (groupCount + 1)}.
   *
   * <p>Offsets for the start of capture groups are stored at even indices while
   * offsets for the end of capture groups are stored at odd indices. Start
   * offsets are inclusive and end offsets are not inclusive.
   *
   * <p>There is always at least one start and end offset (representing where the
   * pattern started and ended matching).
   */
  protected final int[] groups;

  /**
   * Count of capture groups in the pattern.
   */
  protected final int groupCount;

  ArrayMatchResult(CharSequence input, int[] groups, int groupCount) {
    this.input = input;
    this.groups = groups;
    this.groupCount = groupCount;
  }

  public ArrayMatchResult(CharSequence input, int[] groups) {
    this(input, groups, (groups.length >> 1) - 1);

    final int groupsLength = groups.length;
    if ((groupsLength & 1) != 0 || groupsLength < 2) {
      throw new IllegalArgumentException("Group offsets array must be even length and non-empty");
    }
  }

  /**
   * Check that a group index is within the valid group bounds.
   */
  private void checkIndex(int groupIndex) throws IndexOutOfBoundsException {
    if ((groupIndex | (groupCount - groupIndex)) < 0) {
      throw new IndexOutOfBoundsException("No capture group " + groupIndex);
    }
  }

  @Override
  public int groupCount() {
    return groupCount;
  }

  @Override
  public int start() {
    return groups[0];
  }

  @Override
  public int end() {
    return groups[1];
  }

  @Override
  public String group() {
    return input.subSequence(start(), end()).toString();
  }

  @Override
  public int start(int groupIndex) throws IndexOutOfBoundsException {
    checkIndex(groupIndex);
    return startUnchecked(groupIndex);
  }

  @Override
  public int end(int groupIndex) throws IndexOutOfBoundsException {
    checkIndex(groupIndex);
    return endUnchecked(groupIndex);
  }

  @Override
  public String group(int groupIndex) throws IndexOutOfBoundsException {
    checkIndex(groupIndex);
    return groupUnchecked(groupIndex);
  }

  final protected int startUnchecked(int groupIndex) {
    return groups[groupIndex << 1];
  }

  final protected int endUnchecked(int groupIndex) {
    return groups[groupIndex << 1 | 1];
  }

  final protected String groupUnchecked(int groupIndex) {
    int start = startUnchecked(groupIndex);
    int end = endUnchecked(groupIndex);
    return (start | end) < 0 ? null : input.subSequence(start, end).toString();
  }

  /**
   * Ordered stream of all of the groups in the match result.
   */
  public Stream<String> groups() {
    return IntStream
      .rangeClosed(0, groupCount)
      .mapToObj(this::groupUnchecked);
  }

  /**
   * Make an immutable snapshot of the match result.
   */
  public ArrayMatchResult toMatchResult() {
    return new ArrayMatchResult(input.toString(), groups.clone());
  }
}


