package automata.graph;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Collection of group markers, grouped along equivalence classes that are a
 * fixed distance from each other.
 *
 * <p>One of the ways to reduce the number of commands that are in a TDFA is to
 * reduce the number of group markers being tracked. This is possible since
 * group markers are often located at fixed distances from other groups, from
 * the start of the input, or from the end of the input.
 *
 * <p>For example, consider the pattern {@code (a(b*)(c*)a)} which has 3 groups
 * (the outer group is explicit here, although it is usually implicit). Out of
 * these tags:
 *
 * <ul>
 *   <li>{@code S0} and {@code S1} are 0 and 1 away from the start of the input
 *   <li>{@code E1} and {@code S2} are equal
 *   <li>{@code E0} and {@code E2} are 0 and -1 away from the end of the input
 * </ul>
 *
 * <p>This means that if this is running with a fixed start/end offset, only
 * {@code E1} or {@code S2} has to be tracked in the TDFA: every other marker's
 * position can be derived!
 *
 * <h1>Implementation notes</h1>
 *
 * <p>Internally, this uses a variant of a merge-find tree, where nodes track
 * both their parent and their distance to their parent. The motivation for
 * using merge-find is to make it fast to merge two equivalence classes. Unlike
 * the usual merge-find trees, the way that roots get merged is chosen such
 * that the parent distance is non-negative. This means that trees <em>may</em>
 * become imbalanced, but it also means we can count on representative roots to
 * always be the rightmost of their class. Tracking the rightmost marker means
 * that we set variables later, which leads to less tags, more states that can
 * be merged, and less work to do if the regex fails early.
 */
public class GroupMarkers {

  /**
   * All group markers being tracked.
   */
  final HashMap<GroupMarker, MergeFindNode> groups = new HashMap<>();

  /**
   * Fixed group class which has a non-empty distance from start of input.
   *
   * <p>There can be at most one class with {@code distanceToStart} non-empty,
   * and this field must have a reference to it.
   */
  MergeFindNode startClass = null;

  /**
   * Distance from the {@link #startClass} representative to the start of the
   * input.
   *
   * <p>Only relevant if {@link #startClass} is defined.
   *
   * <p>This is generally going to be negative since the start of the input
   * occurs before groups (barring any weirdness about groups in lookbehinds)
   */
  int distanceToStart = 0;

  /**
   * Fixed group class which has a non-empty distance to end of input.
   *
   * <p>There can be at most one class with {@code distanceToEnd} non-empty,
   * and this field must have a reference to it.
   */
  MergeFindNode endClass = null;

  /**
   * Distance from the {@link #endClass} representative the end of the input.
   *
   * <p>Only relevant if {@link #endClass} is defined.
   *
   * <p>This is generally going to be positive since the end of the input
   * occurs after groups (barring any weirdness about groups in lookaheads)
   */
  int distanceToEnd = 0;

  /**
   * Node in a merge-find tree.
   */
  private class MergeFindNode {

    /**
     * Parent node, or a self-reference if this is a root node.
     *
     * <p>The self-reference is not just a representation gimmick - it makes
     * for a more efficient implementation of path compression.
     */
    MergeFindNode parent = this;

    /**
     * Distance to the parent node, or {@code 0} if this is a root node.
     *
     * <p>This distance is non-negative, since we always want the nodes higher
     * in the tree to correspond to positions that are further right.
     */
    int distanceToParent = 0;

    /**
     * Is the group unavoidable.
     */
    boolean unavoidable = false;

    /**
     * Group marker associated with the node.
     */
    final GroupMarker marker;

    MergeFindNode(GroupMarker marker) {
      this.marker = marker;
    }
  }

  /**
   * Equivalence classes of tags fixed distances away from each other.
   *
   * <p>Every group marker should show up in exactly one of:
   *
   * <ul>
   *   <li>key inside {@link #fixedToStart}
   *   <li>key inside {@link #fixedToEnd}
   *   <li>key inside outer map of {@link #fixedToMarker}
   *   <li>key inside and inner map of {@link #fixedToMarker}
   * </ul>
   */
  public class FixedClasses {

    /**
     * Class fixed to the start of the input.
     *
     * <p>Keys in the map are groups fixed to the start of the input and values
     * are the distances (usually negative) from those markers to the start of
     * the input.
     */
    public final Map<GroupMarker, Integer> fixedToStart;

