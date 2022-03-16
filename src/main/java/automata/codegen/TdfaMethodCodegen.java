package automata.codegen;

import static java.util.AbstractMap.SimpleImmutableEntry;

import automata.graph.CodeUnitTransition;
import automata.graph.GroupMarker;
import automata.graph.GroupMarkers;
import automata.graph.MatchMode;
import automata.graph.Register;
import automata.graph.TagCommand;
import automata.graph.Tdfa;
import automata.util.IntRange;
import automata.util.IntRangeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Function;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Functionality for generating the body of a TDFA matching/checking function.
 *
 * <p>This uses the natural mapping of a tagged DFA into the control-flow graph
 * of the bytecode method: states are represented by blocks with transitions
 * encoded as jumps to other blocks. Tag commands in the DFA have a natural
 * mapping too: they turn into code that gets executed before jumping to the
 * target state.
 *
 * @author Alec Theriault
 */
class TdfaMethodCodegen extends BytecodeHelpers {

  /**
   * Tagged DFA for which code is generated.
   */
  private final Tdfa dfa;

  /**
   * If set, the generated method will include code that prints to "standard"
   * error output for: states entered, tag commands executed, final output.
   */
  private final boolean printDebugInfo;

  /**
   * Largest number of elements in a range for which the range is still deemded
   * to be "small" (so that it is checked using a jump table instead of range
   * checks).
   */
  private final int smallRangeThresholdSize = 16;

  /**
   * Offset for argument of type {@code CharSequence}, corresponding to the
   * input string.
   */
  private final int inputLocal;

  /**
   * Offset for argument of type {@code int}, corresponding to the
   * (ascending) offset in the input string.
   */
  private final int offsetLocal;

  /**
   * Offset for argument of type {@code int} corresponding to the maximum
   * offset in the input string.
   *
   * <p>If the entire input string is being matched, this can be set to the
   * length of the input string.
   */
  private final int maxOffsetLocal;

  /**
   * Offset for argument of type {@code int[]} corresponding to the group
   * variables being tracked.
   */
  private final int groupsLocal;

  /**
   * Offset for a local of type {@code char} used as a temporary variable in
   * some of the code-unit branching logic.
   */
  private final int charTempLocal;

  /**
   * Offsets for local variables associated with various temporary registers.
   */
  private final Map<Register.Temporary, Integer> temporaryRegisterLocals;

  /**
   * Labels associated with DFA states.
   *
   * <p>Each DFA state has a label and going to a new state is as simple as
   * jumping to that label. This map is unmodifiable.
   */
  private final Map<Integer, Label> stateLabels;

  /**
   * Label for the block which ends in a positive match being returned.
   */
  private final Label returnSuccess;

  /**
   * Label for the block which ends in a negative match being returned.
   */
  private final Label returnFailure;

  /**
   * Set of fixed classes still needing to be processed.
   *
   * <p>Since some classes get handled at the beginning vs. end of the match and
   * some classes could be anchored against either the beginning or end, it
   * helps to maintain a set of fixed classes not yet processed.
   */
  private final HashSet<GroupMarkers.FixedClass> fixedClasses;

  public TdfaMethodCodegen(
    MethodVisitor mv,
    Tdfa dfa,
    boolean printDebugInfo
  ) {
    super(mv);
    this.dfa = dfa;
    this.printDebugInfo = printDebugInfo;

    // Intialize all local offsets (incrementing offset works since all locals are single-width)
    int nextLocal = 0;
    this.inputLocal = nextLocal++;
    this.offsetLocal = nextLocal++;
    this.maxOffsetLocal = nextLocal++;
    this.groupsLocal = nextLocal++;
    this.charTempLocal = nextLocal++;
    {
      final var tempRegisters = new HashMap<Register.Temporary, Integer>();
      for (final var register : dfa.registers()) {
        tempRegisters.put(register, nextLocal++);
      }
      this.temporaryRegisterLocals = Collections.unmodifiableMap(tempRegisters);
    }

    // Initialize labels
    this.stateLabels = dfa
      .states
      .keySet()
      .stream()
      .collect(Collectors.toUnmodifiableMap(Function.identity(), s -> new Label()));
    this.returnSuccess = new Label();
    this.returnFailure = new Label();

    this.fixedClasses = new HashSet<>(dfa.groupMarkers.classes());
  }


