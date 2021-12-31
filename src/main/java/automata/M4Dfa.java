package automata;

import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Stack;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import static java.util.AbstractMap.SimpleImmutableEntry;

final public class M4Dfa implements DotGraph<Integer, M4Transition>, Dfa<Integer, IntSet, Iterable<GroupMarker>> {

  public record Transition(
    Integer targetState,
    List<GroupMarker> annotation
  ) implements Dfa.Transition<Integer, Iterable<GroupMarker>> { }

  /**
   * State transitions, indexed along starting nodes.
   *
   * This should have entries for all nodes in the finite-state machine
   * (including initial and final ones), and all states should be reachable.
   *
   * None of the nested maps are modifiable.
   */
  public final Map<Integer, Map<IntSet, Transition>> states;

  /**
   * Index of the initial state inside {@code states}.
   */
  public final int initialState;

  /**
   * Index of the final state inside {@code states}.
   */
  public final int finalState;

  private M4Dfa(
    Map<Integer, Map<IntSet, Transition>> states,
    int initialState,
    int finalState
  ) {
    this.states = states;
    this.initialState = initialState;
    this.finalState = finalState;
  }

  /**
   * Count the number of capture groups.
   */
  public int groupCount() {
    return 1 + states
      .values()
      .stream()
      .flatMap(m -> m.values().stream())
      .flatMap((Transition t) -> t.annotation().stream())
      .mapToInt((GroupMarker g) -> g.groupIndex())
      .max()
      .orElse(-1);
  }

  /**
   * Compare two paths using the partial lexicographic order where
   * {@code AlternationMarker.PLUS > AlternationMarker.MINUS} and everything
   * else is incomparable.
   *
   * @param path1 first path
   * @param path2 second path
   * @return -1 if path1 is greater, 1 if path2 is greater, 0 if the paths are equal
   */
  private static int comparePaths(PathMarkers path1, PathMarkers path2) {
    final var iterator1 = path1.iterator();
    final var iterator2 = path2.iterator();

    while (iterator1.hasNext() && iterator2.hasNext()) {
      final PathMarker m1 = iterator1.next();
      final PathMarker m2 = iterator2.next();

      if (!m1.equals(m2)) {
        if (m1 instanceof AlternationMarker a1 && m2 instanceof AlternationMarker a2) {
          return a1.compareTo(a2);
        } else {
          throw new IllegalArgumentException("Incomparable paths " + path1 + " and " + path2);
        }
      }
    }

    return Boolean.compare(iterator1.hasNext(), iterator2.hasNext());
  }

  public static M4Dfa fromM2M3(M2Nfa m2, M3Dfa m3) {

    // Mapping from states in M2 to states in M3 containing the M2 state
    final Map<Integer, Set<IntSet>> stateToPowerState = m3
      .states
      .keySet()
      .stream()
      .flatMap((IntSet powerState) ->
        powerState
          .stream()
          .boxed()
          .map(i -> new SimpleImmutableEntry<Integer, IntSet>(i, powerState))
      )
      .collect(
        Collectors.groupingBy(
          SimpleImmutableEntry::getKey,
          Collectors.mapping(SimpleImmutableEntry::getValue, Collectors.toSet())
        )
      );

    // All `IntSet`s here are powerset states
    final var states = new HashMap<Integer, Map<IntSet, Transition>>();
    final var seenStates = new HashSet<Integer>();
    final var toVisit = new Stack<Integer>();

    // No risk of collision with other states because those are all non-negative
    final int initial = -1;
    toVisit.push(initial);
    seenStates.add(initial);

    while (!toVisit.isEmpty()) {
      final var m2State = toVisit.pop();
      final var output = new HashMap<IntSet, Map.Entry<Integer, PathMarkers>>();

      // Compute state transitions out of this M2 state
      final Stream<Map.Entry<Integer, PathMarkers>> nextStates =
        (m2State == initial)
          ? m2.initialStates.entrySet().stream()
          : m2.states.get(m2State).values().stream().flatMap(map -> map.entrySet().stream());

      // We iterate through the current edges - M4 is just M2 with edges "filtered"
      nextStates.forEach((Map.Entry<Integer, PathMarkers> nextState) -> {
        final int toState = nextState.getKey();

        for (IntSet m3State : stateToPowerState.get(toState)) {
          output.merge(
            m3State,
            nextState,
            (e1, e2) -> comparePaths(e1.getValue(), e2.getValue()) > 0 ? e2 : e1
          );
        }
      });

      // Finalize the output transitions by removing alternation markers
      final var outputTransitions = output
        .entrySet()
        .stream()
        .collect(
          Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            (Map.Entry<IntSet, Map.Entry<Integer, PathMarkers>> e) -> {
              final int to = e.getValue().getKey();
              final var markers = StreamSupport
                .stream(e.getValue().getValue().spliterator(), false)
                .flatMap((PathMarker p) -> {
                  if (p instanceof GroupMarker g) {
                    return Stream.of(g);
                  } else {
                    return Stream.empty();
                  }
                })
                .collect(Collectors.toUnmodifiableList());
              return new Transition(to, markers);
            }
          )
        );
      states.put(m2State, outputTransitions);

      // Update the set of transitions from M2 that are now reachable
      for (Transition outputTransition : outputTransitions.values()) {
        final int to = outputTransition.targetState();
        if (seenStates.add(to)) {
          toVisit.push(to);
        }
      }
    }

