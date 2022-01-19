package automata.parser;

import java.util.Map;

/**
 * Zero-width boundary matchers.
 *
 * TODO: support more of these
 */
public enum Boundary {
  /**
   * Beginning of line
   */
  BEGINNING_OF_LINE,

  /**
   * End of line
   */
  END_OF_LINE,

  /**
   * Word boundary
   */
  WORD_BOUNDARY,

  /**
   * Non-word boundary
   */
  NON_WORD_BOUNDARY,

  /**
   * Beginning of input
   */
  BEGINNING_OF_INPUT,

  /**
   * End of input, safe for an optional final terminator
   */
  END_OF_INPUT_OR_TERMINATOR,

  /**
   * End of input
   */
  END_OF_INPUT;

  /**
   * Mapping from the character used to represent the boundary to the boundary.
   */
  public static Map<Character, Boundary> CHARACTERS = Map.of(
    'b', Boundary.WORD_BOUNDARY,
    'B', Boundary.NON_WORD_BOUNDARY,
    'A', Boundary.BEGINNING_OF_INPUT,
    'Z', Boundary.END_OF_INPUT_OR_TERMINATOR,
    'z', Boundary.END_OF_INPUT
  );
}

