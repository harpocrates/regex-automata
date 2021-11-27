package automata;

import java.util.regex.MatchResult;

public class ArrayMatchResult implements MatchResult {
  private final String matchedString;
  
  /** Starts offsets are stored at even indices and end offsets at odd indices */
  private final int[] offsets;

  public ArrayMatchResult(String matchedString, int[] offsets) {
    this.matchedString = matchedString;
    this.offsets = offsets;
  }

  @Override
  public int start(int group) {
    return offsets[group << 1];
  }

  @Override
  public int start() {
    return offsets[0];
  }

  @Override
  public int end(int group) {
    return offsets[group << 1 | 1];
  }

  @Override
  public int end() {
    return offsets[1];
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
    return offsets.length >> 1;
  }
}


