package automata;

import java.util.OptionalInt;

interface RegexVisitor<I, O> {

  /**
   * Matches nothing.
   */
  O visitEpsilon();

  /**
   * Matches one literal character.
   *
   * @param ch character to match
   */
  O visitCharacter(char ch);

  /**
   * Matches a concatenation of two patterns.
   *
   * @param lhs first pattern to match
   * @param rhs second pattern to match
   */
  O visitConcatenation(I lhs, I rhs);

  /**
   * Matches a union of two patterns.
   *
   * @param lhs first pattern to try matching
   * @param rhs second pattern to try matching
   */
  O visitAlternation(I lhs, I rhs);

  /**
   * Matches a pattern zero or more times.
   *
   * @param lhs pattern to match
   * @param isLazy whether to prioritize a shorter vs. longer match
   */
  O visitKleene(I lhs, boolean isLazy);

  /**
   * Matches a pattern zero or one times.
   *
   * @param lhs pattern to match
   * @param isLazy whether to prioritize an empty vs. nonempty match
   */
  O visitOptional(I lhs, boolean isLazy);

  /**
   * Matches a pattern one or more times.
   *
   * @param lhs pattern to match
   * @param isLazy whether to prioritize a shorter vs. longer match
   */
  O visitPlus(I lhs, boolean isLazy);

  /**
   * Matches a parenthesized pattern.
   *
   * @param arg parenthesized body
   * @param groupIndex if set, the group is capturing and this is the capture index
   */
  O visitGroup(I arg, OptionalInt groupIndex);
}
