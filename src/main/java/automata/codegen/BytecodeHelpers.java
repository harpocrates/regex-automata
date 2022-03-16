package automata.codegen;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Superclass containing a bunch of utility methods for emitting bytecode.
 *
 * <p>Some of the utilities could be easily replaced with a single method call
 * on {@code MethodVisitor}, but their benefit is that they emit shorter
 * bytecode. Method bodies are limited in length by the fact the code array must
 * have length fitting in an unsigned 16-bit number. Consequently, we have an
 * incentive to detect places where we can emit equivalent but shorter bytecode.
 *
 * @author Alec Theriault
 */
class BytecodeHelpers {

  // Field and descriptor constants associated with `System.err`
  private static final String SYSTEM_ERR = "err";
  private static final String PRINTSTREAM_DESC = Type.getDescriptor(java.io.PrintStream.class);

  /**
   * Method visitor into which code will be emitted.
   */
  protected final MethodVisitor mv;

  public BytecodeHelpers(MethodVisitor mv) {
    this.mv = mv;
  }

  /**
   * Emit code to print a constant string to "standard" error.
   *
   * @param message constant message to print
   * @param withNewLine whether to print a newline at the end of the message
   */
  protected void visitPrintErrConstantString(String message, boolean withNewline) {
    mv.visitFieldInsn(Opcodes.GETSTATIC, Method.SYSTEM_CLASS_NAME, SYSTEM_ERR, PRINTSTREAM_DESC);
    mv.visitLdcInsn(message);
    final var printM = withNewline ? Method.PRINTLNSTR_M : Method.PRINTSTR_M;
    printM.invokeMethod(mv, Method.PRINTSTREAM_CLASS_NAME);
  }

  /**
   * Emit code to print a {@code String} at the top of the stack to "standard"
   * error (and a newline after it).
   */
  protected void visitPrintErrString() {
    mv.visitFieldInsn(Opcodes.GETSTATIC, Method.SYSTEM_CLASS_NAME, SYSTEM_ERR, PRINTSTREAM_DESC);
    mv.visitInsn(Opcodes.SWAP);
    Method.PRINTLNSTR_M.invokeMethod(mv, Method.PRINTSTREAM_CLASS_NAME);
  }

  /**
   * Emit code to print an {@code int} at the top of the stack to "standard"
   * error (and a newline after it).
   */
  protected void visitPrintErrInt() {
    mv.visitFieldInsn(Opcodes.GETSTATIC, Method.SYSTEM_CLASS_NAME, SYSTEM_ERR, PRINTSTREAM_DESC);
    mv.visitInsn(Opcodes.SWAP);
    Method.PRINTLNINT_M.invokeMethod(mv, Method.PRINTSTREAM_CLASS_NAME);
  }

  /**
   * Generate bytecode equivalent to `lookupswitch`, but possibly more compact.
   *
   * <p>Equivalent to {@code mv.visitLookupSwitchInsn(dflt, values, labels)},
   * but possibly shorter. {@code lookupswitch} in particular is a very long
   * bytecode instruction.
   *
   * @param dflt label to jump to if nothing else matches
   * @param values test values in the switch (sorted in ascending order)
   * @param labels labels to jump to if the scrutinee is in the test values
   */
  protected void visitLookupBranch(
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
        visitConstantInt(values[0]);
        mv.visitJumpInsn(Opcodes.IF_ICMPEQ, labels[0]);
        mv.visitJumpInsn(Opcodes.GOTO, dflt);
      }
    } else {

      // If the range of values is dense, we can use a tableswitch to save space
      boolean useTableSwitch = true;
      for (int i = 0; i < values.length - 1; i ++) {
        if (values[i] + 1 != values[i + 1]) {
          useTableSwitch = false;
          break;
        }
      }

      if (useTableSwitch) {
        mv.visitTableSwitchInsn(values[0], values[values.length - 1], dflt, labels);
      } else {
        mv.visitLookupSwitchInsn(dflt, values, labels);
      }
    }
  }

  /**
   * Push an integer constant onto the stack.
   *
   * <p>Equivalent to {@code mv.visitLdcInsn(constant)}, but possibly shorter
   * and ideally not consuming a slot in the constants table.
   *
   * @param constant integer constant
   */
  protected void visitConstantInt(int constant) {
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
      mv.visitLdcInsn(constant);
    }
  }
}
