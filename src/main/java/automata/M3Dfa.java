package automata;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import java.util.Collections;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.Optional;

public final class M3Dfa implements DotGraph<IntSet, CodeUnitTransition>, Dfa<IntSet, CodeUnitTransition, Void> {

  /**
   * State transitions, indexed along starting nodes.
   *
   * This should have entries for all nodes in the finite-state machine
   * (including initial and final ones), and all states should be reachable.
   *
   * None of the nested maps are modifiable.
   */
  public final Map<IntSet, Map<CodeUnitTransition, IntSet>> states;

  /**
   * Initial state.
   */
  public final IntSet initialState;

  /**
   * Accepting states.
   */
  public final Set<IntSet> finalStates;

  private M3Dfa(
    Map<IntSet, Map<CodeUnitTransition, IntSet>> states,
    IntSet initialState,
    Set<IntSet> finalStates
  ) {
    this.states = states;
    this.initialState = initialState;
    this.finalStates = finalStates;
  }

  /**
   * Construct an M3 from an M2 using a powerset construction on reversed edges.
   *
   * @param m2 input M2 finite-state machine
   */
  public static M3Dfa fromM2(M2Nfa m2) {
    record IncomingEdge(CodeUnitTransition codeUnitTransition, int from) { }

    // Mapping from state to set of incoming edges
    final Map<Integer, Set<IncomingEdge>> reversedEdges = m2
      .states
      .keySet()
      .stream()
      .collect(Collectors.toMap(i -> i, i -> new HashSet<>()));
    for (var transitions : m2.states.entrySet()) {
      final int from = transitions.getKey();
      for (var transition : transitions.getValue().entrySet()) {
        final CodeUnitTransition codeUnit = transition.getKey();
        for (var to : transition.getValue().keySet()) {
          reversedEdges.get(to).add(new IncomingEdge(codeUnit, from));
        }
      }
    }

    // All `IntSet`s here are powerset states
    final var states = new HashMap<IntSet, Map<CodeUnitTransition, IntSet>>();
    final var seenStates = new HashSet<IntSet>();
    final var toVisit = new Stack<IntSet>();

    final var initialState = IntSet.of(m2.finalState);
    seenStates.add(initialState);
    toVisit.push(initialState);

    while (!toVisit.isEmpty()) {
      final IntSet powerState = toVisit.pop();

      // Look up all the transitions from the constituent states
      final Map<CodeUnitTransition, IntSet> transitions = powerState
        .stream()
        .mapToObj(reversedEdges::get)
        .<IncomingEdge>flatMap(Set::stream)
        .collect(
          Collectors.groupingBy(
            IncomingEdge::codeUnitTransition,
            Collectors.mapping(
              IncomingEdge::from,
              Collectors.collectingAndThen(Collectors.toSet(), IntSet::new)
            )
          )
        );

      states.put(powerState, Collections.unmodifiableMap(transitions));

      for (var outState : transitions.values()) {
        if (seenStates.add(outState)) {
          toVisit.push(outState);
        }
      }
    }

    final Set<IntSet> finalStates = states
      .keySet()
      .stream()
      .filter(key -> key.stream().anyMatch(i -> m2.initialStates.containsKey(i)))
      .collect(Collectors.toSet());

    return new M3Dfa(
      Collections.unmodifiableMap(states),
      initialState,
      Collections.unmodifiableSet(finalStates)
    );
  }

