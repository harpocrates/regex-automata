package automata;

import java.util.concurrent.atomic.AtomicLong;
import static java.util.AbstractMap.SimpleEntry;
import static java.lang.invoke.MethodType.methodType;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.function.Function;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/* TODO:
 *
 *   - handle `null` inputs
 *   - move to a `compiler` package
 *   - add utilities for generating `.class` files (eg. `Files.write(Paths.get(className), classBytes)`)
 */
public final class CompiledDfaCodegen {

  // Class name constants
  private static final String DFAPATTERN_CLASS_NAME = Type.getInternalName(DfaPattern.class);
  private static final String OBJECT_CLASS_NAME = Type.getInternalName(Object.class);
  private static final String ARRAYMATCHRESULT_CLASS_NAME = Type.getInternalName(ArrayMatchResult.class);
  private static final String SYSTEM_CLASS_NAME = Type.getInternalName(System.class);
  private static final String PRINTSTREAM_CLASS_NAME = Type.getInternalName(java.io.PrintStream.class);
  private static final String ARRAYS_CLASS_NAME = Type.getInternalName(Arrays.class);
  private static final String CHARSEQUENCE_CLASS_NAME = Type.getInternalName(CharSequence.class);

  // Method name constants
  private static final Method EMPTYINIT_M = new Method("<init>", methodType(void.class), Opcodes.INVOKESPECIAL);
  private static final Method PATTERN_M = new Method("pattern", methodType(String.class), Opcodes.INVOKEINTERFACE);
  private static final Method GROUPCOUNT_M = new Method("groupCount", methodType(int.class), Opcodes.INVOKEINTERFACE);
  private static final Method CHECKMATCH_M = new Method("checkMatch", methodType(boolean.class, CharSequence.class), Opcodes.INVOKEINTERFACE);
  private static final Method CAPTUREMATCH_M = new Method("captureMatch", methodType(ArrayMatchResult.class, CharSequence.class), Opcodes.INVOKEINTERFACE);
  private static final Method CHECKMATCHSTATIC_M = new Method("checkMatchStatic", methodType(boolean.class, CharSequence.class), Opcodes.INVOKESTATIC);
  private static final Method CAPTUREMATCHSTATIC_M = new Method("captureMatchStatic", methodType(ArrayMatchResult.class, CharSequence.class), Opcodes.INVOKESTATIC);
  private static final Method TOSTRING_M = new Method("toString", methodType(String.class), Opcodes.INVOKEVIRTUAL);
  private static final Method PRINTSTR_M = new Method("print", methodType(void.class, String.class), Opcodes.INVOKEVIRTUAL);
  private static final Method PRINTLNINT_M = new Method("println", methodType(void.class, int.class), Opcodes.INVOKEVIRTUAL);
  private static final Method PRINTLNSTR_M = new Method("println", methodType(void.class, String.class), Opcodes.INVOKEVIRTUAL);
  private static final Method INTARRTOSTRING_M = new Method("toString", methodType(String.class, int[].class), Opcodes.INVOKESTATIC);
  private static final Method LENGTH_M = new Method("length", methodType(int.class), Opcodes.INVOKEINTERFACE);
  private static final Method CHARAT_M = new Method("charAt", methodType(char.class, int.class), Opcodes.INVOKEINTERFACE);
  private static final Method FILLINT_M = new Method("fill", methodType(void.class, int[].class, int.class), Opcodes.INVOKESTATIC);

  // Print a constant string to STDERR
  private static void genPrintErrConstant(MethodVisitor mv, String message, boolean withNewline) {
    mv.visitFieldInsn(Opcodes.GETSTATIC, SYSTEM_CLASS_NAME, "err", "Ljava/io/PrintStream;");
    mv.visitLdcInsn(message);
    final var printM = withNewline ? PRINTLNSTR_M : PRINTSTR_M;
    printM.invokeMethod(mv, PRINTSTREAM_CLASS_NAME);
  }

  // Print a `String` that is at the top of the operand stack to STDERR
  private static void genPrintErrString(MethodVisitor mv) {
    mv.visitFieldInsn(Opcodes.GETSTATIC, SYSTEM_CLASS_NAME, "err", "Ljava/io/PrintStream;");
    mv.visitInsn(Opcodes.SWAP);
    PRINTLNSTR_M.invokeMethod(mv, PRINTSTREAM_CLASS_NAME);
  }

