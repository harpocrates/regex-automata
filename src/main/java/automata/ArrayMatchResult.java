package automata;

import java.util.regex.MatchResult;

public class ArrayMatchResult implements MatchResult {
  private final String matchedString;
  private final int[] startOffsets;
  private final int[] endOffsets;

  public ArrayMatchResult(String matchedString, int[] startOffsets, int[] endOffsets) {
    this.matchedString = matchedString;
    this.startOffsets = startOffsets;
    this.endOffsets = endOffsets;
  }

  @Override
  public int start(int group) {
    return startOffsets[group];
  }

  @Override
  public int start() {
    return start(0);
  }

  @Override
  public int end(int group) {
    return endOffsets[group];
  }

  @Override
  public int end() {
    return end(0);
  }

  @Override
  public String group(int i) {
    return matchedString.substring(start(i), end(i));
  }

  @Override
  public String group() {
    return group(0);
  }

  @Override
  public int groupCount() {
    return startOffsets.length;
  }
}


