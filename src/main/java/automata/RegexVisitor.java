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
   * Matches a pattern at least a certain number of times and possible at most
   * another number of times.
   *
   * @param lhs pattern to match
   * @param atLeast minimum (inclusive) of times the pattern must match
   * @param atMost maximum (inclusive) of time the pattern must match
   * @param isLazy whether to prioritize a shorter vs. longer match
   */
  O visitRepetition(I lhs, int atLeast, OptionalInt atMost, boolean isLazy);

  /**
   * Matches a parenthesized pattern.
   *
   * @param arg parenthesized body
   * @param groupIndex if set, the group is capturing and this is the capture index
   */
  O visitGroup(I arg, OptionalInt groupIndex);

  /**
   * Matches a (zero-width) boundary pattern
   *
   * @param boundary which boundary to match
   */
  O visitBoundary(Boundary boundary);

  /**
   * Zero-width boundary matchers.
   *
   * TODO: support more of these
   */
  enum Boundary {
    /**
     * Beginning of input
     */
    BEGINNING,

    /**
     * End of input
     */
    END,

    /**
     * Word boundary
     */
    WORD_BOUNDARY
  }
}
