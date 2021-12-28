package automata;

import java.util.LinkedList;
import java.util.Collections;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Stack;
import java.util.Collection;
import java.util.stream.Stream;

public class M2Nfa implements DotGraph<Integer, M2Transition> {

  /**
   * State transitions, indexed along starting nodes.
   *
   * This should have entries for all nodes in the finite-state machine
   * (including initial and final ones), and all states should be reachable.
   *
   * None of the nested maps are modifiable.
   */
  public final Map<Integer, Map<CodeUnitTransition, Map<Integer, PathMarkers>>> states;

  /**
   * Set of initial states, along with a path marker tag associated with the
   * initial state.
   *
   * The map is not modifiable.
   */
  public final Map<Integer, PathMarkers> initialStates;

  /**
   * Index of the final state inside {@code states}.
   */
  public final int finalState;

  private M2Nfa(
    Map<Integer, Map<CodeUnitTransition, Map<Integer, PathMarkers>>> states,
    Map<Integer, PathMarkers> initialStates,
    int finalState
  ) {
    this.states = states;
    this.initialStates = initialStates;
    this.finalState = finalState;
  }

  /**
   * Construct an M2 from an M1 by collapsing empty transitions (essentially
   * the epsilon-closure, but with some extra book-keeping).
   *
   * We also prune out unreachable transitions by virtue of creating the
   * reduced finite-state machine through a depth first search exploration.
   *
   * @param m1 input M1 finite-state machine
   */
  public static M2Nfa fromM1(M1Dfa m1) {

    /* Invariant: `seenStates` and `toVisit` should always contain states which
     *            are either the terminal state or whose outgoing transitions
     *            are all `CodeUnitTransition`
     */
    final var seenStates = new HashSet<Integer>();
    final var toVisit = new Stack<Integer>();
    final var states = new HashMap<Integer, Map<CodeUnitTransition, Map<Integer, PathMarkers>>>();

    // Mapping of states reachable from the intial M1 state
    final var initialStates = m1.reachableViaPathMarkers(m1.initialState);

    // Seed the DFS
    seenStates.addAll(initialStates.keySet());
    toVisit.addAll(initialStates.keySet());

    while (!toVisit.isEmpty()) {
      var next = toVisit.pop();
      final Map<CodeUnitTransition, Map<Integer, PathMarkers>> transitionMap = new HashMap<>();

      // Iterate through the transitions (which should all be code unit ones)
      for (var transitionEntry : m1.states.get(next).entrySet()) {
        int to = transitionEntry.getValue();

        if (transitionEntry.getKey() instanceof CodeUnitTransition transition) {
          var reachable = m1.reachableViaPathMarkers(to);
          transitionMap.put(transition, Collections.unmodifiableMap(reachable));

          // Make sure we go visit all of the states we can reach over paths
          for (var reachableState : reachable.keySet()) {
            if (seenStates.add(reachableState)) {
              toVisit.push(reachableState);
            }
          }
        } else {
          throw new IllegalArgumentException("unexpected non-CodeUnitTransition");
        }
      }

      states.put(next, Collections.unmodifiableMap(transitionMap));
    }

    // TODO: this means the automata is _never_ accepting
    if (!states.containsKey(m1.finalState)) {
      throw new IllegalArgumentException("terminal state is not reachable");
    }

    return new M2Nfa(
      Collections.unmodifiableMap(states),
      Collections.unmodifiableMap(initialStates),
      m1.finalState
    );
  }

  @Override
  public Stream<DotGraph.Vertex<Integer>> vertices() {
    return states
      .keySet()
      .stream()
      .map((Integer id) -> new DotGraph.Vertex<Integer>(id, id == finalState));
  }

  @Override
  public Stream<DotGraph.Edge<Integer, M2Transition>> edges() {
    var transitionEdges = states
      .entrySet()
      .stream()
      .flatMap((Map.Entry<Integer, Map<CodeUnitTransition, Map<Integer, PathMarkers>>> transitions) -> {
        final Integer from = transitions.getKey();
        return transitions
          .getValue()
          .entrySet()
          .stream()
          .flatMap((Map.Entry<CodeUnitTransition, Map<Integer, PathMarkers>> perCodeUnit) -> {
            final CodeUnitTransition codeUnitTrans = perCodeUnit.getKey();
            return perCodeUnit
              .getValue()
              .entrySet()
              .stream()
              .map((Map.Entry<Integer, PathMarkers> pathEntry) -> {
                final Integer to = pathEntry.getKey();
                final PathMarkers path = pathEntry.getValue();
                final var transition = new M2Transition(codeUnitTrans, path);
                return new DotGraph.Edge<>(from, to, transition);
              });
          });
      });
    var initialEdges = initialStates
      .entrySet()
      .stream()
      .map((Map.Entry<Integer, PathMarkers> initial) -> {
        final var transition = new M2Transition(null, initial.getValue());
        return new DotGraph.Edge<>(null, initial.getKey(), transition);
      });
    return Stream.concat(initialEdges, transitionEdges);
  }

  @Override
  public String renderEdgeLabel(DotGraph.Edge<Integer, M2Transition> edge) {
    final M2Transition label = edge.label();
    return label == null ? "" : label.dotLabel();
  }
}
