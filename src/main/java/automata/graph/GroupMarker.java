package automata.graph;

import java.util.stream.Collectors;

/**
 * Marker for the start or end of a capture group.
 *
 * @param isStart is this the start or end of the group?
 * @param groupIndex index of the capture group
 */
public record GroupMarker(
  boolean isStart,
  int groupIndex
) implements PathMarker, Register, Comparable<GroupMarker> {

  @Override
  public int compareTo(GroupMarker other) {
    final int groupCmp = Integer.compare(groupIndex, other.groupIndex);
    if (groupCmp != 0) {
      return groupCmp;
    } else {
      return Boolean.compare(isStart, other.isStart);
    }
  }

  @Override
  public String dotLabel() {
    // Note: uses unicode subscripts because they render more nicely
    return String
      .format("<i>%c%d</i>", isStart ? 'S' : 'E', groupIndex)
      .chars()
      .mapToObj((int c) -> Character.toString((Character.isDigit(c) ? c - '0' + '₀' : c)))
      .collect(Collectors.joining(""));
  }

  @Override
  public String compactString() {
    return (isStart ? "S" : "E") + groupIndex;
  }

  @Override
  public String toString() {
    return "GroupMarker[" + compactString() + "]";
  }

  public int arrayOffset() {
    return isStart ? 2 * groupIndex : 2 * groupIndex + 1;
  }

  public static GroupMarker start(int groupIndex) {
    return new GroupMarker(true, groupIndex);
  }

  public static GroupMarker end(int groupIndex) {
    return new GroupMarker(false, groupIndex);
  }
}
