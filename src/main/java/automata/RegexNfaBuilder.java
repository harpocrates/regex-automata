package automata;

import java.util.OptionalInt;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Regex AST visitor which can be used to build up the corresponding NFA.
 *
 * The choice of how to represent the NFA (eg. as an object graph or as a map
 * of states to transitions) is left abstract. The visitor's output is a
 * function from the desired target state to the initial state - it is
 * necessary to emit a function since some regex AST node (eg. like
 * repetition) may need to be re-materialized multiple times in the same NFA.
 *
 * @param <Q> states in the automata
 */
abstract class RegexNfaBuilder<Q> extends CodePointSetVisitor implements RegexVisitor<UnaryOperator<Q>, IntRangeSet> {

  /**
   * Summon a fresh state identifier.
   *
   * @return fresh state ID
   */
  abstract Q freshState();

  /**
   * Register a new state which has plus and minus empty transitions out of it.
   *
   * @param state state to add
   * @param plusTo state at the other end of the "plus" transition
   * @param plusTo state at the other end of the "minus" transition
   */
  abstract void addPlusMinusState(Q state, Q plusTo, Q minusTo);

  /**
   * Register a new state which has a code unit transition out of it.
   *
   * @param state state to add
   * @param codeUnits set of valid code units
   * @param to state at the other end of the code unit transition
   */
  abstract void addCodeUnitsState(Q state, IntRangeSet codeUnits, Q to);

  /**
   * Register a new state with an empty start/end group transition out of it.
   *
   * @param state state to add
   * @param groupIdx unique identifier for the group
   * @param isStart is this the start or end of the group?
   * @param to state at the other end of the group transition
   */
  abstract void addGroupState(Q state, int groupIdx, boolean isStart, Q to);

  /**
   * Register a new state with an empty boundary transition out of it.
   *
   * @param state state to add
   * @param boundary zero-width check
   * @param to state at the other end of the group transition
   */
  abstract void addBoundaryState(Q state, RegexVisitor.Boundary boundary, Q to);

  public UnaryOperator<Q> visitEpsilon() {
    return UnaryOperator.identity();
  }

  public UnaryOperator<Q> visitCharacterClass(IntRangeSet codePointSet) {
    if (!codePointSet.difference(IntRangeSet.of(CodePointSetVisitor.BMP_RANGE)).isEmpty()) {
      throw new IllegalArgumentException("Codepoints outside the BMP aren't supported yet");
    }

    return (Q to) -> {
      final Q start = freshState();
      addCodeUnitsState(start, codePointSet, to);
      return start;
    };
  }

  public UnaryOperator<Q> visitConcatenation(UnaryOperator<Q> lhs, UnaryOperator<Q> rhs) {
    return (Q to) -> {
      final Q mid = rhs.apply(to);
      return lhs.apply(mid);
    };
  }

  public UnaryOperator<Q> visitAlternation(UnaryOperator<Q> lhs, UnaryOperator<Q> rhs) {
    return (Q to) -> {
      final Q lhsFrom = lhs.apply(to);
      final Q rhsFrom = rhs.apply(to);
      final Q from = freshState();
      addPlusMinusState(from, lhsFrom, rhsFrom);
      return from;
    };
  }

  public UnaryOperator<Q> visitKleene(UnaryOperator<Q> lhs, boolean isLazy) {
    return (Q to) -> {
      final Q lhsTo = freshState();
      final Q from = freshState();
      final Q lhsFrom = lhs.apply(lhsTo);
      if (isLazy) {
        addPlusMinusState(lhsTo, to, lhsFrom);
        addPlusMinusState(from, to, lhsFrom);
      } else {
        addPlusMinusState(lhsTo, lhsFrom, to);
        addPlusMinusState(from, lhsFrom, to);
      }
      return from;
    };
  }

  public UnaryOperator<Q> visitOptional(UnaryOperator<Q> lhs, boolean isLazy) {
    return (Q to) -> {
      final Q from = freshState();
      final Q lhsFrom = lhs.apply(to);
      if (isLazy) {
        addPlusMinusState(from, to, lhsFrom);
      } else {
        addPlusMinusState(from, lhsFrom, to);
      }
      return from;
    };
  }

  public UnaryOperator<Q> visitPlus(UnaryOperator<Q> lhs, boolean isLazy) {
    return (Q to) -> {
      final Q lhsTo = freshState();
      final Q lhsFrom = lhs.apply(lhsTo);
      if (isLazy) {
        addPlusMinusState(lhsTo, to, lhsFrom);
      } else {
        addPlusMinusState(lhsTo, lhsFrom, to);
      }
      return lhsFrom;
    };
  }

  public UnaryOperator<Q> visitRepetition(UnaryOperator<Q> lhs, int atLeast, OptionalInt atMost, boolean isLazy) {
    return (Q to) -> {
      // `atMost` portion - this is either a fixed repetition or a kleene star
      if (atMost.isPresent()) {
        final int atMostInt = atMost.getAsInt();
        for (int i = atLeast; i < atMostInt; i++) {
          final Q from = lhs.apply(to);
          final Q fork = freshState();
          if (isLazy) {
            addPlusMinusState(fork, to, from);
          } else {
            addPlusMinusState(fork, from, to);
          }
          to = fork;
        }
      } else {
        to = visitKleene(lhs, isLazy).apply(to);
      }

      // `atLeast` portion
      for (int i = 0; i < atLeast; i++) {
        to = lhs.apply(to);
      }

      return to;
    };
  }

  public UnaryOperator<Q> visitGroup(UnaryOperator<Q> arg, OptionalInt groupIndex) {
    if (groupIndex.isPresent()) {
      final int idx = groupIndex.getAsInt();
      return (Q to) -> {
        final Q argTo = freshState();
        final Q from = freshState();
        final Q argFrom = arg.apply(argTo);
        addGroupState(argTo, idx, true, to);
        addGroupState(from, idx, false, argFrom);
        return from;
      };
    } else {
      return arg;
    }
  }

  public UnaryOperator<Q> visitBoundary(Boundary boundary) {
    return (Q to) -> {
      final Q from = freshState();
      addBoundaryState(from, boundary, to);
      return from;
    };
  }
}
