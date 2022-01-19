package automata.codegen;

import automata.graph.Tdfa;
import automata.graph.GroupMarker;
import automata.graph.Register;
import automata.graph.TagCommand;
import automata.graph.CodeUnitTransition;
import automata.graph.RelativeGroupLocation;
import automata.util.IntRange;
import automata.util.IntRangeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.function.Function;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Functionality for generating the body of a TDFA matching/checking function.
 *
 * This uses the natural mapping of a tagged DFA into the control-flow graph of
 * the bytecode method: states are represented by blocks with transitions
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
   * Controls whether the generated method returns a {@code ArrayMatchResult}
   * or a simple {@code boolean}.
   */
  private final boolean captureGroups;

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
   * If the entire input string is being matched, this can be set to the length
   * of the input string.
   */
  private final int maxOffsetLocal;

  /**
   * Offset for local of type {@code int[]} corresponding to the group
   * variables being tracked.
   *
   * This is only a valid offset if {@link #captureGroups} is set.
   */
  private final int groupsLocal;

  /**
   * Offset for a local of type {@code char} used as a temporary variable in
   * some of the code-unit branching logic.
   */
  private final int charTempLocal;

  /**
   * Offsets for local variables associated with various temporary registers.
   *
   * If {@link #captureGroups} is not set, this will be empty. In either case,
   * the map is unmodifiable.
   */
  private final Map<Register.Temporary, Integer> temporaryRegisterLocals;

  /**
   * Labels associated with DFA states.
   *
   * Each DFA state has a label and going to a new state is as simple as
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

  public TdfaMethodCodegen(
    MethodVisitor mv,
    Tdfa dfa,
    boolean captureGroups,
    boolean printDebugInfo
  ) {
    super(mv);
    this.dfa = dfa;
    this.captureGroups = captureGroups;
    this.printDebugInfo = printDebugInfo;

    // Intialize all local offsets (incrementing offset works since all locals are single-width)
    int nextLocal = 0;
    this.inputLocal = nextLocal++;
    this.offsetLocal = nextLocal++;
    this.maxOffsetLocal = nextLocal++;
    this.groupsLocal = captureGroups ? nextLocal++ : -1;
    this.charTempLocal = nextLocal++;
    if (captureGroups) {
      final var tempRegisters = new HashMap<Register.Temporary, Integer>();
      for (final var register : dfa.registers()) {
        tempRegisters.put(register, nextLocal++);
      }
      this.temporaryRegisterLocals = Collections.unmodifiableMap(tempRegisters);
    } else {
      this.temporaryRegisterLocals = Collections.emptyMap();
    }

    // Initialize labels
    this.stateLabels = dfa
      .states
      .keySet()
      .stream()
      .collect(Collectors.toUnmodifiableMap(Function.identity(), s -> new Label()));
    this.returnSuccess = new Label();
    this.returnFailure = new Label();
  }


  public void visitDfa() {
    initializeLocals();

    // Track entry into DFA
    if (printDebugInfo) {
      final var localVars = captureGroups ? " (temporaries " + temporaryRegisterLocals + ")" : "";
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
      if (finalCommands == null || !captureGroups) {
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, (finalCommands == null) ? returnFailure : returnSuccess);
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
      visitTransition(dfa.states.get(state));
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

    // Groups local variable
    // TODO: consider tracking when `Arrays.fill(groups, -1)` is unnecessary
    if (groupsLocal != -1) {
      visitConstantInt(2 * dfa.groupCount);
      mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
      mv.visitInsn(Opcodes.DUP);
      mv.visitInsn(Opcodes.ICONST_M1);
      Method.FILLINT_M.invokeMethod(mv, Method.ARRAYS_CLASS_NAME);
      mv.visitVarInsn(Opcodes.ASTORE, groupsLocal);
    }

    // Temporary `char` variable
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitVarInsn(Opcodes.ISTORE, charTempLocal);

    // Temporary register variables
    // TODO: this is needed for ASM to infer variables exist - check if manual frames eliminate this
    for (final var temporaryRegisterLocal : temporaryRegisterLocals.values()) {
      mv.visitInsn(Opcodes.ICONST_M1);
      mv.visitVarInsn(Opcodes.ISTORE, temporaryRegisterLocal);
    }
  }

  /**
   * Emit code for the {@link #returnSuccess} block.
   */
  private void visitReturnSuccess() {
    mv.visitLabel(returnSuccess);

    /* Fill in fixed group marker tags. Recall: these are tags that we
     * statically determined are always at a fixed distance from another tag,
     * so we can skip any commands for these and fill in the tag only at the
     * end.
     */
    if (captureGroups) {
      for (final var fixedGroup : dfa.fixedTags.entrySet()) {
        final GroupMarker forGroup = fixedGroup.getKey();
        final RelativeGroupLocation location = fixedGroup.getValue();

        // Prepare for `iastore` assigning to the fixed group
        mv.visitIntInsn(Opcodes.ALOAD, groupsLocal);
        visitConstantInt(forGroup.arrayOffset());

        // Get the relative value
        mv.visitIntInsn(Opcodes.ALOAD, groupsLocal);
        visitConstantInt(location.relativeTo().arrayOffset());
        mv.visitInsn(Opcodes.IALOAD);

        // Only if the distance != 0 do we need to decrement
        if (location.distance() != 0) {

          // If the group is not unavoidable, we must only decrement if != -1
          if (!location.unavoidable()) {
            final var afterDecrement = new Label();
            mv.visitInsn(Opcodes.DUP);
            mv.visitJumpInsn(Opcodes.IFLT, afterDecrement);
            visitConstantInt(location.distance());
            mv.visitInsn(Opcodes.ISUB);
            mv.visitLabel(afterDecrement);
          } else {
            visitConstantInt(location.distance());
            mv.visitInsn(Opcodes.ISUB);
          }
        }

        mv.visitInsn(Opcodes.IASTORE);
      }
    }

    // Print out the final output
    if (printDebugInfo) {
      visitPrintErrConstantString("[TDFA] exiting run (successful): ", false);
      if (captureGroups) {
        mv.visitIntInsn(Opcodes.ALOAD, groupsLocal);
        Method.INTARRTOSTRING_M.invokeMethod(mv, Method.ARRAYS_CLASS_NAME);
        visitPrintErrString();
      } else {
        visitPrintErrConstantString("1", true);
      }
    }

    // Construct and return the final match result
    if (captureGroups) {
      mv.visitTypeInsn(Opcodes.NEW, Method.ARRAYMATCHRESULT_CLASS_NAME);
      mv.visitInsn(Opcodes.DUP);
      mv.visitVarInsn(Opcodes.ALOAD, inputLocal);
      mv.visitVarInsn(Opcodes.ALOAD, groupsLocal);
      mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        Method.ARRAYMATCHRESULT_CLASS_NAME,
        "<init>",
        "(Ljava/lang/CharSequence;[I)V",
        false
      );
      mv.visitInsn(Opcodes.ARETURN);
    } else {
      mv.visitInsn(Opcodes.ICONST_1);
      mv.visitInsn(Opcodes.IRETURN);
    }
  }

  /**
   * Emit code for the {@link #returnSuccess} block.
   */
  private void visitReturnFailure() {
    mv.visitLabel(returnFailure);

    // Print out the final output
    if (printDebugInfo) {
      final var returned = captureGroups ? "null" : "0";
      visitPrintErrConstantString("[TDFA] exiting run (unsuccessful): " + returned, true);
    }

    // Construct and return the final match (non-)result
    if (captureGroups) {
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);
    } else {
      mv.visitInsn(Opcodes.ICONST_0);
      mv.visitInsn(Opcodes.IRETURN);
    }
  }

  /**
   * Generate the branching logic associated with a state transition.
   *
   * This is tricky because there are a lot of ways to do this (and there's not
   * even an obvious way to rank the strategies since we're gunning for small
   * bytecode output as well as fast execution). The approach currently taken
   * is:
   *
   *   - list out all of the ranges in all of the transitions and split them
   *     into those that are "small" and those that are "large" (threshold is
   *     set to {@code smallRangeThresholdSize})
   *
   *   - small transitions all get fed into one branching construct (usually a
   *     {@code lookupswitch}, but {@code visitLookupBranch} helps generate
   *     efficient code for simpler cases).
   *
   *   - large transitions get grouped based on their target labels and are
   *     then "tried" in order. Each group gets tried sequentially using range
   *     checks (some effort is made to minimize the number of jumps).
   *
   * Some ideas for optimization to try:
   *
   *   - Keeping track of values that are no longer reachable. For example,
   *     a simple negation such as {@code [^l]} doesn't need to match
   *     {@code U+0-k} and {@code m-U+10FFFF} separately if {@code l} has
   *     already been ruled out.
   *
   *   - Building a binary search tree of decisions for finding the range
   *     containing the scrutinee.
   *
   * @param transitionsMap transitions out of the current state
   */
  private void visitTransition(Map<CodeUnitTransition, Tdfa.TaggedTransition> transitionsMap) {
    final Label noTransitionFound = returnFailure;

    // Commands that occur for _all_ transitions (hoist these to before branching)
    final Set<TagCommand> sharedCommands = captureGroups
      ? transitionsMap
          .values()
          .stream()
          .map(Tdfa.TaggedTransition::commands)
          .<Set<TagCommand>>map(HashSet::new)
          .reduce((s1, s2) -> { s1.retainAll(s2); return s1; })
          .orElse(Collections.emptySet())
      : Collections.emptySet();

    record TargetLabel(Label stateLabel, List<TagCommand> commands) { }

    // Merge transitions based on their target labels (so we can re-use shared target labels)
    final Map<TargetLabel, IntRangeSet> transitions = transitionsMap
      .entrySet()
      .stream()
      .collect(
        Collectors.groupingBy(
          (Map.Entry<CodeUnitTransition, Tdfa.TaggedTransition> entry) -> {
            final var lbl = stateLabels.get(entry.getValue().targetState());
            final List<TagCommand> commands = captureGroups
              ? entry
                .getValue()
                .commands()
                .stream()
                .filter(command -> !sharedCommands.contains(command))
                .collect(Collectors.toUnmodifiableList())
              : Collections.emptyList();
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

      // Assign to `assignTo`
      if (assignTo instanceof Register.Temporary temp) {
        mv.visitVarInsn(Opcodes.ISTORE, temporaryRegisterLocals.get(temp));
      } else if (assignTo instanceof GroupMarker group) {
        mv.visitInsn(Opcodes.IASTORE);
      } else {
        throw new IllegalArgumentException("Writing unknown register type " + assignTo);
      }

      // Print out the command
      if (printDebugInfo) {
        final var message = "[TDFA] copy " + copy.assignTo() + " <- " + copy.copyFrom() + " or ";
        visitPrintErrConstantString(message, false);
        mv.visitVarInsn(Opcodes.ILOAD, temporaryRegisterLocals.get(copy.copyFrom()));
        visitPrintErrInt();
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
        final var message = "[TDFA] set current pos " + currentPos.assignTo() + " <- ";
        visitPrintErrConstantString(message, false);
        mv.visitVarInsn(Opcodes.ILOAD, offsetLocal);
        visitPrintErrInt();
      }
    } else {
      throw new IllegalArgumentException("Unknown command " + command);
    }
  }

}
