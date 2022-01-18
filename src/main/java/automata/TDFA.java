package automata;

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Set;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import static java.util.AbstractMap.SimpleImmutableEntry;

public class TDFA implements DotGraph<Integer, SimpleImmutableEntry<CodeUnitTransition, List<TagCommand>>> {

  public record TaggedTransition(
    List<TagCommand> commands,
    int targetState
  ) { }

  /**
   * Full set of states inside the tagged DFA.
   *
   * Each states maps sets of code units to a tagged transition. Every state in
   * the DFA should be in this map, even if it does not have any outgoing
   * transitions.
   */
  public final Map<Integer, Map<CodeUnitTransition, TaggedTransition>> states;

  /**
   * Accepting states in the DFA.
   *
   * Each accepting states has one final set of tag commands.
   */
  public final Map<Integer, List<TagCommand>> finalStates;

  /**
   * Index of the initial state inside {@code states}.
   */
  public final int initialState;

  /**
   * Count of groups in the DFA.
   *
   * TODO: clarify connection to `MatchResult.groupCount` (same or not?)
   */
  public final int groupCount;

  /**
   * Set of all of the group markers which are tracked in the TDFA.
   *
   * This does not include markers in {@code fixedTags}.
   */
  public final Set<GroupMarker> trackedGroupMarkers;

  /**
   * Group markers whose position is fixed relative to other group markers.
   */
  public final Map<GroupMarker, RelativeGroupLocation> fixedTags;

  public TDFA(
    Map<Integer, Map<CodeUnitTransition, TaggedTransition>> states,
    Map<Integer, List<TagCommand>> finalStates,
    int initialState,
    int groupCount,
    Set<GroupMarker> trackedGroupMarkers,
    Map<GroupMarker, RelativeGroupLocation> fixedTags
  ) {
    this.states = states;
    this.finalStates = finalStates;
    this.initialState = initialState;
    this.groupCount = groupCount;
    this.trackedGroupMarkers = trackedGroupMarkers;
    this.fixedTags = fixedTags;
  }

  /**
   * Run against an input and extra capture groups.
   *
   * @param input input test string
   * @param printDebugInfo print to STDERR a trace of what is happening
   * @return match results
   */
  public ArrayMatchResult captureSimulate(
    CharSequence input,
    boolean printDebugInfo
  ) {
    int currentState = initialState;
    int position = 0;
    final int length = input.length();
    final var registers = new HashMap<Register, Integer>();

    if (printDebugInfo) {
      System.err.println("[TDFA] starting run on: " + input);
    }

    while (position < length) {
      final char codeUnit = input.charAt(position);

      // Find the matching code unit transition
      final var matchingTransition = states
        .get(currentState)
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey().codeUnitSet().contains(codeUnit))
        .findFirst();
      if (!matchingTransition.isPresent()) {
        if (printDebugInfo) {
          System.err.println("[TDFA] ending run at " + currentState + "; no transition for " + codeUnit);
        }
        return null;
      }
      final var tagged = matchingTransition.get().getValue();

      // Apply the commands associated with the tarnsition
      for (var command : tagged.commands()) {
        command.interpret(registers, position, printDebugInfo);
      }

      // Update the DFA state and position
      currentState = tagged.targetState();
      if (printDebugInfo) {
        System.err.println("[TDFA] entering " + currentState);
      }
      position++;
    }

    // Check whether we are in an accepting state
    final var commands = finalStates.get(currentState);
    if (commands == null) {
      // TODO: log command
      return null;
    }

    // Apply the final accepting commands
    for (var command : commands) {
      command.interpret(registers, position, printDebugInfo);
    }

    // Construct the match
    final int[] offsets = new int[groupCount * 2];
    for (var trackedGroup : trackedGroupMarkers) {
      offsets[trackedGroup.arrayOffset()] = registers.getOrDefault(trackedGroup, -1);
    }
    for (var fixedTag : fixedTags.entrySet()) {
      final var location = fixedTag.getValue();
      int value = offsets[location.relativeTo().arrayOffset()];
      if (value != -1) {
        value -= location.distance();
      } else if (location.unavoidable()) {
        throw new IllegalStateException("Location " + location + " should be unavoidable");
      }
      offsets[fixedTag.getKey().arrayOffset()] = value;
    }