  /**
   * Run a regex on an input.
   *
   * @param input input string
   * @param printDebugInfo print to STDERR a trace of what is happening
   * @return the array of states if it matched (in the order seen), or else `null`
   */
  public IntSet[] captureSimulate(
    CharSequence input,
    boolean printDebugInfo
  ) {
    IntSet currentState = initialState;
    int strOffset = input.length();

    final IntSet[] m3Path = new IntSet[input.length() + 1];
    int m3PathOff = 0;

    if (printDebugInfo) {
      System.err.println("[M3] starting run on: " + input);
    }

    while (strOffset-- > 0) {
      m3Path[m3PathOff++] = currentState;

      // TODO: this should not require scanning over all states
      final char codeUnit = input.charAt(strOffset);
      final Optional<IntSet> nextStateOpt = states
        .get(currentState)
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey().codeUnitSet().contains(codeUnit))
        .findFirst()
        .map(Map.Entry::getValue);

      if (nextStateOpt.isPresent()) {
        currentState = nextStateOpt.get();
        if (printDebugInfo) {
          System.err.println("[M3] entering " + currentState);
        }
      } else {
        if (printDebugInfo) {
          System.err.println("[M3] ending run at " + currentState + "; no transition for " + codeUnit);
        }
        return null;
      }
    }

    m3Path[m3PathOff] = currentState;
    return finalStates.contains(currentState) ? m3Path : null;
  }

  /**
   * Run a regex on an input.
   *
   * Equivalent to {@code captureSimulate(input) != null} but more efficient
   *
   * @param input input string
   * @param printDebugInfo print to STDERR a trace of what is happening
   * @return whether the input matches
   */
  public boolean checkSimulate(
    CharSequence input,
    boolean printDebugInfo
  ) {
    IntSet currentState = initialState;
    int strOffset = input.length();

    if (printDebugInfo) {
      System.err.println("[M3] starting run on: " + input);
    }

    while (strOffset-- > 0) {

      // TODO: this should not require scanning over all states
      final char codeUnit = input.charAt(strOffset);
      final Optional<IntSet> nextStateOpt = states
        .get(currentState)
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey().codeUnitSet().contains(codeUnit))
        .findFirst()
        .map(Map.Entry::getValue);

      if (nextStateOpt.isPresent()) {
        currentState = nextStateOpt.get();
        if (printDebugInfo) {
          System.err.println("[M3] entering " + currentState);
        }
      } else {
        if (printDebugInfo) {
          System.err.println("[M3] ending run at " + currentState + "; no transition for " + codeUnit);
        }
        return false;
      }
    }

    return finalStates.contains(currentState);
  }

  @Override
  public Stream<DotGraph.Vertex<IntSet>> vertices() {
    return states
      .keySet()
      .stream()
      .map((IntSet id) -> new DotGraph.Vertex<IntSet>(id, finalStates.contains(id)));
  }

  @Override
  public Stream<DotGraph.Edge<IntSet, CodeUnitTransition>> edges() {
    var transitionEdges = states
      .entrySet()
      .stream()
      .flatMap((Map.Entry<IntSet, Map<CodeUnitTransition, IntSet>> transitions) -> {
        final IntSet from = transitions.getKey();
        return transitions
          .getValue()
          .entrySet()
          .stream()
          .map((Map.Entry<CodeUnitTransition, IntSet> transition) -> {
            final CodeUnitTransition codeUnitTrans = transition.getKey();
            return new DotGraph.Edge<>(from, transition.getValue(), codeUnitTrans);
          });
      });
    var initialEdge = Stream
      .of(new DotGraph.Edge<IntSet, CodeUnitTransition>(null, initialState, null));
    return Stream.concat(initialEdge, transitionEdges);
  }

  @Override
  public String renderEdgeLabel(DotGraph.Edge<IntSet, CodeUnitTransition> edge) {
    final CodeUnitTransition label = edge.label();
    return label == null ? "" : label.dotLabel();
  }

  @Override
  public IntSet initial() {
    return initialState;
  }

  @Override
  public Set<IntSet> accepting() {
    return finalStates;
  }

  @Override
  public Set<IntSet> allStates() {
    return states.keySet();
  }

  @Override
  public Map<CodeUnitTransition, Dfa.Transition<IntSet, Void>> transitionsMap(IntSet state) {
    record Transition(IntSet targetState) implements Dfa.Transition<IntSet, Void> {
      @Override
      public Void annotation() {
        return null;
      }
    }

    return states
      .get(state)
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> new Transition(e.getValue())));
  }
}
