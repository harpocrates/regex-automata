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
 */
public class GroupMarkers {

  /**
   * Mapping from groups to their fixed group classes.
   *
   * <p>Every member and representative of a class must be in this map and point
   * to the class.
   */
  Map<GroupMarker, FixedClass> groupClasses = new HashMap<>();

  /**
   * Fixity classes.
   */
  Map<GroupMarker, FixedClass> fixityClasses = new HashMap<>();

  /**
   * Fixed group class which has a non-empty distance from start of input.
   *
   * <p>There can be at most one class with {@code distanceToStart} non-empty,
   * and this field must have a reference to it.
   */
  FixedClass startClass = null;

  /**
   * Fixed group class which has a non-empty distance to end of input.
   *
   * <p>There can be at most one class with {@code distanceToEnd} non-empty,
   * and this field must have a reference to it.
   */
  FixedClass endClass = null;

  /**
   * Equivalence class of tags fixed distances away from each other.
   */
  public class FixedClass {

    public String compactString() {
      final var builder = new StringBuilder();
      builder.append("{ ");
      builder.append(representative.compactString());

      for (final var member : memberDistances.entrySet()) {
        builder.append(", ");
        builder.append(member.getKey().compactString());
        builder.append(":");
        builder.append(member.getValue());
      }

      distanceToStart.ifPresent((int dist) -> {
        builder.append(", ^:");
        builder.append(dist);
      });

      distanceToEnd.ifPresent((int dist) -> {
        builder.append(", ^:");
        builder.append(dist);
      });

      builder.append(" }");

      return builder.toString();
    }

    /**
     * Representative of the class.
     */
    public final GroupMarker representative;

    /**
     * Is the marker unavoidable.
     */
    public final boolean unavoidable;

    /**
     * Distance from the start of the input to the representative.
     */
    public OptionalInt distanceToStart = OptionalInt.empty();

    /**
     * Distance from the representative to the end of the input.
     */
    public OptionalInt distanceToEnd = OptionalInt.empty();

    /**
     * Members of the class along with the distance from the representative to
     * the member.
     *
     * <p>Does not include the representative as a key.
     */
    public HashMap<GroupMarker, Integer> memberDistances = new HashMap<>();

    FixedClass(GroupMarker representative, boolean unavoidable) {
      this.representative = representative;
      this.unavoidable = unavoidable;
    }

    /**
     * Compute the distance to the specified marker from the representative.
     *
     * @param from starting at this marker (in the class)
     */
    int distanceToRepresentative(GroupMarker from) {
      if (from.equals(representative)) {
        return 0;
      } else {
        final Integer distance = memberDistances.get(from);
        if (distance == null) {
          throw new NoSuchElementException();
        } else {
          return distance.intValue();
        }
      }
    }
  }

  /**
   * Set of all group markers that need to be tracked in order for the full set
   * of group markers to be derived.
   *
   * @param mode matching mode
   * @return minimal set of tags to track
   */
  public Set<GroupMarker> trackedGroupMarkers(MatchMode mode) {
    final var toTrack = new HashSet<GroupMarker>(fixityClasses.keySet());

    if (startClass != null) {
      toTrack.remove(startClass.representative);
    }

    // Full matches must end at the end of the input
    if (endClass != null && mode == MatchMode.FULL) {
      toTrack.remove(endClass.representative);
    }

    return toTrack;
  }

  /**
   * Count of pairs of group markers.
   */
  public int groupCount() {
    return groupClasses.size() / 2;
  }

  /**
   * All fixity classes.
   */
  public Collection<FixedClass> classes() {
    return fixityClasses.values();
  }

  /**
   * Class that is a fixed distance from the start offset.
   */
  public Optional<FixedClass> startClass() {
    return Optional.ofNullable(startClass);
  }

  /**
   * Class that is a fixed distance from the end offset.
   */
  public Optional<FixedClass> endClass() {
    return Optional.ofNullable(endClass);
  }

  /**
   * Add a group marker a fixed distance away from the start/end of the input.
   *
   * <p>This can be called at most twice: once for the start and once for the
   * end.
   *
   * @param group fixed group marker (possibly previously seen)
   * @param distance how far the group is from/to the start/end
   * @param relativeToStart whether it is relative to the start or end
   */
  public void addAbsoluteFixedGroup(
    GroupMarker group,
    int distance,
    boolean relativeToStart
  ) {
    // Guard for the endpoint not already being in a group
    if (relativeToStart && startClass != null) {
      throw new IllegalArgumentException("Start class is already specified");
    } else if (!relativeToStart && endClass != null) {
      throw new IllegalArgumentException("End class is already specified");
    }

    // Check if we need to create a new class or can re-use the existing one
    FixedClass groupClass = groupClasses.get(group);
    if (groupClass != null) {
      final int repToGroup = groupClass.distanceToRepresentative(group);
      if (relativeToStart) {
        groupClass.distanceToStart = OptionalInt.of(distance + repToGroup);
        startClass = groupClass;
      } else {
        groupClass.distanceToEnd = OptionalInt.of(distance + repToGroup);
        endClass = groupClass;
      }
    } else {
      if (relativeToStart) {
        startClass = new FixedClass(group, true);
        startClass.distanceToStart = OptionalInt.of(distance);
        groupClasses.put(group, startClass);
        fixityClasses.put(group, startClass);
      } else {
        endClass = new FixedClass(group, true);
        endClass.distanceToEnd = OptionalInt.of(distance);
        groupClasses.put(group, endClass);
        fixityClasses.put(group, endClass);
      }
    }
  }

  /**
   * Add a new group which is relative to another group.
   *
   * @param group new group (not previously seen)
   * @param distance distance from new group to relative group
   * @param unavoidable are the two groups unavoidable during matching?
   * @param relativeTo relative group (possibly previously seen)
   */
  public void addRelativeFixedGroup(
    GroupMarker group,
    int distance,
    boolean unavoidable,
    GroupMarker relativeTo
  ) {
    // Guard for the group actually being new
    if (groupClasses.containsKey(group)) {
      throw new IllegalArgumentException("Group is not new: " + group);
    }

    // Check if we need to create a new class or can re-use the existing one
    final FixedClass relativeToCls = groupClasses.get(relativeTo);
    if (relativeToCls != null) {
      final int repToRelativeTo = relativeToCls.distanceToRepresentative(relativeTo);
      relativeToCls.memberDistances.put(group, repToRelativeTo - distance);
      groupClasses.put(group, relativeToCls);
    } else {
      final var cls = new FixedClass(relativeTo, unavoidable);
      cls.memberDistances.put(group, -distance);
      groupClasses.put(relativeTo, cls);
      groupClasses.put(group, cls);
      fixityClasses.put(relativeTo, cls);
    }
  }

  /**
   * Add a new group which is independent from existing groups.
   *
   * @param group new group (not previously seen)
   * @param unavoidable is the group unavoidable during matching?
   */
  public void addGroup(
    GroupMarker group,
    boolean unavoidable
  ) {
    // Guard for the group actually being new
    if (groupClasses.containsKey(group)) {
      throw new IllegalArgumentException("Group is not new: " + group);
    }

    final FixedClass cls = new FixedClass(group, unavoidable);
    groupClasses.put(group, cls);
    fixityClasses.put(group, cls);
  }
}
