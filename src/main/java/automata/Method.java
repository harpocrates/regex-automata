package automata;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.lang.invoke.MethodType;

/**
 * Helper class to simplify codegen around declaring and calling methods.
 *
 * @param name name of the method
 * @param typ type of the method (does not include the receiver)
 * @param invokeSort one of the `Opcodes.INVOKE*` codes
 */
final record Method(
  String name,
  MethodType typ,
  int invokeSort
) {

  /**
   * Start this method on an existing class visitor.
   *
   * @param cv class on which the method is started
   * @param accessFlags access flags for the method (`static` or not is computed)
   */
  MethodVisitor newMethod(ClassVisitor cv, int accessFlags) {
    int staticFlag = (invokeSort == Opcodes.INVOKESTATIC) ? Opcodes.ACC_STATIC : 0;
    return cv.visitMethod(
      accessFlags | staticFlag,
      name,
      typ.descriptorString(),
      null, // signature
      null  // exceptions
    );
  }

  /**
   * Invoke this method inside another method body.
   *
   * @param mv method inside of which this method is called
   * @param className name of the class on which this method is defined
   */
  void invokeMethod(MethodVisitor mv, String className) {
    mv.visitMethodInsn(
      invokeSort,
      className,
      name,
      typ.descriptorString(),
      invokeSort == Opcodes.INVOKEINTERFACE
    );
  }
}

