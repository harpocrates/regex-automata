package automata.codegen;

import automata.ArrayMatchResult;
import automata.DfaMatcher;
import automata.DfaPattern;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Helper class to simplify codegen around declaring and calling methods.
 *
 * @author Alec Theriault
 * @param name name of the method
 * @param typ type of the method (does not include the receiver)
 * @param invokeSort one of the {@code Opcodes.INVOKE*} codes
 */
final record Method(
  String name,
  MethodType typ,
  int invokeSort
) {

  // Class name constants
  public static final String DFAPATTERN_CLASS_NAME = Type.getInternalName(DfaPattern.class);
  public static final String DFAMATCHER_CLASS_NAME = Type.getInternalName(DfaMatcher.class);
  public static final String OBJECT_CLASS_NAME = Type.getInternalName(Object.class);
  public static final String ARRAYMATCHRESULT_CLASS_NAME = Type.getInternalName(ArrayMatchResult.class);
  public static final String SYSTEM_CLASS_NAME = Type.getInternalName(System.class);
  public static final String PRINTSTREAM_CLASS_NAME = Type.getInternalName(java.io.PrintStream.class);
  public static final String ARRAYS_CLASS_NAME = Type.getInternalName(Arrays.class);
  public static final String CHARSEQUENCE_CLASS_NAME = Type.getInternalName(CharSequence.class);

  // Method name constants
  public static final Method EMPTYINIT_M = new Method(
    "<init>",
    MethodType.methodType(void.class),
    Opcodes.INVOKESPECIAL
  );
  public static final Method DFAMATCHERINIT_M = new Method(
    "<init>",
    MethodType.methodType(void.class, DfaPattern.class, CharSequence.class, int.class),
    Opcodes.INVOKESPECIAL
  );
  public static final Method COMPILEDDFAMATCHERINIT_M = new Method(
    "<init>",
    MethodType.methodType(void.class, DfaPattern.class, CharSequence.class),
    Opcodes.INVOKESPECIAL
  );
  public static final Method PATTERN_M = new Method(
    "pattern",
    MethodType.methodType(String.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method GROUPCOUNT_M = new Method(
    "groupCount",
    MethodType.methodType(int.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method MATCHES_M = new Method(
    "matches",
    MethodType.methodType(boolean.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method LOOKINGAT_M = new Method(
    "lookingAt",
    MethodType.methodType(boolean.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method FIND_M = new Method(
    "find",
    MethodType.methodType(boolean.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method PREMATCHUPDATE_M = new Method(
    "preMatchUpdate",
    MethodType.methodType(void.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method POSTMATCHUPDATE_M = new Method(
    "postMatchUpdate",
    MethodType.methodType(boolean.class, boolean.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method MATCHESSTATIC_M = new Method(
    "matchesStatic",
    MethodType.methodType(boolean.class, CharSequence.class, int.class, int.class, int[].class),
    Opcodes.INVOKESTATIC
  );
  public static final Method LOOKINGATSTATIC_M = new Method(
    "lookingAtStatic",
    MethodType.methodType(boolean.class, CharSequence.class, int.class, int.class, int[].class),
    Opcodes.INVOKESTATIC
  );
  public static final Method FINDSTATIC_M = new Method(
    "findStatic",
    MethodType.methodType(boolean.class, CharSequence.class, int.class, int.class, int[].class),
    Opcodes.INVOKESTATIC
  );
  public static final Method TOSTRING_M = new Method(
    "toString",
    MethodType.methodType(String.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method PRINTSTR_M = new Method(
    "print",
    MethodType.methodType(void.class, String.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method PRINTLNINT_M = new Method(
    "println",
    MethodType.methodType(void.class, int.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method PRINTLNSTR_M = new Method(
    "println",
    MethodType.methodType(void.class, String.class),
    Opcodes.INVOKEVIRTUAL
  );
  public static final Method INTARRTOSTRING_M = new Method(
    "toString",
    MethodType.methodType(String.class, int[].class),
    Opcodes.INVOKESTATIC
  );
  public static final Method CHARAT_M = new Method(
    "charAt",
    MethodType.methodType(char.class, int.class),
    Opcodes.INVOKEINTERFACE
  );
  public static final Method FILLINT_M = new Method(
    "fill",
    MethodType.methodType(void.class, int[].class, int.class),
    Opcodes.INVOKESTATIC
  );

  /**
   * Start this method on an existing class visitor.
   *
   * @param cv class on which the method is started
   * @param accessFlags access flags for the method (`static` or not is computed)
   * @return method visitor for this method
   */
  public MethodVisitor newMethod(ClassVisitor cv, int accessFlags) {
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
  public void invokeMethod(MethodVisitor mv, String className) {
    mv.visitMethodInsn(
      invokeSort,
      className,
      name,
      typ.descriptorString(),
      invokeSort == Opcodes.INVOKEINTERFACE
    );
  }
}