  public void visitDfa() {
    initializeLocals();

    // Track entry into DFA
    if (printDebugInfo) {
      final var localVars = " (temporaries " + temporaryRegisterLocals + ")";
      visitPrintErrConstantString("[TDFA] starting run" + localVars + " on: ", false);
      mv.visitVarInsn(Opcodes.ALOAD, inputLocal);
      Method.TOSTRING_M.invokeMethod(mv, Method.OBJECT_CLASS_NAME);
      visitPrintErrString();
    }

    // Jump to the first state
    mv.visitJumpInsn(Opcodes.GOTO, stateLabels.get(dfa.initialState));

    // Lay out the blocks for each state
    for (Map.Entry<Integer, Label> entry : stateLabels.entrySet()) {
      mv.visitLabel(entry.getValue());
      final int state = entry.getKey();

      if (printDebugInfo) {
        visitPrintErrConstantString("[TDFA] entering " + state, true);
      }

      // increment the offset and, if it exceeds length, return whether we are in terminal state
      final var finalCommands = dfa.finalStates.get(state);
      mv.visitIincInsn(offsetLocal, 1);
      mv.visitVarInsn(Opcodes.ILOAD, offsetLocal);
      mv.visitVarInsn(Opcodes.ILOAD, maxOffsetLocal);
      if (finalCommands == null) {
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, returnFailure);
      } else {
        final var notDone = new Label();
        mv.visitJumpInsn(Opcodes.IF_ICMPLT, notDone);

        // Execute final commands
        visitTagCommands(finalCommands);
        mv.visitJumpInsn(Opcodes.GOTO, returnSuccess);

        mv.visitLabel(notDone);
      }

      // get the next character
      mv.visitVarInsn(Opcodes.ALOAD, inputLocal);
      mv.visitVarInsn(Opcodes.ILOAD, offsetLocal);
      Method.CHARAT_M.invokeMethod(mv, Method.CHARSEQUENCE_CLASS_NAME);

      // Generate the transition
      visitTransition(
        dfa.states.get(state),
        Optional.ofNullable(dfa.mode == MatchMode.PREFIX ? finalCommands : null)
      );
    }

    // Final blocks
    visitReturnSuccess();
    visitReturnFailure();

