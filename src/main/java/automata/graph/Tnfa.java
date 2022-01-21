package automata.graph;

import static java.util.AbstractMap.SimpleImmutableEntry;

import automata.parser.Boundary;
import automata.parser.RegexParser;
import automata.util.IntRangeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.UnaryOperator;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Tagged non-deterministic finite state automaton.
 *
 * The states are represented using unique integer values and the transitions
 * are code unit ranges (these get preserved through to the TDFA), alternation
 * markers for prioritized non-deterministic choice, and group markers for
 * signalling the start or end of a capture group.
 *
 * @author Alec Theriault
 */
final public class Tnfa implements DotGraph<Integer, TnfaTransition> {

  /**
   * State transitions, indexed along all of the starting nodes.
   *
   * This list is not modifiable and should support fast random access. The
   * inner maps are also not modifiable and they will fit one of the following
   * profiles:
   *
   *   - all keys are {@code CodeUnitTransition}
   *
   *   - keys are the two {@code AlternationMarker}, and the minus shows up
   *     before the plus in the entry set
   *
   *   - key is a single {@code GroupMarker}
   *
   * Not all states may be reachable (the finite-state machine graph is not
   * necessarily connected). As an example, the graph corresponding to the
   * regular expression {@code a[b&&c]d} will have no connection from {@code a}
   * to {@code d} (since the code point set associated with {@code [b&&c]} is
   * empty).
   */
  public final List<Map<TnfaTransition, Integer>> states;

  /**
   * Index of the initial state inside {@code states}.
   */
  public final int initialState;

  /**
   * Index of the final state inside {@code states}.
   */
  public final int finalState;

  /**
   * Group markers whose position is fixed relative to other group markers.
   */
  public final Map<GroupMarker, RelativeGroupLocation> fixedTags;

  private Tnfa(
    List<Map<TnfaTransition, Integer>> states,
    int initialState,
    int finalState,
    Map<GroupMarker, RelativeGroupLocation> fixedTags
  ) {
    this.states = states;
    this.initialState = initialState;
    this.finalState = finalState;
    this.fixedTags = fixedTags;
  }

  /**
   * Parse an NFA from a regular expression pattern.
   *
   * If both the wrapping group and wildcard prefix options are enabled, the
   * wrapping group starts _after_ the wildcard prefix.
   *
   * @param pattern regular expression to parse
   * @param wrappingGroup add an implicit capture group around the expression
   * @param wildcardPrefix accept any prefix before the expression
   */
  public static Tnfa parse(
    String pattern,
    boolean wrappingGroup,
    boolean wildcardPrefix
  ) throws PatternSyntaxException {
    final var builder = new Builder();
    final var visited = RegexParser.parse(
      builder,
      pattern,
      wrappingGroup,
      wildcardPrefix
    );
    return builder.constructNfa(visited);
  }

  public static class Builder extends RegexNfaBuilder<Integer> {
    // Used to buffer up transitions
    record BufferedTransition(int from, TnfaTransition transition, int to) { }

    private boolean used = false;
    private int lastState = 0;
    private final LinkedList<BufferedTransition> transitions = new LinkedList<>();
    private final Map<GroupMarker, RelativeGroupLocation> fixedTags = new HashMap<>();

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
    public void addGroupState(Integer from, GroupMarker marker, Optional<RelativeGroupLocation> fixedTag, Integer to) {
      if (fixedTag.isPresent()) {
        fixedTags.put(marker, fixedTag.get());
      }
      transitions.addLast(new BufferedTransition(from, marker, to));
    }

    @Override
    public void addBoundaryState(Integer from, Boundary boundary, Integer to) {
      throw new UnsupportedOperationException("boundaries are not yet supported in TNFAs");
    }

