package automata;

import java.util.Iterator;
import java.util.regex.MatchResult;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Immutable match result.
 *
 * This is designed to be minimal: it includes only a reference to the initial
 * source text and an array of the starts and ends of capture groups.
 */
public class ArrayMatchResult implements MatchResult {

  /**
   * Source text against which the regular expression was run.
   *
   * This is not defensively copied on construction, so it will reflect updates
   * that occur after the match result is constructed.
   */
  public final CharSequence sourceText;

  /**
   * Offsets of start/end groups in the source text.
   *
   * Length is equal to {@code 2 * (groupCount + 1)}.
   *
   * Offsets for the start of capture groups are stored at even indices while
   * offsets for the end of capture groups are stored at odd indices. Start
   * offsets are inclusive and end offsets are not inclusive.
   *
   * There is always at least one start and end offset (representing where the
   * pattern started and ended matching).
   */
  private final int[] offsets;

  /**
   * Count of capture groups in the pattern.
   */
  private final int groupCount;

  public ArrayMatchResult(CharSequence sourceText, int[] offsets) {
    final int offsetsLength = offsets.length;
    if ((offsetsLength & 1) != 0 || offsetsLength < 2) {
      throw new IllegalArgumentException("Offsets array must be even length and non-empty");
    }

    this.sourceText = sourceText;
    this.offsets = offsets;
    this.groupCount = (offsetsLength >> 1) - 1;
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
    return offsets[0];
  }

  @Override
  public int end() {
    return offsets[1];
  }

  @Override
  public String group() {
    return sourceText.subSequence(start(), end()).toString();
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

  private int startUnchecked(int groupIndex) {
    return offsets[groupIndex << 1];
  }

  private int endUnchecked(int groupIndex) {
    return offsets[groupIndex << 1 | 1];
  }

  private String groupUnchecked(int groupIndex) {
    int start = startUnchecked(groupIndex);
    int end = endUnchecked(groupIndex);
    return (start | end) < 0 ? null : sourceText.subSequence(start, end).toString();
  }

  /**
   * Ordered stream of all of the groups in the match result.
   */
  public Stream<String> groups() {
    return IntStream
      .rangeClosed(0, groupCount)
      .mapToObj(this::groupUnchecked);
  }
}


