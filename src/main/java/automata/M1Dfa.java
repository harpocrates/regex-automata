package automata;

import static automata.M2Nfa.EmptyPath;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedMap;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.Collections;
import java.util.Arrays;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import static java.util.AbstractMap.SimpleImmutableEntry;
import java.text.ParseException;

/* Notes:
 *
 *  - the FSM is not necessarily a connected graph. It is possible to construct a character class
 *    that is empty: "a[b&&c]d" (no character can be `b` and `c` at the same time).
 *
 */
public class M1Dfa implements Dfa<Integer, M1Transition, Void> {

  private record TransitionTarget(int to) implements Fsm.Transition<Integer, M1Transition, Void> {
    @Override
    public Void annotation() {
      return null;
    }

    @Override
    public Integer targetState() {
      return to;
    }

    @Override
    public String dotLabel(Integer from, M1Transition over) {
      return over.dotLabel();
    }
  }

  /**
   * States are numbered using consecutive indices, so we can use an array to
   * compactly track the list of transitions out of every state.
   */
  private final Map<M1Transition, TransitionTarget>[] states;

  /**
   * Index of the initial state inside {@code states}.
   */
  public final int initialState;

  /**
   * Index of the final state inside {@code states}.
   */
  public final int finalState;

  /**
   * Cached (unmodifiable) set of all of the keys.
   */
  private final Set<Integer> allStates;

  private M1Dfa(
    Map<M1Transition, TransitionTarget>[] states,
    int initialState,
    int finalState
  ) {
    this.states = states;
    this.initialState = initialState;
    this.finalState = finalState;

    this.allStates = Collections.unmodifiableSet(
      IntStream.range(0, states.length).boxed().collect(Collectors.toSet())
    );
  }

  public static M1Dfa parse(String pattern) throws ParseException {
    final var builder = new Builder();
    final var visited = RegexParser.parse(builder, pattern);
    return builder.constructDfa(visited);
  }

  public static class Builder extends RegexNfaBuilder<Integer> {
    // Used to buffer up transitions
    record BufferedTransition(int from, M1Transition transition, int to) { }

    private boolean used = false;
    private int lastState = 0;
    private final LinkedList<BufferedTransition> transitions = new LinkedList<>();

    @Override
    public Integer freshState() {
      return lastState++;
    }

    @Override
    public void addPlusMinusState(Integer from, Integer plus, Integer minus) {
      transitions.addLast(new BufferedTransition(from, AlternationMarker.MINUS, minus));
      transitions.addLast(new BufferedTransition(from, AlternationMarker.PLUS, plus));
    }

    @Override
    public void addCodeUnitsState(Integer from, IntRangeSet codeUnits, Integer to) {
      transitions.addLast(new BufferedTransition(from, new CodeUnitTransition(codeUnits), to));
    }

    @Override
    public void addGroupState(Integer from, int groupIdx, boolean isStart, Integer to) {
      final var marker = isStart ? GroupMarker.start(groupIdx) : GroupMarker.end(groupIdx);
      transitions.addLast(new BufferedTransition(from, marker, to));
    }

    @Override
    public void addBoundaryState(Integer from, RegexVisitor.Boundary boundary, Integer to) {
      throw new UnsupportedOperationException("boundaries are not yet supported in M1 DFAs");
    }

