package automata.parser;

import automata.util.IntRangeSet;
import java.util.Optional;

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
   * @param flags bitmask of regular expression flags
   */
  C visitCharacter(int codePoint, int flags);

  /**
   * Matches a range of abstract characters.
   *
   * @param startCodePoint first code point in the range (inclusive)
   * @param endCodePoint last code point in the range (inclusive)
   * @param flags bitmask of regular expression flags
   */
  C visitRange(int startCodePoint, int endCodePoint, int flags);

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
   * @param flags bitmask of regular expression flags
   */
  C visitBuiltinClass(BuiltinClass cls, int flags);

  /**
   * Matches characters inside a specified unicode block.
   *
   * @param block unicode block
   * @param negated match characters outside the block
   * @param flags bitmask of regular expression flags
   */
  C visitUnicodeBlock(Character.UnicodeBlock block, boolean negated, int flags);

  /**
   * Matches characters inside a specified unicode script.
   *
   * @param script unicode script
   * @param negated match characters outside the script
   * @param flags bitmask of regular expression flags
   */
  C visitUnicodeScript(Character.UnicodeScript script, boolean negated, int flags);

  /**
   * Matches characters in a property class.
   *
   * @param propertyClass property class
   * @param flags bitmask of regular expression flags
   */
  C visitPropertyClass(PropertyClass propertyClass, boolean negated, int flags);

  /**
   * Matches any character inside the set of code points.
   *
   * @param codePointSet set of accepted code points
   * @return nothing if the code point set is empty
   */
  default Optional<C> visitCodePointSet(IntRangeSet codePoints) {
    return codePoints
      .ranges()
      .stream()
      .<C>map(range -> visitRange(range.lowerBound(), range.upperBound(), 0))
      .reduce((l, r) -> visitUnion(l, r));
  }
}