    /**
     * Finalize the construction of the TNFA.
     *
     * @param visitorOutput output from visiting the regular expression
     * @param initialState which state is the starting one
     * @param finalState which state is the accepting one
     * @return valid TNFA
     */
    public Tnfa constructNfa(UnaryOperator<NfaState<Integer>> visitorOutput) {
      if (used) {
        throw new IllegalStateException("construct may only be called once on a TNFA builder");
      } else {
        used = true;
      }

      // "Run" the visitor output, populating `transitions` as a side-effect
      final int finalState = freshState();
      final int initialState = visitorOutput.apply(new NfaState<>(finalState, true, Optional.empty())).state();

      // Initialize the states array to the right length
      @SuppressWarnings({"unchecked"})
      final var states = IntStream
        .range(0, lastState)
        .<Map<TnfaTransition, Integer>>mapToObj(i -> new LinkedHashMap<TnfaTransition, Integer>())
        .collect(Collectors.toCollection(ArrayList::new));

      // Extract out an identity mapping of all code unit sets from code unit transitions
      final Map<IntRangeSet, IntRangeSet> identityCodeTransitions = new HashMap<IntRangeSet, IntRangeSet>();
      for (BufferedTransition buffered : transitions) {
        if (buffered.transition instanceof CodeUnitTransition trans) {
           identityCodeTransitions.put(trans.codeUnitSet(), trans.codeUnitSet());
        } else {
           states
             .get(buffered.from)
             .put(buffered.transition, buffered.to);
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
            states
              .get(buffered.from)
              .put(new CodeUnitTransition(partitionSubset), buffered.to);
          }
        }
      }

      // Defensively prevent updates to the state maps
      states.replaceAll(Collections::unmodifiableMap);
      states.trimToSize();

      return new Tnfa(
        Collections.unmodifiableList(states),
        initialState,
        finalState,
        fixedTags
      );
    }
  }

  /**
   * Explore all states reachable only via path markers and ending at states
   * which have outgoing code unit transitions.
   *
   * If there are paths for reaching the same code unit transition, the path
   * which has a {@code AlternationMarker.PLUS} earlier on it is the one that
   * should be returned.
   *
   * The worst case complexity is {@code O(E)} (since we visit each transition
   * at most once), but it is usually much better. Practically speaking the
   * sub-graph obtained by filtering out code unit transitions tends to be much
   * smaller and often not connected.
   *
   * @param startingState state from which to initiate the directed search
   * @return ordered mapping from states reachable via path markers to those paths
   */
  public Map<Integer, PathMarkers> epsilonReachable(int startingState) {
    record ToVisit(int state, PathMarkers pathToState) { }

    final var seenStates = new HashSet<Integer>();
    final var toVisit = new Stack<ToVisit>();
    final var output = new LinkedHashMap<Integer, PathMarkers>();

    // Seed the DFS with the starting node
    toVisit.push(new ToVisit(startingState, PathMarkers.EMPTY));
    seenStates.add(startingState);

    // DFS loop
    while (!toVisit.isEmpty()) {
      final var next = toVisit.pop();

      // The terminal state is special - it is always included in the output
      if (next.state() == finalState) {
        output.put(next.state(), next.pathToState());
        continue;
      }

      for (var entry : states.get(next.state()).entrySet()) {
        final int entryTo = entry.getValue();

        // If the transition out of this state is not a path marker, halt the search
        if (entry.getKey() instanceof PathMarker marker) {

          // Skip over states we've already seen
          if (seenStates.add(entryTo)) {
            var newPath = next.pathToState().appended(marker);
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

  /**
   * Compute all the group markers in the NFA.
   *
   * @return all group markers (even unreachable ones)
   */
  public Set<GroupMarker> groupMarkers() {
    return states
      .stream()
      .flatMap(state -> state.keySet().stream())
      .flatMap((TnfaTransition transition) -> {
        if (transition instanceof GroupMarker groupMarker) {
          return Stream.of(groupMarker);
        } else {
          return Stream.empty();
        }
      })
      .collect(Collectors.toSet());
  }

  @Override
  public Stream<DotGraph.Vertex<Integer>> vertices() {
    return IntStream
      .range(0, states.size())
      .mapToObj((int id) -> new DotGraph.Vertex<Integer>(id, id == finalState));
  }

  @Override
  public Stream<DotGraph.Edge<Integer, TnfaTransition>> edges() {
    var transitionEdges = IntStream
      .range(0, states.size())
      .boxed()
      .flatMap((Integer from) -> {
        return states
          .get(from)
          .entrySet()
          .stream()
          .map(entry -> new DotGraph.Edge<>(from, entry.getValue(), entry.getKey()));
      });
    var initialEdge = Stream
      .of(new DotGraph.Edge<Integer, TnfaTransition>(null, initialState, null));
    return Stream.concat(initialEdge, transitionEdges);
  }

  @Override
  public String renderEdgeLabel(DotGraph.Edge<Integer, TnfaTransition> edge) {
    final TnfaTransition label = edge.label();
    return label == null ? "" : label.dotLabel();
  }

}
