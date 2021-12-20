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
 *   - put all the M1, M2, M3, M4 stuff in that package
 *   - add utilities for generating `.class` files (eg. `Files.write(Paths.get(className), classBytes)`)
 *   - optimize the case where there are no groups to skip M4
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
  private static final Method CHECKMATCH_M = new Method("checkMatch", methodType(boolean.class, CharSequence.class), Opcodes.INVOKEINTERFACE);
  private static final Method CAPTUREMATCH_M = new Method("captureMatch", methodType(ArrayMatchResult.class, CharSequence.class), Opcodes.INVOKEINTERFACE);
  private static final Method CHECKMATCHSTATIC_M = new Method("checkMatchStatic", methodType(boolean.class, CharSequence.class), Opcodes.INVOKESTATIC);
  private static final Method CAPTUREMATCHPATH_M = new Method("captureMatchPath", methodType(int[].class, CharSequence.class), Opcodes.INVOKESTATIC);
  private static final Method CAPTUREMATCHGROUPS_M = new Method("captureMatchGroups", methodType(int[].class, int[].class), Opcodes.INVOKESTATIC);
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
   * @param <Q3> state type for M3 automata
   * @param <Q4> state type for M4 automata
   * @param m3 M3 DFA
   * @param m4 M4 DFA
   * @param groupCount how many groups are captured? (Must start at 0 and be consequtive)
   * @param className name of the anonymous class to generate
   * @param classFlags class flags to set (visibility, `final`, `synthetic` etc.)
   * @param printDebugInfo print debug info and generate code which prints debug info to STDERR
   * @return class implementing `DfaPattern`
   */
  public static final <Q3, Q4> ClassWriter generateDfaPatternSubclass(
    String pattern,
    Dfa<Q3, Character, ?> m3,
    Dfa<Q4, Q3, Iterable<RegexMarker>> m4,
    int groupCount,
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

    /* Map Q3 states into integer indices
     *
     * The optional output of M3 is an array tracking the path taken through
     * the Q3 states. The obvious efficient way to represent each step of this
     * path at runtime is to associate each Q3 state to a unique integer.
     *
     * We refer to this at the "state ID" of the Q3 state.
     */
    final Map<Q3, Integer> q3StateIds = m3
      .allStates()
      .stream()
      .collect(Collectors.toUnmodifiableMap(
        Function.<Q3>identity(),
        new Function<Q3, Integer>() {
          int m3NextState = 0;
          public Integer apply(Q3 q3) {
            return m3NextState++;
          }
        }
      ));

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

    // `checkMatchStatic` static helper method
    {
      final var mv = CHECKMATCHSTATIC_M.newMethod(cw, Opcodes.ACC_PRIVATE);
      mv.visitCode();
      generateBytecodeForM3Automata(mv, m3, null, printDebugInfo);
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

    // `captureMatchPath` static helper method
    {
      final var mv = CAPTUREMATCHPATH_M.newMethod(cw, Opcodes.ACC_PRIVATE);
      mv.visitCode();
      generateBytecodeForM3Automata(mv, m3, q3StateIds, printDebugInfo);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // `captureMatchGroups` static helper method
    {
      final var mv = CAPTUREMATCHGROUPS_M.newMethod(cw, Opcodes.ACC_PRIVATE);
      mv.visitCode();
      generateBytecodeForM4Automata(mv, m4, groupCount, q3StateIds, printDebugInfo);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    // `captureMatch` method
    {
      final var mv = CAPTUREMATCH_M.newMethod(cw, Opcodes.ACC_PUBLIC);
      mv.visitCode();

      final Label failedToMatch = new Label();

      // get the match path...
      mv.visitVarInsn(Opcodes.ALOAD, 1);
      CAPTUREMATCHPATH_M.invokeMethod(mv, className);
      mv.visitInsn(Opcodes.DUP);
      mv.visitJumpInsn(Opcodes.IFNULL, failedToMatch);

      // get the groups
      CAPTUREMATCHGROUPS_M.invokeMethod(mv, className);
      mv.visitInsn(Opcodes.DUP);
      mv.visitJumpInsn(Opcodes.IFNULL, failedToMatch);
      mv.visitVarInsn(Opcodes.ASTORE, 2);

      // Construct the match result
      mv.visitTypeInsn(Opcodes.NEW, ARRAYMATCHRESULT_CLASS_NAME);
      mv.visitInsn(Opcodes.DUP);
      mv.visitVarInsn(Opcodes.ALOAD, 1);
      TOSTRING_M.invokeMethod(mv, OBJECT_CLASS_NAME);
      mv.visitVarInsn(Opcodes.ALOAD, 2);
      mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        ARRAYMATCHRESULT_CLASS_NAME,
        "<init>",
        "(Ljava/lang/String;[I)V",
        false
      );
      mv.visitInsn(Opcodes.ARETURN);

      // Failure exit case
      mv.visitLabel(failedToMatch);
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);

      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    cw.visitEnd();
    return cw;
  }

  /**
   * Generate the method body associated with simulating an M3 automata.
   *
   * If `m3StateIds` is `null`, this produces a method that tracks nothing but
   * offset in the string and just returns a boolean indicating match success
   * or failure.
   *
   * If `m3StateIds` is not `null`, this produces a method that will track an
   * array of all of the states seen and then return this array (or `null` if
   * the the automata doesn't end at a terminal state).
   *
   * @param <Q3> state type for M3 automata
   * @param mv method visitor (only argument is the input `CharSequence`)
   * @param m3 M3 DFA
   * @param m3StateIds state IDs - return a bool if null or an array of IDs if not null
   * @param printDebugInfo print debug info and generate code which prints debug info to STDERR
   */
  private static <Q3> void generateBytecodeForM3Automata(
    MethodVisitor mv,
    Dfa<Q3, Character, ?> m3,
    Map<Q3, Integer> m3StateIds,
    boolean printDebugInfo
  ) {
    final int inputVar = 0;  // Input argument: `CharSequence input`
    final int offsetVar = 1; // Local tracking (descending) offset in string: `int offset`
    final int statesVar = 2; // Local tracking states seen: `int[] seen`
    final int statesOffsetVar = 3; // Local tracking (ascending) offset in states: `int statesOffset`

    final Set<Q3> terminals = m3.accepting();

    final Label returnFailure = new Label();
    final Label returnSuccess = new Label();
    final Map<Q3, Label> q3States = m3
      .allStates()
      .stream()
      .collect(Collectors.toMap(
        Function.<Q3>identity(),
        (Q3 q3) -> new Label()
      ));

    // Track entry into M3
    if (printDebugInfo) {
      final var stateIds = (m3StateIds == null) ? "" : " (state IDs " + m3StateIds + ")";
      genPrintErrConstant(mv, "[M3] starting run" + stateIds + " on: ", false);
      mv.visitVarInsn(Opcodes.ALOAD, inputVar);
      TOSTRING_M.invokeMethod(mv, OBJECT_CLASS_NAME);
      genPrintErrString(mv);
    }

    // Variable for offset in the string (starts at `str.length` and decrements to 0)
    mv.visitVarInsn(Opcodes.ALOAD, inputVar);
    LENGTH_M.invokeMethod(mv, CHARSEQUENCE_CLASS_NAME);
    mv.visitVarInsn(Opcodes.ISTORE, offsetVar);

    if (m3StateIds != null) {
      // Variable for tracking states seen to far
      mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
      mv.visitInsn(Opcodes.ICONST_1);
      mv.visitInsn(Opcodes.IADD);
      mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
      mv.visitVarInsn(Opcodes.ASTORE, statesVar);

      // Variable for offset in `seen` states (starts as 0 and increments to `str.length`)
      mv.visitInsn(Opcodes.ICONST_0);
      mv.visitVarInsn(Opcodes.ISTORE, statesOffsetVar);
    }

    // Jump to the first state
    mv.visitJumpInsn(Opcodes.GOTO, q3States.get(m3.initial()));

    // Lay out the blocks for each state
    for (Map.Entry<Q3, Label> entry : q3States.entrySet()) {
      mv.visitLabel(entry.getValue());
      final Q3 state = entry.getKey();

      if (printDebugInfo) {
        final var stateId = (m3StateIds == null) ? "" : " (ID: " + m3StateIds.get(state) + ")";
        genPrintErrConstant(mv, "[M3] entering " + state + stateId, true);
      }

      // Store the state
      if (m3StateIds != null) {
        int stateId = m3StateIds.get(state);
        assert stateId <= Short.MAX_VALUE : " state ID overflow";

        mv.visitVarInsn(Opcodes.ALOAD, statesVar);
        mv.visitVarInsn(Opcodes.ILOAD, statesOffsetVar);
        pushConstantInt(mv, stateId);
        mv.visitInsn(Opcodes.IASTORE);

        // Increment the state offset (no need to worry about overflow - `offsetVar` tracks that)
        mv.visitIincInsn(statesOffsetVar, 1);
      }

      // decrement the offset and, if it becomes neagtive, return whether we are in terminal state
      mv.visitIincInsn(offsetVar, -1);
      mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
      mv.visitJumpInsn(Opcodes.IFLT, terminals.contains(state) ? returnSuccess : returnFailure);

      // get the next character
      mv.visitVarInsn(Opcodes.ALOAD, inputVar);
      mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
      CHARAT_M.invokeMethod(mv, CHARSEQUENCE_CLASS_NAME);

      final var transitions = new TreeMap<Character, Dfa.Transition<Q3, ?>>(m3.transitionsMap(state));
      final int[] charValues = transitions
        .keySet()
        .stream()
        .mapToInt((c) -> c.charValue())
        .toArray();
      final Label[] stateLabels = transitions
        .values()
        .stream()
        .map((transition) -> q3States.get(transition.targetState()))
        .toArray(Label[]::new);
      makeBranch(
        mv,
        returnFailure, // no transition found means no match
        charValues,
        stateLabels
      );
    }

    // Returning unsuccessfully
    mv.visitLabel(returnFailure);
    if (printDebugInfo) {
      final var returned = (m3StateIds == null) ? "0" : "null";
      genPrintErrConstant(mv, "[M3] exiting run (unsuccessful): " + returned, true);
    }
    if (m3StateIds == null) {
      mv.visitInsn(Opcodes.ICONST_0);
      mv.visitInsn(Opcodes.IRETURN);
    } else {
      mv.visitInsn(Opcodes.ACONST_NULL);
      mv.visitInsn(Opcodes.ARETURN);
    }

    // Returning successfully
    mv.visitLabel(returnSuccess);
    if (printDebugInfo) {
      genPrintErrConstant(mv, "[M3] exiting run (successful): ", false);
      if (m3StateIds != null) {
        mv.visitVarInsn(Opcodes.ALOAD, statesVar);
        INTARRTOSTRING_M.invokeMethod(mv, ARRAYS_CLASS_NAME);
        genPrintErrString(mv);
      } else {
        genPrintErrConstant(mv, "1", true);
      }
    }
    if (m3StateIds == null) {
      mv.visitInsn(Opcodes.ICONST_1);
      mv.visitInsn(Opcodes.IRETURN);
    } else {
      mv.visitVarInsn(Opcodes.ALOAD, statesVar);
      mv.visitInsn(Opcodes.ARETURN);
    }

    if (printDebugInfo) {
      final var stateOffsets = q3States
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getOffset()));
      System.err.println("[M3 compilation] state offsets: " + stateOffsets);
    }
  }

  /**
   * Generate the method body associated with simulating an M4 automata.
   *
   * This method takes as input path through M3 and uses that path to walk the
   * M4 DFA here. Along the way, whenever there is a group transition, code
   * gets synthesized to write in that group into an array for tracking capture
   * groups. The method returns the group array if the match succeeds or `null`
   * otherwise.
   *
   * @param <Q3> state type for M3 automata
   * @param <Q4> state type for M4 automata
   * @param mv method visitor (only argument is the input `CharSequence`)
   * @param m4 M4 DFA
   * @param groupCount how many groups are captured? (Must start at 0 and be consequtive)
   * @param m3StateIds state IDs in M3
   * @param printDebugInfo print debug info and generate code which prints debug info to STDERR
   */
  private static <Q3, Q4> void generateBytecodeForM4Automata(
    MethodVisitor mv,
    Dfa<Q4, Q3, Iterable<RegexMarker>> m4,
    int groupCount,
    Map<Q3, Integer> m3StateIds,
    boolean printDebugInfo
  ) {
    final int m3PathVar = 0; // Input argument: `int[] m3Path`
    final int offsetVar = 1; // Local tracking (descending) offset in path: `int offset`
    final int strOffsetVar = 2; // Local tracking (ascending) offset in string: `int strOffset`
    final int groupsVar = 3; // Local tracking capture groups: `int[] groups`

    final Set<Q4> terminals = m4.accepting();

    final Label returnFailure = new Label(); // TODO: is failure here possible!?
    final Label returnSuccess = new Label();
    final Map<Q4, Label> q4States = m4
      .allStates()
      .stream()
      .collect(Collectors.toMap(
        Function.<Q4>identity(),
        (Q4 q4) -> new Label()
      ));

    // Track entry into M3
    if (printDebugInfo) {
      genPrintErrConstant(mv, "[M4] starting run (state IDs " + m3StateIds + ") on: ", false);
      mv.visitVarInsn(Opcodes.ALOAD, m3PathVar);
      INTARRTOSTRING_M.invokeMethod(mv, ARRAYS_CLASS_NAME);
      genPrintErrString(mv);
    }

    // Initialize the `groups` array to the right length, filled with `-1`
    final int groupsArrLen = groupCount * 2;
    pushConstantInt(mv, groupsArrLen);
    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
    mv.visitInsn(Opcodes.DUP);
    mv.visitVarInsn(Opcodes.ASTORE, groupsVar);
    mv.visitInsn(Opcodes.ICONST_M1);
    FILLINT_M.invokeMethod(mv, ARRAYS_CLASS_NAME);

    // Initialize `offset`
    mv.visitIntInsn(Opcodes.ALOAD, m3PathVar);
    mv.visitInsn(Opcodes.ARRAYLENGTH);
    mv.visitIntInsn(Opcodes.ISTORE, offsetVar);

    // Initialize `strOffset`
    mv.visitInsn(Opcodes.ICONST_M1);
    mv.visitIntInsn(Opcodes.ISTORE, strOffsetVar);

    // Jump to the first state
    mv.visitJumpInsn(Opcodes.GOTO, q4States.get(m4.initial()));

    // Lay out the blocks for each state
    for (Map.Entry<Q4, Label> entry : q4States.entrySet()) {
      mv.visitLabel(entry.getValue());
      final Q4 state = entry.getKey();

      if (printDebugInfo) {
        genPrintErrConstant(mv, "[M4] entering " + state, true);
      }

      // decrement the offset and, if it becomes neagtive, return whether we are in terminal state
      mv.visitIincInsn(offsetVar, -1);
      mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
      mv.visitJumpInsn(Opcodes.IFLT, terminals.contains(state) ? returnSuccess : returnFailure);

      // Increment the string offset (don't worry about overflow - `offsetVar` covers that)
      mv.visitIincInsn(strOffsetVar, 1);

      // get the next M3 path
      mv.visitVarInsn(Opcodes.ALOAD, m3PathVar);
      mv.visitVarInsn(Opcodes.ILOAD, offsetVar);
      mv.visitInsn(Opcodes.IALOAD);

      /* Determining the table switch here is tricky:
       *
       *   - first we group transitions based on common target state and regex
       *     marker sequence
       *
       *   - for each of these groups with a non-empty marker sequence, we
       *     generate a fresh label (this is where the code processing the
       *     "regex marker" will live).
       *
       *   - in the lookup switch, we redirect every element to its label.
       *     Elements whose group had an empty sequence jump straight to their
       *     the next state
       */
      final Map<SimpleEntry<Q4, List<RegexMarker>>, Label> groupedMarkerTransitions =
        new HashMap<SimpleEntry<Q4, List<RegexMarker>>, Label>();
      final SortedMap<Integer, Label> q3StateIdTargets =
        new TreeMap<Integer, Label>();
      for (var transitionEntry : m4.transitionsMap(state).entrySet()) {
        final Q4 q4Target = transitionEntry.getValue().targetState();
        final Q3 q3State = transitionEntry.getKey();
        final List<RegexMarker> markersList = StreamSupport
          .stream(transitionEntry.getValue().annotation().spliterator(), false)
          .collect(Collectors.toList());

        if (markersList.isEmpty()) {
          // Map the Q3 ID to the label of the next Q4 state
          q3StateIdTargets.put(m3StateIds.get(q3State), q4States.get(q4Target));
        } else {
          // Lookup (or create if missing) the group and add the Q3 transition to it
          final var groupLabel = groupedMarkerTransitions.computeIfAbsent(
            new SimpleEntry<>(q4Target, markersList),
            (k) -> new Label()
          );

          // Map the Q3 ID to the processing block
          q3StateIdTargets.put(m3StateIds.get(q3State), groupLabel);
        }
      }

      // Jump to a marker processing block or the next state based on the next Q3 ID
      final int[] q3StateIdValues = q3StateIdTargets
        .keySet()
        .stream()
        .mapToInt((c) -> c.intValue())
        .toArray();
      final Label[] stateLabels = q3StateIdTargets
        .values()
        .stream()
        .toArray(Label[]::new);
      makeBranch(
        mv,
        returnFailure, // no transition found means no match
        q3StateIdValues,
        stateLabels
      );


      // Place all of the marker processing blocks
      for (var grouped : groupedMarkerTransitions.entrySet()) {
        final Label groupLabel = grouped.getValue();
        final Q4 nextState = grouped.getKey().getKey();
        final List<RegexMarker> markers = grouped.getKey().getValue();

        mv.visitLabel(groupLabel);
        for (RegexMarker marker : markers) {
          int groupOff = -1;
          if (marker instanceof RegexMarker.GroupStart) {
            groupOff = ((RegexMarker.GroupStart) marker).groupIndex() * 2;
          } else if (marker instanceof RegexMarker.GroupEnd) {
            groupOff = ((RegexMarker.GroupEnd) marker).groupIndex() * 2 + 1;
          } else {
            throw new RuntimeException("Unhandled marker " + marker);
          }

          if (printDebugInfo) {
            final var message = "[M4] capturing " + marker + ": groups[" + groupOff + "] = ";
            genPrintErrConstant(mv, message, false);
            mv.visitVarInsn(Opcodes.ILOAD, strOffsetVar);
            genPrintErrInt(mv);
          }

          // TODO minimize
          mv.visitVarInsn(Opcodes.ALOAD, groupsVar);
          pushConstantInt(mv, groupOff);
          mv.visitVarInsn(Opcodes.ILOAD, strOffsetVar);
          mv.visitInsn(Opcodes.IASTORE);
        }

        mv.visitJumpInsn(Opcodes.GOTO, q4States.get(nextState));
      }
    }

    // Returning unsuccessfully
    mv.visitLabel(returnFailure);
    if (printDebugInfo) {
      genPrintErrConstant(mv, "[M4] exiting run (unsuccessful): null", true);
    }
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.ARETURN);

    // Returning successfully
    mv.visitLabel(returnSuccess);
    if (printDebugInfo) {
      genPrintErrConstant(mv, "[M4] exiting run (successful): ", false);
      mv.visitVarInsn(Opcodes.ALOAD, groupsVar);
      INTARRTOSTRING_M.invokeMethod(mv, ARRAYS_CLASS_NAME);
      genPrintErrString(mv);
    }
    mv.visitVarInsn(Opcodes.ALOAD, groupsVar);
    mv.visitInsn(Opcodes.ARETURN);
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