  // Print an `int` that is at the top of the operand stack to STDERR
  private static void genPrintErrInt(MethodVisitor mv) {
    mv.visitFieldInsn(Opcodes.GETSTATIC, SYSTEM_CLASS_NAME, "err", "Ljava/io/PrintStream;");
    mv.visitInsn(Opcodes.SWAP);
    PRINTLNINT_M.invokeMethod(mv, PRINTSTREAM_CLASS_NAME);
  }

  /**
   * Code generator for a compiled DFA pattern
   *
   * @param pattern initial regular expression pattern
   * @param dfa tagged DFA
   * @param className name of the anonymous class to generate
   * @param classFlags class flags to set (visibility, `final`, `synthetic` etc.)
   * @param printDebugInfo print debug info and generate code which prints debug info to STDERR
   * @return class implementing `DfaPattern`
   */
  public static final ClassWriter generateDfaPatternSubclass(
    String pattern,
    TDFA dfa,
    String className,
    int classFlags,
    boolean printDebugInfo
  ) {

    // Note: `COMPUTE_FRAMES` means that `visitMaxs` ignores its arguments
    final var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(
      Opcodes.V1_8,
      Opcodes.ACC_SUPER | classFlags,
      className,
      null, // signature
      OBJECT_CLASS_NAME,
      new String[] { DFAPATTERN_CLASS_NAME }
    );

    // Set of all temporary registers in the TDFA
    final Set<Register.Temporary> registers = dfa.registers();

    // Make constructor (which takes no arguments - the class has no state!)
    {
      final var mv = EMPTYINIT_M.newMethod(cw, Opcodes.ACC_PUBLIC);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      EMPTYINIT_M.invokeMethod(mv, OBJECT_CLASS_NAME);
      mv.visitCode();
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // `pattern` method
    {
      final var mv = PATTERN_M.newMethod(cw, Opcodes.ACC_PUBLIC);
      mv.visitCode();
      mv.visitLdcInsn(pattern);
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // `groupCount` method
    {
      final var mv = GROUPCOUNT_M.newMethod(cw, Opcodes.ACC_PUBLIC);
      mv.visitCode();
      mv.visitLdcInsn(dfa.groupCount);
      mv.visitInsn(Opcodes.IRETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // `checkMatchStatic` static helper method
    {
      final var mv = CHECKMATCHSTATIC_M.newMethod(cw, Opcodes.ACC_PRIVATE);
      mv.visitCode();
      generateBytecodeForAutomata(mv, dfa, null, printDebugInfo);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // `checkMatch` method (just calls out to `checkMatchStatic`)
    {
      final var mv = CHECKMATCH_M.newMethod(cw, Opcodes.ACC_PUBLIC);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 1);
      CHECKMATCHSTATIC_M.invokeMethod(mv, className);
      mv.visitInsn(Opcodes.IRETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // `captureMatchState` static helper method
    {
      final var mv = CAPTUREMATCHSTATIC_M.newMethod(cw, Opcodes.ACC_PRIVATE);
      mv.visitCode();
      generateBytecodeForAutomata(mv, dfa, registers, printDebugInfo);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // `captureMatch` method
    {
      final var mv = CAPTUREMATCH_M.newMethod(cw, Opcodes.ACC_PUBLIC);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 1);
      CAPTUREMATCHSTATIC_M.invokeMethod(mv, className);
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    cw.visitEnd();
    return cw;
  }

  private static void generateBytecodeForCommand(
    MethodVisitor mv,
    TagCommand command,
    int offsetVar,
    int groupsVar,
    Map<Register.Temporary, Integer> temporaryVars,
    boolean printDebugInfo
  ) {
    if (command instanceof TagCommand.Copy copy) {

      final Register copyFrom = copy.copyFrom();
      final Register assignTo = copy.assignTo();

      // Prepare for assignment to a group
      if (assignTo instanceof GroupMarker group) {
        mv.visitVarInsn(Opcodes.ALOAD, groupsVar);
        pushConstantInt(mv, group.arrayOffset());
      }

      // Load from `copyFrom`
      if (copyFrom instanceof Register.Temporary temp) {
        mv.visitVarInsn(Opcodes.ILOAD, temporaryVars.get(temp));
      } else if (copyFrom instanceof GroupMarker group) {
        mv.visitVarInsn(Opcodes.ALOAD, groupsVar);
        pushConstantInt(mv, group.arrayOffset());
        mv.visitInsn(Opcodes.IALOAD);
      }

      // Assign to `assignTo`
      if (assignTo instanceof Register.Temporary temp) {
        mv.visitVarInsn(Opcodes.ISTORE, temporaryVars.get(temp));
      } else if (assignTo instanceof GroupMarker group) {
        mv.visitInsn(Opcodes.IASTORE);
      }

      if (printDebugInfo) {
        final var message = "[TDFA] copy " + copy.assignTo() + " <- " + copy.copyFrom() + " or ";
        genPrintErrConstant(mv, message, false);
        mv.visitVarInsn(Opcodes.ILOAD, temporaryVars.get(copy.copyFrom()));
        genPrintErrInt(mv);
      }
    } else if (command instanceof TagCommand.CurrentPosition currentPos) {
      final Register assignTo = currentPos.assignTo();

      if (assignTo instanceof Register.Temporary temp) {
        mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
        mv.visitVarInsn(Opcodes.ISTORE, temporaryVars.get(temp));
      } else if (assignTo instanceof GroupMarker group) {
        mv.visitVarInsn(Opcodes.ALOAD, groupsVar);
        pushConstantInt(mv, group.arrayOffset());
        mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
        mv.visitInsn(Opcodes.IASTORE);
      }

      if (printDebugInfo) {
        final var message = "[TDFA] set current pos " + currentPos.assignTo() + " <- ";
        genPrintErrConstant(mv, message, false);
        mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
        genPrintErrInt(mv);
      }
    } else {
      throw new IllegalArgumentException("Unknown command " + command);
    }
  }

  /**
   * Generate the method body associated with simulating the TDFA automata.
   *
   * If {@code registers} is {@code null}, this produces a method that tracks
   * nothing but offset in the string and just returns a boolean indicating
   * match success or failure. Otherwise, the tags on the DFA will be simulated
   * using local variables and the final output of the generated method will
   * be an {@code ArrayMatchResult} (which is {@code null} in the case of no
   * match).
   *
   * @param mv method visitor (only argument is the input `CharSequence`)
   * @param dfa tagged DFA
   * @param registers temporary registers (set to {@code null} to ignore tags)
   * @param printDebugInfo print debug info and generate code which prints debug info to STDERR
   */
  private static void generateBytecodeForAutomata(
    MethodVisitor mv,
    TDFA dfa,
    Set<Register.Temporary> registers,
    boolean printDebugInfo
  ) {
    final int inputVar = 0;  // Input argument: `CharSequence input`
    final int offsetVar = 1; // Local tracking (ascending) offset in string: `int offset`
    final int lengthVar = 2; // Local tracking max offset in input `int length`
    final int groupsVar = 3; // Local tracking group variables: `int[] groups`
    final int codeUnitTempVar = 4; // Local sometimes used in codeUnit tests: `char codeUnit`

    // Mapping from temporary register in the TDFA to the JVM locals in this method
    final var temporaryVars = new HashMap<Register.Temporary, Integer>();
    if (registers != null) {
      int nextLocal = 5;
      for (final var register : registers) {
        temporaryVars.put(register, nextLocal++);
      }
    }

    // Variable for offset in input
    mv.visitInsn(Opcodes.ICONST_M1);
    mv.visitVarInsn(Opcodes.ISTORE, offsetVar);

    // Variable for length of input
    mv.visitVarInsn(Opcodes.ALOAD, inputVar);
    LENGTH_M.invokeMethod(mv, CHARSEQUENCE_CLASS_NAME);
    mv.visitVarInsn(Opcodes.ISTORE, lengthVar);

    // Groups variable
    if (registers != null) {
      pushConstantInt(mv, 2 * dfa.groupCount);
      mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
      mv.visitInsn(Opcodes.DUP);
      mv.visitInsn(Opcodes.ICONST_M1);
      FILLINT_M.invokeMethod(mv, ARRAYS_CLASS_NAME);
      mv.visitVarInsn(Opcodes.ASTORE, groupsVar);
    }

    // Help ASM infer the right signatures
    // TODO: this is where register allocation to decrease registers matters
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitVarInsn(Opcodes.ISTORE, codeUnitTempVar);
    for (final var tempVar : temporaryVars.values()) {
      mv.visitInsn(Opcodes.ICONST_M1);
      mv.visitVarInsn(Opcodes.ISTORE, tempVar);
    }

    final Label returnFailure = new Label();
    final Label returnSuccess = new Label();
    final Map<Integer, Label> stateLabels = dfa
      .states
      .keySet()
      .stream()
      .collect(Collectors.toMap(Function.<Integer>identity(), id -> new Label()));

    // Track entry into TDFA
    if (printDebugInfo) {
      final var localVars = (registers == null) ? "" : " (temporaries " + temporaryVars + ")";
      genPrintErrConstant(mv, "[TDFA] starting run" + localVars + " on: ", false);
      mv.visitVarInsn(Opcodes.ALOAD, inputVar);
      TOSTRING_M.invokeMethod(mv, OBJECT_CLASS_NAME);
      genPrintErrString(mv);
    }

    // Jump to the first state
    mv.visitJumpInsn(Opcodes.GOTO, stateLabels.get(dfa.initialState));

    // Lay out the blocks for each state
    for (Map.Entry<Integer, Label> entry : stateLabels.entrySet()) {
      mv.visitLabel(entry.getValue());
      final int state = entry.getKey();

      if (printDebugInfo) {
        genPrintErrConstant(mv, "[TDFA] entering " + state, true);
      }

      // increment the offset and, if it exceeds length, return whether we are in terminal state
      final var finalCommands = dfa.finalStates.get(state);
      mv.visitIincInsn(offsetVar, 1);
      mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
      mv.visitVarInsn(Opcodes.ILOAD, lengthVar);
      if (finalCommands == null || registers == null) {
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, (finalCommands == null) ? returnFailure : returnSuccess);
      } else {
        final var notDone = new Label();
        mv.visitJumpInsn(Opcodes.IF_ICMPLT, notDone);

        // Execute final commands
        for (final var command : finalCommands) {
          generateBytecodeForCommand(
            mv,
            command,
            offsetVar,
            groupsVar,
            temporaryVars,
            printDebugInfo
          );
        }
        mv.visitJumpInsn(Opcodes.GOTO, returnSuccess);

        mv.visitLabel(notDone);
      }

      // get the next character
      mv.visitVarInsn(Opcodes.ALOAD, inputVar);
      mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
      CHARAT_M.invokeMethod(mv, CHARSEQUENCE_CLASS_NAME);

      generateDfaTransition(
        mv,
        codeUnitTempVar,
        dfa.states.get(state),
        stateLabels,
        registers == null,
        groupsVar,
        temporaryVars,
        offsetVar,
        returnFailure,
        printDebugInfo
      );
    }

    // Returning unsuccessfully
    mv.visitLabel(returnFailure);
    if (printDebugInfo) {
      final var returned = (registers == null) ? "0" : "null";
      genPrintErrConstant(mv, "[TDFA] exiting run (unsuccessful): " + returned, true);
    }
    if (registers == null) {
      mv.visitInsn(Opcodes.ICONST_0);
      mv.visitInsn(Opcodes.IRETURN);
    } else {
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);
    }

    // Returning successfully
    mv.visitLabel(returnSuccess);

    if (printDebugInfo) {
      genPrintErrConstant(mv, "[TDFA] exiting run (successful): ", false);
      if (registers != null) {
        mv.visitIntInsn(Opcodes.ALOAD, groupsVar);
        INTARRTOSTRING_M.invokeMethod(mv, ARRAYS_CLASS_NAME);
        genPrintErrString(mv);
      } else {
        genPrintErrConstant(mv, "1", true);
      }
    }

    // Construct and return the final match result
    if (registers == null) {
      mv.visitInsn(Opcodes.ICONST_1);
      mv.visitInsn(Opcodes.IRETURN);
    } else {
      mv.visitTypeInsn(Opcodes.NEW, ARRAYMATCHRESULT_CLASS_NAME);
      mv.visitInsn(Opcodes.DUP);
      mv.visitVarInsn(Opcodes.ALOAD, inputVar);
      mv.visitVarInsn(Opcodes.ALOAD, groupsVar);
      mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        ARRAYMATCHRESULT_CLASS_NAME,
        "<init>",
        "(Ljava/lang/CharSequence;[I)V",
        false
      );
      mv.visitInsn(Opcodes.ARETURN);
    }

    if (printDebugInfo) {
      final var stateOffsets = stateLabels
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getOffset()));
      System.err.println("[TDFA compilation] state offsets: " + stateOffsets);
    }
  }

  private static final int SMALL_RANGE_SIZE = 16;

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
   *     set to {@code SMALL_RANGE_SIZE})
   *
   *   - small transitions all get fed into one branching construct (usually a
   *     {@code lookupswitch}, but {@code makeBranch} helps generate efficient
   *     code for simpler cases).
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
   * @param mv method visitor
   * @param codeUnitTempVar index of a temporary variable for a {@code char}
   * @param transitionsMap transitions out of the current state
   * @param stateLabels mapping of DFA states to their labels
   * @param skipCommands whether to skip commands
   * @param groupsVar group array variable
   * @param temporaryVars mapping of tagged registers to their locals
   * @param noTransitionFound what to do if the next character doesn't match
   */
  private static void generateDfaTransition(
    MethodVisitor mv,
    int codeUnitTempVar,
    Map<CodeUnitTransition, TDFA.TaggedTransition> transitionsMap,
    Map<Integer, Label> stateLabels,
    boolean skipCommands,
    int groupsVar,
    Map<Register.Temporary, Integer> temporaryVars,
    int offsetVar,
    Label noTransitionFound,
    boolean printDebugInfo
  ) {

    // Commands that occur for _all_ transitions
    final Set<TagCommand> sharedCommands = skipCommands
      ? Collections.emptySet()
      : transitionsMap
          .values()
          .stream()
          .map(TDFA.TaggedTransition::commands)
          .<Set<TagCommand>>map(HashSet::new)
          .reduce((s1, s2) -> { s1.retainAll(s2); return s1; })
          .orElse(Collections.emptySet());

    record TargetLabel(Label stateLabel, List<TagCommand> commands) { }

    // Merge transitions based on their target labels
    final Map<TargetLabel, IntRangeSet> transitions = transitionsMap
      .entrySet()
      .stream()
      .collect(
        Collectors.groupingBy(
          (Map.Entry<CodeUnitTransition, TDFA.TaggedTransition> entry) -> {
            final var lbl = stateLabels.get(entry.getValue().targetState());
            final List<TagCommand> commands = skipCommands
              ? Collections.emptyList()
              : entry
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

    // Contains entries from ranges that are SMALL_RANGE_SIZE or less elements
    final var shortRanges = new TreeMap<Integer, TargetLabel>();

    // Contains all (sorted) ranges keyed based on their label
    final var longRanges = new HashMap<TargetLabel, List<IntRange>>();

    // Split all ranges into `shortRanges` or `longRanges`
    for (Map.Entry<TargetLabel, IntRangeSet> entry : transitions.entrySet()) {
      final TargetLabel target = entry.getKey();
      final var ranges = new ArrayList<IntRange>();

      for (IntRange codeUnitRange : entry.getValue().ranges()) {
        if (codeUnitRange.size() <= SMALL_RANGE_SIZE) {
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
    for (TagCommand command : sharedCommands) {
      generateBytecodeForCommand(
        mv,
        command,
        offsetVar,
        groupsVar,
        temporaryVars,
        printDebugInfo
      );
    }

    // Populate the variable if we know we have long ranges coming
    if (!longRanges.isEmpty()) {
      if (!shortRanges.isEmpty()) {
        mv.visitInsn(Opcodes.DUP);
      }
      mv.visitIntInsn(Opcodes.ISTORE, codeUnitTempVar);
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

      makeBranch(
        mv,
        lookupFallthrough,
        codeUnitValues,
        targetStateLabels
      );

      // Place command block
      for (var commandBlock : commandBlocks.entrySet()) {
        mv.visitLabel(commandBlock.getValue());
        for (final var command : commandBlock.getKey().commands()) {
          generateBytecodeForCommand(
            mv,
            command,
            offsetVar,
            groupsVar,
            temporaryVars,
            printDebugInfo
          );
        }
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

        boolean firstIteration = true;
        for (IntRange range : labelRanges.getValue()) {

          // lowerBound - x
          pushConstantInt(mv, range.lowerBound() - 1);
          mv.visitIntInsn(Opcodes.ILOAD, codeUnitTempVar);
          mv.visitInsn(Opcodes.ISUB);

          // x - upperBound
          mv.visitIntInsn(Opcodes.ILOAD, codeUnitTempVar);
          pushConstantInt(mv, range.upperBound() + 1);
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

        // TODO: document the range trick
        final var targetLabel = labelRanges.getKey();
        if (targetLabel.commands().isEmpty()) {
          mv.visitJumpInsn(Opcodes.IFLT, targetLabel.stateLabel());
        } else {
          final var notMatched = new Label();
          mv.visitJumpInsn(Opcodes.IFGE, notMatched);

          // Execute final commands
          for (final var command : targetLabel.commands()) {
            generateBytecodeForCommand(
              mv,
              command,
              offsetVar,
              groupsVar,
              temporaryVars,
              printDebugInfo
            );
          }
          mv.visitJumpInsn(Opcodes.GOTO, targetLabel.stateLabel());

          mv.visitLabel(notMatched);
        }
      }

      mv.visitJumpInsn(Opcodes.GOTO, noTransitionFound);
    }
  }

  /**
   * Generate bytecode equivalent to `lookupswitch`, but possibly more compact.
   *
   * Method bodies are limited in length by the fact the code array must have
   * length fitting in an unsigned 16-bit number. This fact, combined with the
   * knowledge that `lookupswitch` is one of the largets instructions, we have
   * an incentive to detect cases where we can emit equivalent but shorter
   * bytecode.
   *
   * TODO: test for ranges
   * TODO: consider when `tableswitch` is more compact
   *
   * @param mv method visitor
   * @param dflt label to jump to if nothing else matches
   * @param values test values in the switch (sorted in ascending order)
   * @param labels labels to jump to if the scrutinee is in the test values
   */
  private static void makeBranch(
    MethodVisitor mv,
    Label dflt,
    int[] values,
    Label[] labels
  ) {
    if (values.length == 0) {
      mv.visitInsn(Opcodes.POP);
      mv.visitJumpInsn(Opcodes.GOTO, dflt);
    } else if (values.length == 1) {
      if (values[0] == 0) {
        mv.visitJumpInsn(Opcodes.IFEQ, labels[0]);
        mv.visitJumpInsn(Opcodes.GOTO, dflt);
      } else {
        pushConstantInt(mv, values[0]);
        mv.visitJumpInsn(Opcodes.IF_ICMPEQ, labels[0]);
        mv.visitJumpInsn(Opcodes.GOTO, dflt);
      }
    } else {
      mv.visitLookupSwitchInsn(dflt, values, labels);
    }
  }

  /**
   * Push an integer constant onto the stack.
   *
   * This chooses the instruction(s) that use the least bytecode space.
   *
   * @param mv method visitor
   * @param constant integer constant
   */
  private static void pushConstantInt(
    MethodVisitor mv,
    int constant
  ) {
    switch (constant) {
      case -1:
        mv.visitInsn(Opcodes.ICONST_M1);
        return;

      case 0:
        mv.visitInsn(Opcodes.ICONST_0);
        return;

      case 1:
        mv.visitInsn(Opcodes.ICONST_1);
        return;

      case 2:
        mv.visitInsn(Opcodes.ICONST_2);
        return;

      case 3:
        mv.visitInsn(Opcodes.ICONST_3);
        return;

      case 4:
        mv.visitInsn(Opcodes.ICONST_4);
        return;

      case 5:
        mv.visitInsn(Opcodes.ICONST_5);
        return;
    }

    if (Byte.MIN_VALUE <= constant && constant <= Byte.MAX_VALUE) {
      mv.visitIntInsn(Opcodes.BIPUSH, constant);
    } else if (Short.MIN_VALUE <= constant && constant <= Short.MAX_VALUE) {
      mv.visitIntInsn(Opcodes.SIPUSH, constant);
    } else {
      mv.visitLdcInsn(Integer.valueOf(constant));
    }
  }
}