    return new ArrayMatchResult(input, offsets);
  }

  /**
   * Run against an input check if it matches.
   *
   * @param input input test string
   * @param printDebugInfo print to STDERR a trace of what is happening
   * @return match results
   */
  public boolean checkSimulate(
    CharSequence input,
    boolean printDebugInfo
  ) {
    int currentState = initialState;
    int position = 0;
    final int length = input.length();

    if (printDebugInfo) {
      System.err.println("[TDFA] starting run on: " + input);
    }

    while (position < length) {
      final char codeUnit = input.charAt(position);

      // Find the matching code unit transition
      final var matchingTransition = states
        .get(currentState)
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey().codeUnitSet().contains(codeUnit))
        .findFirst();
      if (!matchingTransition.isPresent()) {
        if (printDebugInfo) {
          System.err.println("[TDFA] ending run at " + currentState + "; no transition for " + codeUnit);
        }
        return false;
      }
      final var tagged = matchingTransition.get().getValue();

      // Update the DFA state and position
      currentState = tagged.targetState();
      if (printDebugInfo) {
        System.err.println("[TDFA] entering " + currentState);
      }
      position++;
    }

    // Check whether we are in an accepting state
    return finalStates.containsKey(currentState);
  }

  /**
   * Full set of temporary registers used in commands.
   *
   * @return set of every temporary register
   */
  public Set<Register.Temporary> registers() {
    final Stream<TagCommand> innerCommands = states
      .values()
      .stream()
      .flatMap(codeUnitTransitions -> codeUnitTransitions.values().stream())
      .flatMap(taggedTransition -> taggedTransition.commands().stream());

    final Stream<TagCommand> finalCommands = finalStates
      .values()
      .stream()
      .flatMap(List::stream);

    return Stream
      .concat(innerCommands, finalCommands)
      .flatMap((TagCommand command) -> {
        final var builder = Stream.<Register.Temporary>builder();
        final Consumer<Register> addVar = (Register r) -> {
          if (r instanceof Register.Temporary t) {
            builder.accept(t);
          }
        };
        command.usedVariable().ifPresent(addVar);
        command.definedVariable().ifPresent(addVar);
        return builder.build();
      })
      .collect(Collectors.toSet());
  }

  /**
   * Row in a TDFA state construction.
   *
   * @param nfaState TNFA state (whose outgoing transition is {@code transition})
   * @param registers which registers store tags (this always has all markers as keys)
   * @param lookaheadOperations commands to apply before advancing the position
   */
  private record RowConfiguration(
    int nfaState,
    SortedMap<GroupMarker, Register> registersPerTag,
    Set<GroupMarker> lookaheadOperations
  ) { }

  /**
   * State in the TDFA.
   *
   * @param dfaStateId unique ID to identify the state
   * @param rows rowconfigurations in the state
   */
  private record DfaState(
    int dfaStateId,
    List<RowConfiguration> rows
  ) {

    public List<Integer> nfaStates() {
      return rows
        .stream()
        .mapToInt(RowConfiguration::nfaState)
        .boxed()
        .collect(Collectors.toUnmodifiableList());
    }

    public boolean containsNfaState(int nfaState) {
      return rows.stream().anyMatch(r -> r.nfaState() == nfaState);
    }

    /**
     * Check if this is the same state as another, up to register isomorphism.
     *
     * To be the same, the state must have the same TNFA states in the same
     * priority (order), the same lookahead tags, and a bijection between
     * registers must exist. The bijection is then used to map transitions to
     * the "that" state into transitions to "this" state.
     *
     * @return map of registers from other state to this
     */
    public Map<Register, Register> isomorphicTo(DfaState that) {

      final var thisRows = rows.iterator();
      final var thatRows = that.rows.iterator();

      // Mappings of registers
      final var thisToThat = new HashMap<Register, Register>();
      final var thatToThis = new HashMap<Register, Register>();

      while (thisRows.hasNext() && thatRows.hasNext()) {
        final var thisRow = thisRows.next();
        final var thatRow = thatRows.next();

        // Ordered NFA state must match
        if (thisRow.nfaState() != thatRow.nfaState()) {
          return null;
        }

        // Lookahead tags must match
        if (!thisRow.lookaheadOperations().equals(thatRow.lookaheadOperations())) {
          return null;
        }

        // Registers must match up to isomorphism
        final var thisRegisters = thisRow.registersPerTag.values().iterator();
        final var thatRegisters = thatRow.registersPerTag.values().iterator();

        while (thisRegisters.hasNext() && thatRegisters.hasNext()) {
          final Register thisRegister = thisRegisters.next();
          final Register thatRegister = thatRegisters.next();

          final Register thatPrevious = thisToThat.put(thisRegister, thatRegister);
          if (!(thatPrevious == null || thatPrevious.equals(thatRegister))) {
            return null;
          }

          final Register thisPrevious = thatToThis.put(thatRegister, thisRegister);
          if (!(thisPrevious == null || thisPrevious.equals(thisRegister))) {
            return null;
          }
        }

        if (thisRegisters.hasNext() != thatRegisters.hasNext()) {
          return null;
        }
      }

      return (thisRows.hasNext() != thatRows.hasNext()) ? null : thatToThis;
    }
  }

  public static TDFA fromTNFA(M1Dfa m1) {

    final Set<GroupMarker> groupMarkers = m1.groupMarkers();
    final Set<GroupMarker> trackedGroupMarkers = new HashSet<>(groupMarkers);
    trackedGroupMarkers.removeAll(m1.fixedTags.keySet());

    // Fresh TDFA states come from here
    final IntSupplier dfaStateIdSupplier = new IntSupplier() {
      int nextDfaState = 0;

      @Override
      public int getAsInt() {
        return nextDfaState++;
      }
    };

    // Fresh registers come from here
    final Supplier<Register> registerSupplier = new Supplier<Register>() {
      int nextRegister = 0;

      @Override
      public Register get() {
        return new Register.Temporary(nextRegister++);
      }
    };

    /* Every time we visit a new DFA state, we use its ordered set of NFA
     * states to lookup the list of possibly matching DFA states. Next, we
     * comb through these looking for a state that is isomorphic. If we find
     * one, we merge with that state. If not, we add the state here (and push
     * it onto the stack of states to visit with a fresh ID).
     */
    final var seenStates = new HashMap<List<Integer>, List<DfaState>>();
    final var toVisit = new Stack<DfaState>();

    // These only get populated after a state is popped off of `toVisit`
    final var states = new HashMap<Integer, Map<CodeUnitTransition, TaggedTransition>>();
    final var finalStates = new HashMap<Integer, List<TagCommand>>();
    final int initialState = dfaStateIdSupplier.getAsInt();

    // Mapping from a group marker to its _final_ register
    final Map<GroupMarker, Register> groupRegisters = trackedGroupMarkers
      .stream()
      .collect(Collectors.toMap(
        Function.identity(),
        Function.identity()
      ));

    // Set up the initial state to visit
    {
      final SortedMap<GroupMarker, Register> initialRegisters = trackedGroupMarkers
        .stream()
        .collect(Collectors.toMap(
          Function.identity(),
          g -> registerSupplier.get(),
          (v1, v2) -> { throw new IllegalStateException("cannot merge values"); },
          TreeMap::new
        ));

      final var initialDfaState = new DfaState(
        initialState,
        m1.epsilonReachable(m1.initialState)
          .entrySet()
          .stream()
          .map((Map.Entry<Integer, PathMarkers> entry) ->
            new RowConfiguration(
              entry.getKey(),
              initialRegisters,
              StreamSupport
                .stream(entry.getValue().spliterator(), false)
                .flatMap((PathMarker marker) -> {
                  if (marker instanceof GroupMarker groupMarker && trackedGroupMarkers.contains(groupMarker)) {
                    return Stream.of(groupMarker);
                  } else {
                    return Stream.empty();
                  }
                })
                .collect(Collectors.toSet())
            )
          )
          .collect(Collectors.toList())
      );

      seenStates
        .computeIfAbsent(initialDfaState.nfaStates(), k -> new LinkedList<>())
        .add(initialDfaState);
      toVisit.push(initialDfaState);
    }

    while (!toVisit.isEmpty()) {
      final DfaState nextState = toVisit.pop();

      // For each lookahead tag in this state, generate a fresh register
      // Only contains keys which are lookahead tags
      final Map<GroupMarker, Register> lookaheadRegisters = nextState
        .rows()
        .stream()
        .flatMap(row -> row.lookaheadOperations.stream())
        .distinct()
        .collect(Collectors.toMap(Function.identity(), m -> registerSupplier.get()));

      final var transitions = new HashMap<CodeUnitTransition, Map.Entry<Set<Register>, DfaState>>();
      for (final RowConfiguration row : nextState.rows()) {

        // These are the registers that target row configurations will use
        // Contains keys for every tag
        final SortedMap<GroupMarker, Register> updatedRegisters = row
          .registersPerTag()
          .entrySet()
          .stream()
          .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> row.lookaheadOperations.contains(e.getKey()) ? lookaheadRegisters.get(e.getKey()) : e.getValue(),
            (v1, v2) -> { throw new IllegalStateException("cannot merge values"); },
            TreeMap::new
          ));

        // Based on the initial row's lookaheads, determine the tags
        final Set<Register> transitionOperations = row
          .lookaheadOperations
          .stream()
          .map(updatedRegisters::get)
          .collect(Collectors.toSet());

        // Check if the NFA state is accepting
        if (row.nfaState == m1.finalState) {
          final List<TagCommand> commands = Stream
            .concat(
              transitionOperations
                .stream()
                .<TagCommand>map(t -> new TagCommand.CurrentPosition(t)),
              groupRegisters
                .entrySet()
                .stream()
                .<TagCommand>map(m -> {
                  final Register copyFrom = updatedRegisters.get(m.getKey());
                  final Register assignTo = m.getValue();

                  // This is needed to ensure the invariant that we never copy
                  // a variable in the same set of commands as assigning to it
                  if (transitionOperations.contains(copyFrom)) {
                    return new TagCommand.CurrentPosition(assignTo);
                  } else {
                    return new TagCommand.Copy(assignTo, copyFrom);
                  }
                })
            )
            .collect(Collectors.toCollection(LinkedList::new));

          finalStates.put(nextState.dfaStateId(), commands);
        }

        // Explore all transitions out of the NFA state in this row.
        for (final var codeUnitTransition : m1.states.get(row.nfaState).entrySet()) {

          // Immediate code unit transition and the epsilon closure starting after it
          final var codeUnit = (CodeUnitTransition) codeUnitTransition.getKey();
          final int codeUnitTargetNfaState = codeUnitTransition.getValue();
          final var epsilonClosure = m1
            .epsilonReachable(codeUnitTargetNfaState)
            .entrySet();

          if (epsilonClosure.isEmpty()) {
            continue;
          }

          // Find or create the WIP DFA state for this code unit
          final var wipTransition = transitions.computeIfAbsent(
            codeUnit,
            k -> new SimpleImmutableEntry<>(
              new HashSet<Register>(),
              new DfaState(dfaStateIdSupplier.getAsInt(), new LinkedList<>())
            )
          );
          final DfaState wipDfaState = wipTransition.getValue();
          final Set<Register> wipSetToCurrentPos = wipTransition.getKey();

          for (final Map.Entry<Integer, PathMarkers> epsilonReachable : epsilonClosure) {

            final int nfaState = epsilonReachable.getKey();
            final PathMarkers epsilonPath = epsilonReachable.getValue();

            // Skip over NFA states which have already been reached via this code unit
            if (wipDfaState.containsNfaState(nfaState)) {
              continue;
            }

            // Construct the target row configuration
            final var targetRow = new RowConfiguration(
              nfaState,
              updatedRegisters,
              StreamSupport
                .stream(epsilonPath.spliterator(), false)
                .flatMap((PathMarker marker) -> {
                  if (marker instanceof GroupMarker groupMarker && trackedGroupMarkers.contains(groupMarker)) {
                    return Stream.of(groupMarker);
                  } else {
                    return Stream.empty();
                  }
                })
                .collect(Collectors.toSet())
            );

            wipDfaState.rows().add(targetRow);
            wipSetToCurrentPos.addAll(transitionOperations);
          }
        }
      }

      // Build up the set of transitions from this DFA state
      final var transitionsFromThisDfaState = new HashMap<CodeUnitTransition, TaggedTransition>();
      states.put(nextState.dfaStateId, transitionsFromThisDfaState);
      for (final var transition : transitions.entrySet()) {
        final CodeUnitTransition codeUnit = transition.getKey();
        final Set<Register> assignCurrentPos = transition.getValue().getKey();
        final DfaState targetDfaState = transition.getValue().getValue();

        // Look for a state into which the target state can be merged
        final var duplicateOptStateMapping = seenStates
          .getOrDefault(targetDfaState.nfaStates(), Collections.emptyList())
          .stream()
          .flatMap((DfaState dfaState) -> {
            final var registerMapping = dfaState.isomorphicTo(targetDfaState);
            if (registerMapping == null) {
              return Stream.empty();
            } else {
              return Stream.of(new SimpleImmutableEntry<>(dfaState, registerMapping));
            }
          })
          .findFirst();

        if (duplicateOptStateMapping.isPresent()) {
          final var duplicateStateMapping = duplicateOptStateMapping.get();
          final var duplicateDfaState = duplicateStateMapping.getKey();
          final var registerMapping = duplicateStateMapping.getValue();

          // Commands will include renaming of registers + position captures
          final List<TagCommand> commands = Stream
            .concat(
              assignCurrentPos
                .stream()
                .<TagCommand>map(t -> new TagCommand.CurrentPosition(t)),
              registerMapping
                .entrySet()
                .stream()
                .filter(m -> !m.getKey().equals(m.getValue()))
                .<TagCommand>map(m -> {
                  final Register copyFrom = m.getKey();
                  final Register assignTo = m.getValue();

                  // This is needed to ensure the invariant that we never copy
                  // a variable in the same set of commands as assigning to it
                  if (assignCurrentPos.contains(copyFrom)) {
                    return new TagCommand.CurrentPosition(assignTo);
                  } else {
                    return new TagCommand.Copy(assignTo, copyFrom);
                  }
                })
            )
            .collect(Collectors.toCollection(LinkedList::new));

          // Adjust the transition to point to the duplicated state
          transitionsFromThisDfaState.put(
            codeUnit,
            new TaggedTransition(commands, duplicateDfaState.dfaStateId)
          );
        } else {

          // Commands for position captures
          final List<TagCommand> commands = assignCurrentPos
            .stream()
            .<TagCommand>map(t -> new TagCommand.CurrentPosition(t))
            .collect(Collectors.toCollection(LinkedList::new));

          // Add the transition
          transitionsFromThisDfaState.put(
            codeUnit,
            new TaggedTransition(commands, targetDfaState.dfaStateId)
          );

          // Record the new DFA state and push it onto the stack to visit
          seenStates
            .computeIfAbsent(targetDfaState.nfaStates(), k -> new LinkedList<DfaState>())
            .add(targetDfaState);
          toVisit.push(targetDfaState);
        }
      }
    }

    return new TDFA(states, finalStates, initialState, groupMarkers.size() / 2, trackedGroupMarkers, m1.fixedTags);
  }

  /**
   * Optimize and minimize tag commands.
   *
   * This operates by performing liveness analysis and then using that
   * information to:
   *
   *   - eliminate dead stores
   *   - build an interference graph and coalesce registers whenever possible
   *
   * This mutates the graph in-place (specifically updating the command lists)
   *
   * @return if anything was simplified
   */
  public boolean simplifyTagCommands() {
    boolean somethingWasSimplified = false;

    /* We run liveness analysis on a CFG of basic blocks. But what do those
     * blocks correspond to in the DFA?
     *
     *   - (non-empty) ones correspond to tagged transitions
     *   - (empty) ones are associated with DFA states
     *
     * The only purpose of the empty state blocks is to serve as join points
     * (eg. to multiple incoming transition blocks and multiple outgoing
     * transition blocks)
     */

    final var blocks = new LinkedList<BasicBlock<Register>>();
    final var stateBlocks = new HashMap<Integer, BasicBlock<Register>>();
    final var transitionBlocks = new HashMap<BasicBlock<Register>, List<TagCommand>>();

    // Built up from all copies
    final var coalescingCandidates = new HashMap<Register, Register>();

    // Initialize the "state" basic blocks
    for (var dfaStateId : states.keySet()) {
      final var block = new BasicBlock<Register>();
      blocks.add(block);
      stateBlocks.put(dfaStateId, block);
    }

    // Add in transition blocks
    for (var entry : states.entrySet()) {
      final var fromBlock = stateBlocks.get(entry.getKey());
      for (var transition : entry.getValue().values()) {
        final var toBlock = stateBlocks.get(transition.targetState());
        final var commands = transition.commands();

        // Compute which variables are used and defined
        final var usedVariables = new HashSet<Register>();
        final var definedVariables = new HashSet<Register>();
        for (TagCommand command : commands) {
          command.usedVariable().ifPresent(usedVariables::add);
          command.definedVariable().ifPresent(definedVariables::add);
        }

        final var block = new BasicBlock<Register>(usedVariables, definedVariables);
        blocks.add(block);
        transitionBlocks.put(block, commands);

        // Connect the blocks
        BasicBlock.link(fromBlock, block);
        BasicBlock.link(block, toBlock);
      }
    }

    // Add in final transition blocks (TODO: reduce code duplication)
    for (var entry : finalStates.entrySet()) {
      final var fromBlock = stateBlocks.get(entry.getKey());
      final var commands = entry.getValue();

      // Compute which variables are used and defined
      final var usedVariables = new HashSet<Register>();
      final var definedVariables = new HashSet<Register>();
      for (TagCommand command : commands) {
        command.usedVariable().ifPresent(usedVariables::add);
        command.definedVariable().ifPresent(definedVariables::add);
      }

      final var block = new BasicBlock<Register>(usedVariables, definedVariables);
      blocks.add(block);
      transitionBlocks.put(block, commands);

      // Connect the blocks
      BasicBlock.link(fromBlock, block);

      // Since this is a final block, mark the group markers as "live out"
      block.liveOut.addAll(trackedGroupMarkers);
    }

    // Run liveness analysis
    BasicBlock.livenessAnalysis(blocks);
    final var interferenceGraph = new InterferenceGraph<Register>();

    // First pass: dead store elimination and building up interference graph
    for (var entry : transitionBlocks.entrySet()) {
      final var block = entry.getKey();
      final var commands = entry.getValue();

      final var commandIterator = commands.listIterator();
      while (commandIterator.hasNext()) {
        final var command = commandIterator.next();
        final var definedVar = command.definedVariable();

        if (definedVar.isPresent() && !block.liveOut.contains(definedVar.get())) {
          // Dead store elimination: if a variable is not live out, eliminate any writes to it
          commandIterator.remove();
          somethingWasSimplified = true;
        } else if (command instanceof TagCommand.Copy copy) {
          // Update coalescing candidates
          // Note: assign to is the RHS so it will get kept in coalescing ops
          coalescingCandidates.put(copy.copyFrom(), copy.assignTo());
        }
      }

      // Update interference graph
      // TODO: do we need live in _and_ live out?
      interferenceGraph.addInterference(block.liveIn);
      interferenceGraph.addInterference(block.liveOut);
    }

    // Try to coalesce variables
    for (final var entry : coalescingCandidates.entrySet()) {
      if (interferenceGraph.coalesce(entry.getKey(), entry.getValue())) {
        somethingWasSimplified = true;
      }
    }

    // Second pass: apply coalescing and remove pointless copies
    for (var commands : transitionBlocks.values()) {

      final var commandIterator = commands.listIterator();
      while (commandIterator.hasNext()) {
        var command = commandIterator.next();

        if (command instanceof TagCommand.CurrentPosition position) {
          final var assignTo = interferenceGraph.canonical(position.assignTo());
          command = new TagCommand.CurrentPosition(assignTo);
        } else if (command instanceof TagCommand.Copy copy) {
          final var assignTo = interferenceGraph.canonical(copy.assignTo());
          final var copyFrom = interferenceGraph.canonical(copy.copyFrom());
          if (assignTo.equals(copyFrom)) {
            commandIterator.remove();
            continue;
          }
          command = new TagCommand.Copy(assignTo, copyFrom);
        }

        commandIterator.set(command);
      }
    }

    return somethingWasSimplified;
  }

  /**
   * Take a deep copy of the TDFA, merging states and modifying commands.
   *
   * @param mergeStates merge keys into their values (empty map to not merge)
   * @param cloneCommands modify command lists
   * @return cloned TDFA
   */
  public TDFA deepCopy(
    Map<Integer, Integer> mergeStates,
    UnaryOperator<List<TagCommand>> cloneCommands,
    IntUnaryOperator updateGroupCount,
    UnaryOperator<Set<GroupMarker>> cloneTrackedGroupMarkers,
    UnaryOperator<Map<GroupMarker, RelativeGroupLocation>> cloneFixedTags
  ) {

    // Updated states
    final var newStates = new HashMap<Integer, Map<CodeUnitTransition, TaggedTransition>>();
    for (final var entry : states.entrySet()) {
      final var fromState = entry.getKey();
      if (mergeStates.containsKey(fromState)) {
        continue;
      }

      final var newTransitions = new HashMap<CodeUnitTransition, TaggedTransition>();
      for (final var transition : entry.getValue().entrySet()) {
        final var newCommands = cloneCommands.apply(transition.getValue().commands());
        var targetState = transition.getValue().targetState();
        targetState = mergeStates.getOrDefault(targetState, targetState);
        final var newTransition = new TaggedTransition(newCommands, targetState);
        newTransitions.put(transition.getKey(), newTransition);
      }
      newStates.put(fromState, newTransitions);
    }

    // Updated final states
    final var newFinalStates = new HashMap<Integer, List<TagCommand>>();
    for (final var entry : finalStates.entrySet()) {
      final var fromState = entry.getKey();
      if (mergeStates.containsKey(fromState)) {
        continue;
      }

      newFinalStates.put(fromState, cloneCommands.apply(entry.getValue()));
    }

    // Updated initial state
    final int newInitialState = mergeStates.getOrDefault(initialState, initialState);

    return new TDFA(
      newStates,
      newFinalStates,
      newInitialState,
      updateGroupCount.applyAsInt(groupCount),
      cloneTrackedGroupMarkers.apply(trackedGroupMarkers),
      cloneFixedTags.apply(fixedTags)
    );
  }

  /**
   * Minimize the TDFA while also removing all commands.
   *
   * The output TDFA should accept and reject the same inputs, but it won't
   * carry any tags or groups.
   *
   * @return minimized TDFA without any groups
   */
  public TDFA minimizeWithoutTagCommands() {

    // Mapping from states that should be collapsed to the canonical state
    final Map<Integer, Integer> canonicalStates = new HashMap<>();
    for (final SortedSet<Integer> partition : minimizedDfaPartition(true)) {
      final var canonical = partition.first();
      for (final var other : partition) {
        canonicalStates.put(other, canonical);
      }
      canonicalStates.remove(canonical);
    }

    return deepCopy(
      canonicalStates,
      k -> Collections.emptyList(),
      i -> 0,
      m -> Collections.emptySet(),
      t -> Collections.emptyMap()
    );
  }

  /**
   * Minimize the TDFA while respecting commands.
   *
   * The output TDFA should have exactly the same end behaviour as the initial
   * TDFA, but some indistinguishable states may be merged.
   *
   * @return equivalent minimized TDFA
   */
  public TDFA minimized() {

    // Mapping from states that should be collapsed to the canonical state
    final Map<Integer, Integer> canonicalStates = new HashMap<>();
    for (final SortedSet<Integer> partition : minimizedDfaPartition(false)) {
      final var canonical = partition.first();
      for (final var other : partition) {
        canonicalStates.put(other, canonical);
      }
      canonicalStates.remove(canonical);
    }

    return deepCopy(
      canonicalStates,
      LinkedList::new,
      IntUnaryOperator.identity(),
      UnaryOperator.identity(),
      UnaryOperator.identity()
    );
  }

  /**
   * Perform minimization and return a partition for a minimized TDFA.
   *
   * This uses a variant of Hopcroft's algorithm, except that equivalence of
   * transitions includes the tags traversed (including final ones).
   *
   * @param ignoreCommands only enable this if you intend to discard tags
   * @return a partition of the TDFA states
   */
  public Set<SortedSet<Integer>> minimizedDfaPartition(boolean ignoreCommands) {

    record ReversedTaggedTransition(
      CodeUnitTransition codeUnit,
      Set<TagCommand> commands
    ) { }

    // Keys are targets states and values are mappings from transitions to source states
    final Map<Integer, Map<ReversedTaggedTransition, Set<Integer>>> reversedTransitions = new HashMap<>();
    for (final var entry : states.entrySet()) {
      final int fromState = entry.getKey();
      for (final var transitions : entry.getValue().entrySet()) {
        final CodeUnitTransition codeUnit = transitions.getKey();
        final Set<TagCommand> commands = ignoreCommands
          ? Collections.emptySet()
          : new HashSet<>(transitions.getValue().commands());
        final int toState = transitions.getValue().targetState();
        reversedTransitions
          .computeIfAbsent(toState, k -> new HashMap<>())
          .computeIfAbsent(new ReversedTaggedTransition(codeUnit, commands), k -> new HashSet<>())
          .add(fromState);
      }
    }

    // Set up initial partition
    final var partition = new HashSet<SortedSet<Integer>>();
    final var sortedSetCollector = Collectors.<Integer, SortedSet<Integer>>toCollection(TreeSet::new);
    if (ignoreCommands) {
      partition.add(new TreeSet(finalStates.keySet()));
    } else {
      partition.addAll(
        finalStates
          .entrySet()
          .stream()
          .collect(
            Collectors.groupingBy(
              Map.Entry::getValue,
              Collectors.mapping(Map.Entry::getKey, sortedSetCollector)
            )
          )
          .values()
      );
    }
    partition.add(
      states
        .keySet()
        .stream()
        .filter(k -> !finalStates.containsKey(k))
        .collect(sortedSetCollector)
    );
    partition.removeIf(Set::isEmpty);

    // Mapping from states to power states in the partition
    final var stateToPartition = new HashMap<Integer, Set<Integer>>();
    for (final var powerState : partition) {
      for (final var state : powerState) {
        stateToPartition.put(state, powerState);
      }
    }

    // Worklist
    final var toVisit = new HashSet<Set<Integer>>();
    toVisit.addAll(partition);

    while (!toVisit.isEmpty()) {
      final var powerState = toVisit.iterator().next();
      toVisit.remove(powerState);

      // Find all pre-images from this aggregated state, keyed by the code unit and tags
      final var reversedFromThisState = new HashMap<ReversedTaggedTransition, Set<Integer>>();
      for (final int state : powerState) {
        final var toTransitions = reversedTransitions.get(state);
        if (toTransitions != null) {
          for (final var entry : toTransitions.entrySet()) {
            reversedFromThisState
              .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
              .addAll(entry.getValue());
          }
        }
      }

      // Figure out which pre-images require some refinement of partition sets
      for (final Set<Integer> targetSubset : reversedFromThisState.values()) {
        for (final int containedState : targetSubset) {
          final var oldPowerSet = stateToPartition.get(containedState);

          // Partition `oldPartition`
          final var inTargetSubset = new TreeSet<Integer>();
          final var notInTargetSubset = new TreeSet<Integer>();
          for (final int state : oldPowerSet) {
            if (targetSubset.contains(state)) {
              inTargetSubset.add(state);
            } else {
              notInTargetSubset.add(state);
            }
          }

          // Skip to the next powerset if not refinement of `oldPowerSet` needed
          if (notInTargetSubset.isEmpty()) {
            continue;
          }

          // Update partition
          partition.remove(oldPowerSet);
          partition.add(inTargetSubset);
          partition.add(notInTargetSubset);

          // Update stateToPartition
          for (final int state : inTargetSubset) {
            stateToPartition.put(state, inTargetSubset);
          }
          for (final int state : notInTargetSubset) {
            stateToPartition.put(state, notInTargetSubset);
          }

          // Update worklist
          if (toVisit.remove(oldPowerSet)) {
            toVisit.add(inTargetSubset);
            toVisit.add(notInTargetSubset);
          } else if (inTargetSubset.size() < notInTargetSubset.size()) {
            toVisit.add(inTargetSubset);
          } else {
            toVisit.add(notInTargetSubset);
          }
        }
      }
    }

    return partition;
  }

  @Override
  public Stream<Vertex<Integer>> vertices() {
    return states
      .keySet()
      .stream()
      .map(id -> new Vertex<>(id, finalStates.containsKey(id)));
  }

  @Override
  public Stream<Edge<Integer, SimpleImmutableEntry<CodeUnitTransition, List<TagCommand>>>> edges() {

    final Integer noState = null;
    final CodeUnitTransition noCodeUnit = null;
    final List<TagCommand> noCommands = Collections.emptyList();

    final var initialEdge = new Edge<>(
      noState,
      initialState,
      new SimpleImmutableEntry<>(noCodeUnit, noCommands)
    );

    final var finalEdges = finalStates
      .entrySet()
      .stream()
      .map(entry ->
        new Edge<>(
          entry.getKey(),
          noState,
          new SimpleImmutableEntry<>(noCodeUnit, entry.getValue())
        )
      );

    final var innerEdges = states
      .entrySet()
      .stream()
      .flatMap(stateEntry ->
        stateEntry
          .getValue()
          .entrySet()
          .stream()
          .map(transitionEntry ->
            new Edge<>(
              stateEntry.getKey(),
              transitionEntry.getValue().targetState(),
              new SimpleImmutableEntry<>(transitionEntry.getKey(), transitionEntry.getValue().commands())
            )
          )
      );

    return Stream
      .of(Stream.of(initialEdge), finalEdges, innerEdges)
      .flatMap(Function.identity());
  }

  @Override
  public String renderEdgeLabel(Edge<Integer, SimpleImmutableEntry<CodeUnitTransition, List<TagCommand>>> edge) {
    final var label = edge.label();
    final var codeUnit = label.getKey();
    final var commands = label.getValue();
    final var builder = new StringBuilder();

    if (codeUnit != null) {
      builder.append(codeUnit.dotLabel());
    }

    if (codeUnit != null || !commands.isEmpty()) {
      builder.append("&nbsp;/&nbsp;");
    }

    if (!commands.isEmpty()) {
      builder.append(commands.stream().map(TagCommand::dotLabel).collect(Collectors.joining("; ")));
    }

    return builder.toString();
  }
}
