package automata.graph;

/**
 * Method in which a TNFA/TDFA can handle the end of a match.
 *
 * More rigorously: what the TNFA/TDFA does when there is no matching transition
 * for the next code unit of the input.
 *
 * Note that this is not quite a mode that can be toggled at runtime to enable
 * different capabilities. Rather, the automata are constructed with the
 * expectation that they will run in a certain mode (so running in another mode
 * just leads to undefined behaviour).
 */
public enum MatchMode {
  /**
   * Input must be fully consumed.
   *
   * If there is no matching transition at any point, matching fails.
   */
  FULL,

  /**
   * A prefix of the input is consumed, up to either the end of the input or
   * a state where there is no matching transition for the next code unit.
   */
  PREFIX
}