    if (printDebugInfo) {
      final var stateOffsets = stateLabels
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getOffset()));
      System.err.println("[TDFA compilation] state offsets: " + stateOffsets);
    }
  }

  /**
   * Initialize local variables that aren't arguments.
   */
  private void initializeLocals() {

    // Fill in group markers whose position is anchored on the start/end offsets (TODO: do this at end?)
    mv.visitVarInsn(Opcodes.ALOAD, groupsLocal);
    dfa.groupMarkers.startClass().ifPresent((GroupMarkers.FixedClass startClass) -> {
      if (!fixedClasses.remove(startClass)) {
        return;
      }

      final int distanceToStart = startClass.distanceToStart.getAsInt();
      final var fixedToStart = new HashSet<>(startClass.memberDistances.entrySet());
      fixedToStart.add(new SimpleImmutableEntry<>(startClass.representative, -distanceToStart));

      for (var fixedGroup : fixedToStart) {
        final GroupMarker forGroup = fixedGroup.getKey();

        // Prepare for `iastore` assigning to the fixed group
        mv.visitInsn(Opcodes.DUP);
        visitConstantInt(forGroup.arrayOffset());

        // Compute the offset
        mv.visitIntInsn(Opcodes.ILOAD, offsetLocal);
        final int increment = fixedGroup.getValue() + distanceToStart;
        if (increment != 0) {
          visitConstantInt(increment);
          mv.visitInsn(Opcodes.IADD);
        }

        // Store the fixed group
        mv.visitInsn(Opcodes.IASTORE);
      }
    });
    dfa.groupMarkers.endClass().ifPresent((GroupMarkers.FixedClass endClass) -> {
      if (dfa.mode == MatchMode.PREFIX || !fixedClasses.remove(endClass)) {
        return;
      }

      final int distanceToEnd = endClass.distanceToEnd.getAsInt();
      final var fixedToEnd = new HashSet<>(endClass.memberDistances.entrySet());
      fixedToEnd.add(new SimpleImmutableEntry<>(endClass.representative, distanceToEnd));

      for (var fixedGroup : fixedToEnd) {
        final GroupMarker forGroup = fixedGroup.getKey();

        // Prepare for `iastore` assigning to the fixed group
        mv.visitInsn(Opcodes.DUP);
        visitConstantInt(forGroup.arrayOffset());

        // Compute the offset
        mv.visitIntInsn(Opcodes.ILOAD, maxOffsetLocal);
        final int increment = fixedGroup.getValue() + distanceToEnd;
        if (increment != 0) {
          visitConstantInt(increment);
          mv.visitInsn(Opcodes.IADD);
        }

        // Store the fixed group
        mv.visitInsn(Opcodes.IASTORE);
      }
    });
    mv.visitInsn(Opcodes.POP);

    // Temporary `char` variable
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitVarInsn(Opcodes.ISTORE, charTempLocal);

    // Temporary register variables
    // TODO: this is needed for ASM to infer variables exist - check if manual frames eliminate this
    for (final var temporaryRegisterLocal : temporaryRegisterLocals.values()) {
      mv.visitInsn(Opcodes.ICONST_M1);
      mv.visitVarInsn(Opcodes.ISTORE, temporaryRegisterLocal);
    }

    // Decrement offset
    mv.visitIincInsn(offsetLocal, -1);
  }

  /**
   * Emit code for the {@link #returnSuccess} block.
   *
   * <p>This is nominally about preparing the match object, but most of the work
   * is filling in untracked group markers that are at fixed positions from
   * group markers that <em>are</em> tracked.
   */
  private void visitReturnSuccess() {
    mv.visitLabel(returnSuccess);

    // Fill in group markers whose position is anchored on remaining classes
    for (final var fixedClass : fixedClasses) {

      final GroupMarker trackedGroup = fixedClass.representative;
      for (var fixedGroup : fixedClass.memberDistances.entrySet()) {
        final GroupMarker forGroup = fixedGroup.getKey();
        final int distance = fixedGroup.getValue();

        // Prepare for `iastore` assigning to the fixed group
        mv.visitIntInsn(Opcodes.ALOAD, groupsLocal);
        visitConstantInt(forGroup.arrayOffset());

        // Get the relative value
        mv.visitIntInsn(Opcodes.ALOAD, groupsLocal);
        visitConstantInt(trackedGroup.arrayOffset());
        mv.visitInsn(Opcodes.IALOAD);

        // Only if the distance != 0 do we need to decrement
        if (distance != 0) {

          // If the group is not unavoidable, we must only decrement if != -1
          if (!fixedClass.unavoidable) {
            final var afterDecrement = new Label();
            mv.visitInsn(Opcodes.DUP);
            mv.visitJumpInsn(Opcodes.IFLT, afterDecrement);
            visitConstantInt(distance);
            mv.visitInsn(Opcodes.IADD);
            mv.visitLabel(afterDecrement);
          } else {
            visitConstantInt(distance);
            mv.visitInsn(Opcodes.IADD);
          }
        }

        // Store the fixed group
        mv.visitInsn(Opcodes.IASTORE);
      }
    }
    fixedClasses.clear();

    // Print out the final output
    if (printDebugInfo) {
      visitPrintErrConstantString("[TDFA] exiting run (successful): ", false);
      mv.visitIntInsn(Opcodes.ALOAD, groupsLocal);
      Method.INTARRTOSTRING_M.invokeMethod(mv, Method.ARRAYS_CLASS_NAME);
      visitPrintErrString();
    }

    // Construct and return the final match result
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IRETURN);
  }

  /**
   * Emit code for the {@link #returnSuccess} block.
   */
  private void visitReturnFailure() {
    mv.visitLabel(returnFailure);

    // Print out the final output
    if (printDebugInfo) {
      visitPrintErrConstantString("[TDFA] exiting run (unsuccessful)", true);
    }

    // Construct and return the final match (non-)result
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitInsn(Opcodes.IRETURN);
  }

  /**
   * Generate the branching logic associated with a state transition.
   *
   * <p>This is tricky because there are a lot of ways to do this (and there's
   * not even an obvious way to rank the strategies since we're gunning for
   * small bytecode output as well as fast execution). The approach currently
   * taken is:
   *
   * <ul>
   *   <li>
   *     list out all of the ranges in all of the transitions and split them
   *     into those that are "small" and those that are "large" (threshold is
   *     set to {@code smallRangeThresholdSize})
   *
   *   <li>
   *     small transitions all get fed into one branching construct (usually a
   *     {@code lookupswitch}, but {@code visitLookupBranch} helps generate
   *     efficient code for simpler cases).
   *
   *   <li>
   *     large transitions get grouped based on their target labels and are
   *     then "tried" in order. Each group gets tried sequentially using range
   *     checks (some effort is made to minimize the number of jumps).
   * </ul>
   *
   * <p>Some ideas for optimization to try:
   *
   * <ul>
   *   <li>
   *     Keeping track of values that are no longer reachable. For example,
   *     a simple negation such as {@code [^l]} doesn't need to match
   *     {@code U+0-k} and {@code m-U+10FFFF} separately if {@code l} has
   *     already been ruled out.
   *
   *   <li>
   *     Building a binary search tree of decisions for finding the range
   *     containing the scrutinee.
   * </ul>
   *
   * @param transitionsMap transitions out of the current state
   * @param finalCommandsOpt final commands to run if out of input (otherwise fail)
   */
  private void visitTransition(
    Map<CodeUnitTransition, Tdfa.TaggedTransition> transitionsMap,
    Optional<List<TagCommand>> finalAcceptingCommandsOpt
  ) {

    // Commands that occur for _all_ transitions (hoist these to before branching)
    final Set<TagCommand> sharedCommands = Stream
      .<List<TagCommand>>concat(
        transitionsMap.values().stream().map(Tdfa.TaggedTransition::commands),
        finalAcceptingCommandsOpt.stream()
      )
      .<Set<TagCommand>>map(HashSet::new)
      .reduce((s1, s2) -> { s1.retainAll(s2); return s1; })
      .orElse(Collections.emptySet());
    final var filteredFinalAcceptingCommandsOpt = finalAcceptingCommandsOpt
      .map(finalCommands ->
        finalCommands
          .stream()
          .filter(command -> !sharedCommands.contains(command))
          .collect(Collectors.toUnmodifiableList())
      );

    /* Failing to find a transition is only a failure if we don't have final
     * accepting commands. However, if we have a non-empty set of accepting
     * commands, we will still need an extra block to run those before jumping
     * to the success label.
     */
    final Label noTransitionFound = filteredFinalAcceptingCommandsOpt
      .map(commands -> (commands.isEmpty()) ? returnSuccess : new Label())
      .orElse(returnFailure);

    record TargetLabel(Label stateLabel, List<TagCommand> commands) { }

    // Merge transitions based on their target labels (so we can re-use shared target labels)
    final Map<TargetLabel, IntRangeSet> transitions = transitionsMap
      .entrySet()
      .stream()
      .collect(
        Collectors.groupingBy(
          (Map.Entry<CodeUnitTransition, Tdfa.TaggedTransition> entry) -> {
            final var lbl = stateLabels.get(entry.getValue().targetState());
            final List<TagCommand> commands = entry
              .getValue()
              .commands()
              .stream()
              .filter(command -> !sharedCommands.contains(command))
              .collect(Collectors.toUnmodifiableList());
            return new TargetLabel(lbl, commands);
          },
          Collectors.reducing(
            IntRangeSet.EMPTY,
            entry -> entry.getKey().codeUnitSet(),
            (x, y) -> x.union(y)
          )
        )
      );

    // Contains entries from ranges that are `smallRangeThresholdSize` or less elements
    final var shortRanges = new TreeMap<Integer, TargetLabel>();

    // Contains all (sorted) ranges keyed based on their label
    final var longRanges = new HashMap<TargetLabel, List<IntRange>>();

    // Split all ranges into `shortRanges` or `longRanges`
    for (Map.Entry<TargetLabel, IntRangeSet> entry : transitions.entrySet()) {
      final TargetLabel target = entry.getKey();
      final var ranges = new ArrayList<IntRange>();

      for (IntRange codeUnitRange : entry.getValue().ranges()) {
        if (codeUnitRange.size() <= smallRangeThresholdSize) {
          for (Integer codeUnit : codeUnitRange) {
            shortRanges.put(codeUnit, target);
          }
        } else {
          ranges.add(codeUnitRange);
        }
      }

      if (!ranges.isEmpty()) {
        longRanges.put(target, ranges);
      }
    }

    // Generate code for shared commands
    visitTagCommands(sharedCommands);

    // Populate the variable if we know we have long ranges coming
    if (!longRanges.isEmpty()) {
      if (!shortRanges.isEmpty()) {
        mv.visitInsn(Opcodes.DUP);
      }
      mv.visitIntInsn(Opcodes.ISTORE, charTempLocal);
    }

    // Generate one switch/branch for all the short ranges
    if (longRanges.isEmpty() || !shortRanges.isEmpty()) {

      // If there are long ranges to check, dup the char and don't fallthrough to failure
      final var lookupFallthrough = longRanges.isEmpty() ? noTransitionFound : new Label();

      final int[] codeUnitValues = shortRanges
        .keySet()
        .stream()
        .mapToInt((Integer c) -> c.intValue())
        .toArray();
      final Label[] targetStateLabels = new Label[shortRanges.size()];
      final var commandBlocks = new HashMap<TargetLabel, Label>();
      {
        int stateLabelIdx = 0;
        for (final var targetLabel : shortRanges.values()) {
          if (targetLabel.commands().isEmpty()) {
            targetStateLabels[stateLabelIdx] = targetLabel.stateLabel();
          } else {
            final var commandsLabel = commandBlocks.computeIfAbsent(targetLabel, k -> new Label());
            targetStateLabels[stateLabelIdx] = commandsLabel;
          }
          stateLabelIdx++;
        }
      }

      // Branch to the right command block or state
      visitLookupBranch(lookupFallthrough, codeUnitValues, targetStateLabels);

      // Place command block
      for (var commandBlock : commandBlocks.entrySet()) {
        mv.visitLabel(commandBlock.getValue());
        visitTagCommands(commandBlock.getKey().commands());
        mv.visitJumpInsn(Opcodes.GOTO, commandBlock.getKey().stateLabel());
      }

      // If there are long ranges, place the fallthrough label
      if (!longRanges.isEmpty()) {
        mv.visitLabel(lookupFallthrough);
      }
    }

    if (!longRanges.isEmpty()) {

      // Generate one check per set of ranges and try them sequentially
      for (Map.Entry<TargetLabel, List<IntRange>> labelRanges : longRanges.entrySet()) {

        /* This uses a trick to do aggregate all range checks to end up using
         * only a single branching operation. Suppose we are trying to see if
         * `x` is in the range `[l0,r0] U [l1,r1] U ... U [ln,rn]`.
         *
         * ```
         * x ∈ [li,ri]
         *  ≡ li ≤ x && x ≤ ri
         *  ≡ (li-1) - x < 0 && x - (ri+1) < 0
         *  ≡ (((li-1) - x) & (x - (ri+1))) < 0
         * ```
         *
         * The last step of the equivalence levarages the fact that `x` is
         * a signed number and the twos-complement representation implies that
         * `x` is negative iff its first bit is 1.
         *
         * The generalization to all ranges uses a similar trick, but with OR.
         *
         * ```
         * x ∈ [l0,r0] U [l1,r1] U ... U [ln,rn]
         *  ≡ x ∈ [l0,r0] || ... || x ∈ [ln,rn]
         *  ≡ (((l0-1) - x) & (x - (r0+1))) < 0 || ... || (((ln-1) - x) & (x - (rn+1))) < 0
         *  ≡ ((((l0-1) - x) & (x - (r0+1))) | ... | (((ln-1) - x) & (x - (rn+1)))) < 0
         * ```
         */
        boolean firstIteration = true;
        for (IntRange range : labelRanges.getValue()) {

          // lowerBound - x
          visitConstantInt(range.lowerBound() - 1);
          mv.visitIntInsn(Opcodes.ILOAD, charTempLocal);
          mv.visitInsn(Opcodes.ISUB);

          // x - upperBound
          mv.visitIntInsn(Opcodes.ILOAD, charTempLocal);
          visitConstantInt(range.upperBound() + 1);
          mv.visitInsn(Opcodes.ISUB);

          // (lowerBound - x) & (x - upperBound)
          mv.visitInsn(Opcodes.IAND);

          // acc = acc | ((lowerBound - x) & (x - upperBound))
          if (firstIteration) {
            firstIteration = false;
          } else {
            mv.visitInsn(Opcodes.IOR);
          }
        }

        final var targetLabel = labelRanges.getKey();
        if (targetLabel.commands().isEmpty()) {
          mv.visitJumpInsn(Opcodes.IFLT, targetLabel.stateLabel());
        } else {
          final var notMatched = new Label();
          mv.visitJumpInsn(Opcodes.IFGE, notMatched);

          // Execute final commands
          visitTagCommands(targetLabel.commands());
          mv.visitJumpInsn(Opcodes.GOTO, targetLabel.stateLabel());

          mv.visitLabel(notMatched);
        }
      }

      mv.visitJumpInsn(Opcodes.GOTO, noTransitionFound);
    }

    /* If there the no-transition-found case has a final block of commands,
     * place that block and then return success.
     */
    if (filteredFinalAcceptingCommandsOpt.isPresent()) {
      final var acceptingCommands = filteredFinalAcceptingCommandsOpt.get();
      if (!acceptingCommands.isEmpty()) {
        mv.visitLabel(noTransitionFound);
        visitTagCommands(filteredFinalAcceptingCommandsOpt.get());
        mv.visitJumpInsn(Opcodes.GOTO, returnSuccess);
      }
    }
  }

  /**
   * Emit code that executes tag commands.
   *
   * @param commands tag commands
   */
  private void visitTagCommands(Iterable<TagCommand> commands) {
    for (final TagCommand command : commands) {
      visitTagCommand(command);
    }
  }

  /**
   * Emit code that executes a tag command.
   *
   * @param command tag command
   */
  private void visitTagCommand(TagCommand command) {

    if (command instanceof TagCommand.Copy copy) {
      final Register copyFrom = copy.copyFrom();
      final Register assignTo = copy.assignTo();

      // Prepare for assignment to a group (the `iastore` comes later)
      if (assignTo instanceof GroupMarker group) {
        mv.visitVarInsn(Opcodes.ALOAD, groupsLocal);
        visitConstantInt(group.arrayOffset());
      }

      // Load from `copyFrom`
      if (copyFrom instanceof Register.Temporary temp) {
        mv.visitVarInsn(Opcodes.ILOAD, temporaryRegisterLocals.get(temp));
      } else if (copyFrom instanceof GroupMarker group) {
        mv.visitVarInsn(Opcodes.ALOAD, groupsLocal);
        visitConstantInt(group.arrayOffset());
        mv.visitInsn(Opcodes.IALOAD);
      } else {
        throw new IllegalArgumentException("Reading unknown register type " + copyFrom);
      }

      // Print out the command
      if (printDebugInfo) {
        final var message = "[TDFA] " + copy.compactString() + ", which is currently ";
        visitPrintErrConstantString(message, false);
        mv.visitInsn(Opcodes.DUP);
        visitPrintErrInt();
      }

      // Assign to `assignTo`
      if (assignTo instanceof Register.Temporary temp) {
        mv.visitVarInsn(Opcodes.ISTORE, temporaryRegisterLocals.get(temp));
      } else if (assignTo instanceof GroupMarker group) {
        mv.visitInsn(Opcodes.IASTORE);
      } else {
        throw new IllegalArgumentException("Writing unknown register type " + assignTo);
      }
    } else if (command instanceof TagCommand.CurrentPosition currentPos) {
      final Register assignTo = currentPos.assignTo();

      // Assign to `assignTo`
      if (assignTo instanceof Register.Temporary temp) {
        mv.visitVarInsn(Opcodes.ILOAD, offsetLocal);
        mv.visitVarInsn(Opcodes.ISTORE, temporaryRegisterLocals.get(temp));
      } else if (assignTo instanceof GroupMarker group) {
        mv.visitVarInsn(Opcodes.ALOAD, groupsLocal);
        visitConstantInt(group.arrayOffset());
        mv.visitVarInsn(Opcodes.ILOAD, offsetLocal);
        mv.visitInsn(Opcodes.IASTORE);
      } else {
        throw new IllegalArgumentException("Writing unknown register type " + assignTo);
      }

      // Print out the command
      if (printDebugInfo) {
        final var message = "[TDFA] " + currentPos.compactString() + ", which is currently ";
        visitPrintErrConstantString(message, false);
        mv.visitVarInsn(Opcodes.ILOAD, offsetLocal);
        visitPrintErrInt();
      }
    } else {
      throw new IllegalArgumentException("Unknown command " + command);
    }
  }

}
