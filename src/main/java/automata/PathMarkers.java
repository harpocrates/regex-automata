package automata;

import java.util.Objects;
import java.util.List;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Collections;
import java.util.LinkedList;
import java.lang.ref.SoftReference;
import java.util.stream.Collectors;

/**
 * Immutable sequence of path markers.
 *
 * The immutability means that you can create a new appended markers object in
 * constant time and it will share its prefix. This is useful given that we
 * construct path markers during a DFS traversal of a graph, so paths are
 * constantly being appended to and can share their prefixes.
 */
final class PathMarkers implements Iterable<PathMarker> {

  private record MarkerPrefixAndLast(MarkerPrefixAndLast prefix, PathMarker last) { }

  private final MarkerPrefixAndLast path;

  /**
   * Cache of an unmodifiable list matching the path.
   */
  private transient SoftReference<List<PathMarker>> cachedList = new SoftReference<>(null);

  private PathMarkers(MarkerPrefixAndLast path) {
    this.path = path;
  }

  /**
   * Canonical empty path.
   */
  public static final PathMarkers EMPTY = new PathMarkers(null);

  /**
   * Check if the path is empty.
   */
  public boolean isEmpty() {
    return path == null;
  }

  /**
   * Construct a new path markers that is the same as the receiver, but has the
   * specified element tacked on to the end.
   *
   * @param last element to add to the end of the returned markers
   * @return new markers sequence with an extra element at the end
   */
  public PathMarkers appended(PathMarker last) {
    return new PathMarkers(new MarkerPrefixAndLast(path, last));
  }

  @Override
  public String toString() {
    return "PathMarkers" + toList();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (other instanceof PathMarkers otherPath) {
      return Objects.equals(path, otherPath);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(path);
  }

  /**
   * Construct an unmodifiable list of path markers
   */
  public List<PathMarker> toList() {
    var output = cachedList.get();

    // If we don't have a cached value, compute the list
    if (output == null) {
      var linkedOutput = new LinkedList<PathMarker>();
      var remainingPath = path;
      while (remainingPath != null) {
        linkedOutput.addFirst(remainingPath.last);
        remainingPath = remainingPath.prefix;
      }
      output = Collections.unmodifiableList(linkedOutput);

      // Cache the computed value
      cachedList = new SoftReference<>(output);
    }

    return output;
  }

  @Override
  public Iterator<PathMarker> iterator() {
    return toList().iterator();
  }

  @Override
  public Spliterator<PathMarker> spliterator() {
    return toList().spliterator();
  }
}
