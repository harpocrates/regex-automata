package automata;

/**
 * State transition for a set of code units.
 *
 * @param codeUnitSet code units permitted in the transition
 */
public record CodeUnitTransition(IntRangeSet codeUnitSet) implements M1Transition {

  public static CodeUnitTransition ofChar(char c) {
    return new CodeUnitTransition(IntRangeSet.of(IntRange.single(c)));
  }
}