    /**
     * Class fixed to the end of the input.
     *
     * <p>Keys in the map are groups fixed to the end of the input and values
     * are the distances (usually negative) from those markers to the start of
     * the input.
     */
    public final Map<GroupMarker, Integer> fixedToEnd;

    /**
     * Classes fixed to another group.
     *
     * <p>Keys in the outer map are the representatives from the groups and
     * values are the set of groups fixed to that representative (with values
     * in the values being the distance from the fixed group to its
     * representative).
     */
    public final Map<GroupMarker, Map<GroupMarker, Integer>> fixedToMarker;

    /**
     * All unavoidable group markers.
     */
    public final Set<GroupMarker> unavoidable;

    /**
     * Total number of pairs of groups.
     */
    public final int groupCount;

    FixedClasses(
      Map<GroupMarker, Integer> fixedToStart,
      Map<GroupMarker, Integer> fixedToEnd,
      Map<GroupMarker, Map<GroupMarker, Integer>> fixedToMarker,
      Set<GroupMarker> unavoidable,
      int groupCount
    ) {
      this.fixedToStart = fixedToStart;
      this.fixedToEnd = fixedToEnd;
      this.fixedToMarker = fixedToMarker;
      this.unavoidable = unavoidable;
      this.groupCount = groupCount;
    }
  }

  /**
   * Set of fixed classes that need to be tracked in order for the full set
   * of group markers to be derived.
   *
   * @param mode matching mode
   * @return equivalence classes of markers to track
   */
  public FixedClasses fixedClasses(MatchMode mode) {
    final var fixedToStart = new HashMap<GroupMarker, Integer>();
    final var fixedToEnd = new HashMap<GroupMarker, Integer>();
    final var fixedToMarker = new HashMap<GroupMarker, Map<GroupMarker, Integer>>();
    final var unavoidable = new HashSet<GroupMarker>();

    for (final var entry : groups.entrySet()) {
      final GroupMarker marker = entry.getKey();
      final MergeFindNode node = entry.getValue();

      // Lookup the representative
      final var rootAndDistance = getRoot(node);
      final MergeFindNode root = rootAndDistance.node;
      final int distToRoot = rootAndDistance.distance;
      if (root.unavoidable) {
        unavoidable.add(marker);
      }

      // All matches start at the start of the input (`find` just starts with `.*`)
      if (root == startClass) {
        fixedToStart.put(marker, distToRoot + distanceToStart);
        continue;
      }

      // Full matches must end at the end of the input
      if (root == endClass && mode == MatchMode.FULL) {
        fixedToEnd.put(marker, distToRoot + distanceToEnd);
        continue;
      }

      // This is part of a fixed class
      final var cls = fixedToMarker.computeIfAbsent(root.marker, k -> new HashMap<>());
      if (root != node) {
        cls.put(marker, distToRoot);
      }
    }

    return new FixedClasses(
      fixedToStart,
      fixedToEnd,
      fixedToMarker,
      unavoidable,
      groups.size() / 2
    );
  }

  static class MergeFindNodeAndDistance {
    public final MergeFindNode node;
    public final int distance;

    MergeFindNodeAndDistance(MergeFindNode node, int distance) {
      this.node = node;
      this.distance = distance;
    }
  }

  /**
   * Get the root of a node in the set.
   *
   * <p>This performs path compression (specifically Tarjan's path splitting
   * strategy). This gradually flattens the tree until it is just the root and
   * direct children.
   *
   * @param node node to query
   * @return root node and the distance between the node and the root node
   */
  private MergeFindNodeAndDistance getRoot(MergeFindNode node) {
    int distance = 0;

    // The root's parent reference is a self-reference
    while (node.parent != node) {
      final var oldParent = node.parent;

      // Collapse the two edges between `node` and `node.parent.parent` into one
      node.parent = oldParent.parent;
      node.distanceToParent += oldParent.distanceToParent;

      // Move one edge closer to the root of the tree
      distance += node.distanceToParent;
      node = oldParent;
    }
    return new MergeFindNodeAndDistance(node, distance);
  }