    /**
     * Finalize the construction of the M1 DFA.
     *
     * @param visitorOutput output from visiting the regular expression
     * @param initialState which state is the starting one
     * @param finalState which state is the accepting one
     * @return valid M1 finite state machine
     */
    public M1Dfa constructDfa(UnaryOperator<Integer> visitorOutput) {
      if (used) {
        throw new IllegalStateException("construct may only be called once on an M1 builder");
      } else {
        used = true;
      }

      // "Run" the visitor output, populating `transitions` as a side-effect
      final int finalState = freshState();
      final int initialState = visitorOutput.apply(finalState);

      // Initialize the states array to the right length
      @SuppressWarnings({"unchecked"})
      final Map<M1Transition, TransitionTarget>[] states = (Map<M1Transition, TransitionTarget>[]) new Map[lastState];
      Arrays.setAll(states, i -> new LinkedHashMap<M1Transition, TransitionTarget>());

      // Extract out an identity mapping of all code unit sets from code unit transitions
      final Map<IntRangeSet, IntRangeSet> identityCodeTransitions = new HashMap<IntRangeSet, IntRangeSet>();
      for (BufferedTransition buffered : transitions) {
        if (buffered.transition instanceof CodeUnitTransition trans) {
           identityCodeTransitions.put(trans.codeUnitSet(), trans.codeUnitSet());
        } else {
           states[buffered.from].put(buffered.transition, new TransitionTarget(buffered.to));
        }
      }

      // Determine for each initial code unit transition the collection of new disjoint ranges
      final Map<IntRangeSet, List<IntRangeSet>> partitionedTransitions = IntRangeSet
        .disjointPartition(identityCodeTransitions)
        .entrySet()
        .stream()
        .flatMap((Map.Entry<Set<IntRangeSet>, IntRangeSet> entry) -> {
          final var inputRange = entry.getValue();
          return entry
            .getKey()
            .stream()
            .map(outputRange -> new SimpleImmutableEntry<>(inputRange, outputRange));
        })
        .collect(Collectors.groupingBy(
          SimpleImmutableEntry::getValue,
          Collectors.mapping(SimpleImmutableEntry::getKey, Collectors.toList())
        ));

      // Add in all character transitions
      for (BufferedTransition buffered : transitions) {
        if (buffered.transition instanceof CodeUnitTransition trans) {
          for (IntRangeSet partitionSubset : partitionedTransitions.get(trans.codeUnitSet())) {
            states[buffered.from].put(new CodeUnitTransition(partitionSubset), new TransitionTarget(buffered.to));
          }
        }
      }

      // Defensively prevent updates to the state maps
      for (int i = 0; i < states.length; i++) {
        states[i] = Collections.unmodifiableMap(states[i]);
      }
      return new M1Dfa(states, initialState, finalState);
    }
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
  public Set<Integer> allStates() {
    return allStates;
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Map<M1Transition, Fsm.Transition<Integer, M1Transition, Void>> transitionsMap(Integer state) {
    return (Map) states[state];
  }

  /**
   * Starting from the specified state, traverse 0 or more path marker edges,
   * prioritizing `PLUS` over `MINUS` and stopping at states which have
   * outgoing code point transitions.
   *
   * This uses a version of DFS where we try `PLUS` before `MINUS` (by virtue
   * of the linked hashmap order). This means that the runtime is {@code O(E)},
   * but practically speaking the sub-graphs for marker paths tend to be much
   * smaller (and often not connected to each other).
   *
   * @param startingState state from which to start the search
   * @return mapping from states reachable via marker paths to those paths
   */
  public Map<Integer, EmptyPath> exploreMarkerPaths(int startingState) {
    record ToVisit(int state, EmptyPath pathToState) { }

    final var seenStates = new HashSet<Integer>();
    final var toVisit = new Stack<ToVisit>();
    final var output = new HashMap<Integer, EmptyPath>();

    // Seed the DFS with the starting node
    toVisit.push(new ToVisit(startingState, null));
    seenStates.add(startingState);

    // DFS loop
    while (!toVisit.isEmpty()) {
      final var next = toVisit.pop();

      // The terminal state is special - it is always included in the output
      if (next.state() == finalState) {
        output.put(next.state(), next.pathToState());
        continue;
      }

      for (var entry : states[next.state()].entrySet()) {
        int entryTo = entry.getValue().to();

        // If the transition out of this state is not a path marker, halt the search
        if (entry.getKey() instanceof PathMarker marker) {

          // Skip over states we've already seen
          if (seenStates.add(entryTo)) {
            var newPath = new EmptyPath(next.pathToState(), marker);
            toVisit.push(new ToVisit(entryTo, newPath));
          }
        } else {
          output.put(next.state(), next.pathToState());
          break;
        }
      }
    }

    return output;
  }
}
