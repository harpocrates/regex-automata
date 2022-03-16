package automata.parser;

/**
 * Bottom-up traversal of the character class AST.
 *
 * @param <C> output from traversing the pattern AST
 */
public interface CharClassVisitor<C> {

  /**
   * Matches a single abstract character.
   *
   * <p>An abstract character is represented by its unicode code point. Note
   * that this is not equivalent to a Java {@code char}, which is instead a
   * code unit (and where unicode code points get encoded as one or two UTF-16
   * code units).
   *
   * @param codePoint unicode code point to match
   */
  C visitCharacter(int codePoint);

  /**
   * Matches a range of abstract characters.
   *
   * @param startCodePoint first code point in the range (inclusive)
   * @param endCodePoint last code point in the range (inclusive)
   */
  C visitRange(int startCodePoint, int endCodePoint);

  /**
   * Matches all characters that don't match another pattern.
   *
   * @param negate negated pattern
   */
  C visitNegated(C negate);

  /**
   * Matches characters in either class.
   *
   * <p>The "union" in a character class has no symbolic operator - classes
   * are implicitly unioned. Unlike a regular expression union, this _is_
   * symmetric.
   *
   * @param lhs first class of characters
   * @param rhs second class of characters
   */
  C visitUnion(C lhs, C rhs);

  /**
   * Matches characters in both classes.
   *
   * @param lhs first class of characters
   * @param rhs second class of characters
   */
  C visitIntersection(C lhs, C rhs);

  /**
   * Matches characters inside a builtin character class.
   *
   * @param cls builtin class
   */
  C visitBuiltinClass(BuiltinClass cls);

  /**
   * Matches characters inside a specified unicode block.
   *
   * @param block unicode block
   * @param negated match characters outside the block
   */
  C visitUnicodeBlock(Character.UnicodeBlock block, boolean negated);

  /**
   * Matches characters inside a specified unicode script.
   *
   * @param script unicode script
   * @param negated match characters outside the script
   */
  C visitUnicodeScript(Character.UnicodeScript script, boolean negated);
}