  /**
   * Merge equivalence classes given their two roots.
   *
   * @param root1 first root
   * @param distRoot1ToRoot2 distance from the first root to the second root
   * @param root2 second root
   */
  private void mergeRoots(
    MergeFindNode root1,
    int distRoot1ToRoot2,
    MergeFindNode root2
  ) {
    /* Ensure distance from `root1` to `root2` is positive.
     *
     * Normally, this is where the new root node would decided based on the
     * ranks or sizes of the two roots being merged, but we care about another
     * invariant more: that the distances along parent edges are always
     * non-negative.
     */
    if (distRoot1ToRoot2 < 0) {
      distRoot1ToRoot2 = -distRoot1ToRoot2;
      final var temp = root1;
      root1 = root2;
      root2 = temp;
    }

    // `root1` is now a child of `root2`
    root1.parent = root2;
    root1.distanceToParent = distRoot1ToRoot2;

    if (root1.unavoidable != root2.unavoidable) {
      root2.unavoidable = root1.unavoidable;
    }

    // Merge information about distance to start/end
    if (startClass == root1) {
      distanceToStart -= distRoot1ToRoot2;
      startClass = root2;
    }
    if (endClass == root1) {
      distanceToEnd -= distRoot1ToRoot2;
      endClass = root2;
    }
  }

  /**
   * Record a distance to the start or end of the input.
   *
   * @param group group which is a fixed distance from the start/end
   * @param distance distance to start/end
   * @param isStart is the distance to the start or to the end?
   */
  public void recordDistanceToEndpoint(
    GroupMarker group,
    int distance,
    boolean isStart
  ) {
    final var node = groups.get(group);

    if (node == null) {
      throw new NoSuchElementException("Unknown group: " + group);
    }

    final var rootAndDistance = getRoot(node);
    final int distNodeToRoot = rootAndDistance.distance;
    final var root = rootAndDistance.node;

    if (isStart) {
      if (startClass == null) {
        startClass = root;
        distanceToStart = distance - distNodeToRoot;
      } else if (startClass == root) {
        if (distanceToStart != distance) {
          throw new IllegalStateException(
            "Group " + group +
              " is already a fixed distance from the start of " +
              distanceToStart + " (different from user-request distance " +
              distance + ")"
          );
        }
      } else {
        mergeRoots(startClass, distance - distanceToStart, root);
      }
    } else {
      if (endClass == null) {
        endClass = root;
        distanceToEnd = distance - distNodeToRoot;
      } else if (endClass == root) {
        if (distanceToEnd != distance) {
          throw new IllegalStateException(
            "Group " + group +
              " is already a fixed distance from the end of " +
              distanceToEnd + " (different from user-request distance " +
              distance + ")"
          );
        }
      } else {
        mergeRoots(endClass, distance - distanceToEnd, root);
      }
    }
  }

  /**
   * Record that two groups are a fixed distance from each other.
   *
   * @param group1 first group
   * @param distance distance from the first to the second group
   * @param group2 second group
   */
  public void recordFixedDistance(GroupMarker group1, int distance, GroupMarker group2) {
    final var node1 = groups.get(group1);
    final var node2 = groups.get(group2);

    if (node1 == null) {
      throw new NoSuchElementException("Unknown starting group: " + group1);
    } else if (node2 == null) {
      throw new NoSuchElementException("Unknown ending group: " + group2);
    }

    final var rootAndDistance1 = getRoot(node1);
    final var rootAndDistance2 = getRoot(node2);
    final var root1 = rootAndDistance1.node;
    final var root2 = rootAndDistance2.node;
    final int distNode1ToRoot1 = rootAndDistance1.distance;
    final int distNode2ToRoot2 = rootAndDistance2.distance;

    if (root1 != root2) {
      mergeRoots(root1, distance - distNode1ToRoot1 + distNode2ToRoot2, root2);
    } else {
      final int distNode1ToNode2 = distNode1ToRoot1 - distNode2ToRoot2;
      if (distance != distNode1ToNode2) {
        throw new IllegalStateException(
          "Group " + group1 + " is already at a fixed distance to " + group2 +
            " computed to be " + distNode1ToNode2 +
            " (different from user-specified distance " + distance + ")"
        );
      }
    }
  }

  /**
   * Add a new group, not part of any existing equivalence class.
   *
   * @param group new group (not previously seen)
   * @param unavoidable is the group unavoidable during matching?
   */
  public void addFreshGroup(
    GroupMarker group,
    boolean unavoidable
  ) {
    // Guard for the group actually being new
    if (groups.containsKey(group)) {
      throw new IllegalArgumentException("Group is not fresh: " + group);
    }

    final var node = new MergeFindNode(group);
    node.unavoidable = unavoidable;
    groups.put(group, node);
  }
}
