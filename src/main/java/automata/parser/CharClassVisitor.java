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
   * An abstract character is represented by its unicode code point. Note that
   * this is not equivalent to a Java `char`, which is instead a code unit (and
   * where unicode code points get encoded as one or two code units in UTF-16).
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
   * Note that "union" in a character class has no symbolic operator - classes
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
}

