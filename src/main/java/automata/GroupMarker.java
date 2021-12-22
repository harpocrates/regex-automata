package automata;

/**
 * Marker for the start or end of a capture group.
 *
 * @param isStart is this the start or end of the group?
 * @param groupIndex index of the capture group
 */
public record GroupMarker(
  boolean isStart,
  int groupIndex
) implements PathMarker {

  @Override
  public String graphVizLabel() {
    return String.format("%c<SUB>%d</SUB>", isStart ? 'S' : 'E', groupIndex);
  }

  public static GroupMarker start(int groupIndex) {
    return new GroupMarker(true, groupIndex);
  }

  public static GroupMarker end(int groupIndex) {
    return new GroupMarker(false, groupIndex);
  }
}
