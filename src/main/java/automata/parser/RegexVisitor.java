package automata.parser;

import java.util.OptionalInt;

/**
 * Bottom-up traversal of the regular expression pattern AST.
 *
 * @param <R> output from traversing the regex pattern AST
 * @param <C> output from traversing the character class AST
 */
public interface RegexVisitor<R, C> extends CharClassVisitor<C> {

  /**
   * Empty expression, matching only the empty string.
   */
  R visitEpsilon();

  /**
   * Matches any character inside the character class.
   *
   * @param characterClass character class sub-AST
   */
  R visitCharacterClass(C characterClass);

  /**
   * Matches a concatenation of two patterns.
   *
   * @param lhs first pattern to match
   * @param rhs second pattern to match
   */
  R visitConcatenation(R lhs, R rhs);

  /**
   * Matches a union of two patterns.
   *
   * <p>This is not a symmetric operation; if both sides match but they have
   * different capture-group effects, those of the left-hand side will be the
   * ones taken in the match.
   *
   * @param lhs first pattern to try matching
   * @param rhs second pattern to try matching
   */
  R visitAlternation(R lhs, R rhs);

  /**
   * Matches a pattern zero or more times.
   *
   * @param lhs pattern to match
   * @param isLazy whether to prioritize a shorter vs. longer match
   */
  R visitKleene(R lhs, boolean isLazy);

  /**
   * Matches a pattern zero or one times.
   *
   * @param lhs pattern to match
   * @param isLazy whether to prioritize an empty vs. non-empty match
   */
  R visitOptional(R lhs, boolean isLazy);

  /**
   * Matches a pattern one or more times.
   *
   * @param lhs pattern to match
   * @param isLazy whether to prioritize a shorter vs. longer match
   */
  R visitPlus(R lhs, boolean isLazy);

  /**
   * Matches a pattern at least a certain number of times and possibly at most
   * another number of times.
   *
   * @param lhs pattern to match
   * @param atLeast minimum (inclusive) of times the pattern must match
   * @param atMost maximum (inclusive) of time the pattern must match
   * @param isLazy whether to prioritize a shorter vs. longer match
   */
  R visitRepetition(R lhs, int atLeast, OptionalInt atMost, boolean isLazy);

  /**
   * Matches a parenthesized pattern.
   *
   * @param arg parenthesized body
   * @param groupIndex if set, the group is capturing with this capture index
   */
  R visitGroup(R arg, OptionalInt groupIndex);

  /**
   * Matches a (zero-width) boundary pattern
   *
   * @param boundary which boundary to match
   */
  R visitBoundary(Boundary boundary);
}
