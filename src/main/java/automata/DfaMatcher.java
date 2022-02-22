package automata;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.regex.MatchResult;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Mutable matcher object.
 *
 * @author Alec Theriault
 */
abstract public class DfaMatcher extends ArrayMatchResult {

  protected final DfaPattern pattern;

  /**
   * Index into {@link #input} where matching should start.
   */
  protected int regionStart = 0;

  /**
   * Index into {@link #input} where matching should end.
   */
  protected int regionEnd;

  /**
   * Index into {@link #input} where matching has advanced to.
   */
  protected int currentStart = 0;

  /**
   * Whether the last matching operation was successful.
   *
   * If this is {@code false}, then methods extracting match results will throw
   * and illegal state exception.
   */
  protected boolean successfulMatch = false;

  protected DfaMatcher(DfaPattern pattern, CharSequence input) {
    this(pattern, input, pattern.groupCount());
  }

  DfaMatcher(DfaPattern pattern, CharSequence input, int groupCount) {
    super(input, new int[(groupCount + 1) * 2], groupCount);
    this.pattern = pattern;
    this.regionEnd = input.length();
  }

  /**
   * Check that the match is in a successful state.
   */
  private void checkMatch() throws IllegalStateException {
    if (!successfulMatch) {
      throw new IllegalStateException("No match found");
    }
  }

  @Override
  public int start() throws IllegalStateException {
    checkMatch();
    return super.start();
  }

  @Override
  public int end() throws IllegalStateException {
    checkMatch();
    return super.end();
  }

  @Override
  public String group() throws IllegalStateException {
    checkMatch();
    return super.group();
  }


  @Override
  public int start(int groupIndex) throws IllegalStateException, IndexOutOfBoundsException {
    checkMatch();
    return super.start(groupIndex);
  }

  @Override
  public int end(int groupIndex) throws IllegalStateException, IndexOutOfBoundsException {
    checkMatch();
    return super.end(groupIndex);
  }

  @Override
  public String group(int groupIndex) throws IllegalStateException, IndexOutOfBoundsException {
    checkMatch();
    return super.group(groupIndex);
  }

  /**
   * Get the pattern being matched by this matcher.
   *
   * @return pattern matched by this matcher
   */
  public DfaPattern pattern() {
    return pattern;
  }

  /**
   * Check if the last match hit the end of the region.
   *
   * @return whether the latest match was successful and reach the end of the region
   */
  public boolean hitEnd() {
    return successfulMatch && end() == regionEnd;
  }

  /**
   * Get the start of the region of the input being matched.
   *
   * @return start of the region to match
   */
  public int regionStart() {
    return regionStart;
  }

  /**
   * Get the end of the region of the input being matched.
   *
   * @return end of the region to match
   */
  public int regionEnd() {
    return regionEnd;
  }

  /**
   * Update the region of the input to match and reset to this region.
   *
   * @param start new start of the match region
   * @param end new end of the match region
   * @return this matcher
   */
  public DfaMatcher region(int start, int end) throws IndexOutOfBoundsException {
    if (start >= input.length()) {
      throw new IndexOutOfBoundsException("start");
    } else if (end > input.length()) {
      throw new IndexOutOfBoundsException("end");
    }

    this.regionStart = start;
    this.regionEnd = end;
    this.currentStart = start;
    this.successfulMatch = false;
    return this;
  }

  public DfaMatcher reset() {
    this.regionStart = 0;
    this.regionEnd = input.length();
    this.currentStart = 0;
    this.successfulMatch = false;
    return this;
  }

  /**
   * Update other matcher state based on the match outcome.
   */
  protected boolean postMatchUpdate(boolean successfulMatch) {
    this.successfulMatch = successfulMatch;
    if (successfulMatch) {
      this.currentStart = groups[1];
    }
    return successfulMatch;
  }

  /**
   * Match an input string to the DFA pattern and extract capture groups.
   *
   * @return whether the pattern matched
   */
  public abstract boolean matches();

  /**
   * Match a prefix of the input string to the DFA pattern and extract capture
   * groups
   *
   * @return whether the pattern matched
   */
  public abstract boolean lookingAt();

  /**
   * Find the first instance of the DFA pattern inside the input and extract
   * capture groups
   *
   * @return whether the pattern matched
   */
  public abstract boolean find();

  /**
   * Make an immutable snapshot of the match result.
   */
  public ArrayMatchResult toMatchResult() {
    return new ArrayMatchResult(input.toString(), groups.clone());
  }

  public Stream<MatchResult> results() {
    if (!find()) {
      return Stream.empty();
    }

    final var iterator = new Iterator<MatchResult>() {

      // Make an immutable copy of the input for reuse in match results
      final String immutableInput = input.toString();

      /* 0 means we haven't tried the next match
       * 1 means we tried the next match and there is a result found (and ready)
       * 2 means we tried the next match and there is no result found
       */
      int nextFlag = 1;

      private void checkNext() {
        if (nextFlag == 0) {
          nextFlag = find() ? 1 : 2;
        }
      }

      @Override
      public boolean hasNext() {
        checkNext();
        return nextFlag == 1;
      }

      @Override
      public MatchResult next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }

        return new ArrayMatchResult(immutableInput, groups.clone());
      }

      @Override
      public void forEachRemaining(Consumer<? super MatchResult> action) {
        checkNext();
        boolean hasNext = nextFlag == 1;
        while (hasNext) {
          action.accept(new ArrayMatchResult(immutableInput, groups.clone()));
          hasNext = find();
        }
      }
    };

    return StreamSupport.stream(
      Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
      false
    );
  }

  // TODO: this does none of the fancy group substitution in Java regex
  public String replaceAll(String replacement) {
    reset();
    int lastStart = 0;
    boolean found = find();
    if (found) {
      StringBuilder builder = new StringBuilder();
      do {
        builder.append(input.subSequence(lastStart, super.start()));
        builder.append(replacement);
        lastStart = currentStart;
        found = find();
      } while (found);
      builder.append(input.subSequence(currentStart, regionEnd));
      return builder.toString();
    } else {
      return input.toString();
    }
  }


}