    // Ensure that `states` contains _every_ state
    if (!states.containsKey(m2.finalState)) {
      states.put(m2.finalState, Collections.emptyMap());
    }

    return new M4Dfa(
      Collections.unmodifiableMap(states),
      initial,
      m2.finalState
    );
  }

  /**
   * Run against the output from an M3 automata, but in reverse.
   *
   * @param input input test string
   * @param m3Path states visited in M3 (see the output of `M3Dfa.captureSimulate`)
   * @param printDebugInfo print to STDERR a trace of what is happening
   * @return array of match results
   */
  public ArrayMatchResult simulate(
    CharSequence input,
    IntSet[] m3Path,
    boolean printDebugInfo
  ) {
    int currentState = initialState;
    int m3Index = m3Path.length - 1;
    int strOffset = 0;

    final int[] captureGroups = new int[groupCount() * 2];
    Arrays.fill(captureGroups, -1);

    if (printDebugInfo) {
      System.err.println("[M4] starting run on: " + Arrays.toString(m3Path));
    }

    while (m3Index >= 0) {
      final IntSet step = m3Path[m3Index];
      final Transition transition = states.get(currentState).get(step);

      // This should be unreachable if the `m3Path` comes from M3
      if (transition == null) {
        throw new IllegalStateException("No transition from " + currentState + " for " + step);
      }

      // Record groups
      for (GroupMarker group : transition.annotation()) {
        int idx = group.groupIndex() * 2;
        if (!group.isStart()) idx++;

        captureGroups[idx] = strOffset;
        if (printDebugInfo) {
          System.err.println("[M4] capturing $group: groups[" + idx + "] = " + strOffset);
        }
      }

      // Change state
      currentState = transition.targetState();
      if (printDebugInfo) {
        System.err.println("[M4] entering " + currentState);
      }

      m3Index--;
      strOffset++;
    }

    // This should be unreachable if the `m3Path` comes from M3
    if (currentState != finalState) {
      throw new IllegalStateException("Invalid non-final state " + currentState);
    }

    return new ArrayMatchResult(input.toString(), captureGroups);
  }

  @Override
  public Stream<DotGraph.Vertex<Integer>> vertices() {
    return states
      .keySet()
      .stream()
      .map((Integer id) -> new DotGraph.Vertex<Integer>(id, finalState == id));
  }

  @Override
  public Stream<DotGraph.Edge<Integer, M4Transition>> edges() {
    var transitionEdges = states
      .entrySet()
      .stream()
      .flatMap((Map.Entry<Integer, Map<IntSet, Transition>> transitions) -> {
        final int from = transitions.getKey();
        return transitions
          .getValue()
          .entrySet()
          .stream()
          .map((Map.Entry<IntSet, Transition> transition) -> {
            final IntSet m3State = transition.getKey();
            final var trans = new M4Transition(m3State, transition.getValue().annotation());
            return new DotGraph.Edge<>(from, transition.getValue().targetState(), trans);
          });
      });
    var initialEdge = Stream
      .of(new DotGraph.Edge<Integer, M4Transition>(null, initialState, null));
    return Stream.concat(initialEdge, transitionEdges);
  }

  @Override
  public String renderEdgeLabel(DotGraph.Edge<Integer, M4Transition> edge) {
    final var label = edge.label();
    return label == null ? "" : label.dotLabel();
  }

  @Override
  public Integer initial() {
    return initialState;
  }

  @Override
  public Set<Integer> accepting() {
    return Set.of(finalState);
  }

  @Override
  public Map<IntSet, Dfa.Transition<Integer, Iterable<GroupMarker>>> transitionsMap(Integer state) {
    return states
      .get(state)
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()));
  }

  @Override
  public Set<Integer> allStates() {
    return states.keySet();
  }
}
