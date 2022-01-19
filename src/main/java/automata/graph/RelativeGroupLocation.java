package automata.graph;

/**
 * Location relative to a group marker.
 *
 * @param relativeTo which group marker is the position relative to?
 * @param unavoidable must this location (and the marker) be traversed in any match?
 * @param distance how far is this location from the marker?
 */
public record RelativeGroupLocation(
  GroupMarker relativeTo,
  boolean unavoidable,
  int distance
) {

  public RelativeGroupLocation addDistance(int extraDistance) {
    return new RelativeGroupLocation(relativeTo, unavoidable, distance + extraDistance);
  }
}
