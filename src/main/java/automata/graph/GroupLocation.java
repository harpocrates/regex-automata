package automata.graph;

public interface GroupLocation {

  public int distance();

  public GroupLocation addDistance(int extraDistance);

  /**
   * Location at a fixed distance from the start or end of the input
   *
   * @param relativeToStart whether it is relative to the start or end
   * @param distance how far is this location from the start or end?
   */
  public record Absolute(
    boolean relativeToStart,
    int distance
  ) implements GroupLocation {

    public Absolute addDistance(int extraDistance) {
      return new Absolute(relativeToStart, distance + extraDistance);
    }
  }

  /**
   * Location at a fixed distance from another group marker.
   *
   * @param relativeTo which group marker is the position relative to?
   * @param unavoidable must this location (and the marker) be traversed in any match?
   * @param distance how far is this location from the marker?
   */
  public record RelativeToGroup(
    GroupMarker relativeTo,
    int distance
  ) implements GroupLocation {

    public RelativeToGroup addDistance(int extraDistance) {
      return new RelativeToGroup(relativeTo, distance + extraDistance);
    }
  }
}
