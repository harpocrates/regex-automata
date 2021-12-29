package automata;

import java.util.OptionalInt;
import java.util.Map;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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
public abstract class RegexNfaBuilder<Q> extends CodePointSetVisitor implements RegexVisitor<UnaryOperator<Q>, IntRangeSet> {

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

  /**
   * Break down the input code point set into a mapping of low ranges to high
   * ranges.
   *
   * The values in the output should be disjoint and union out to a subset of
   * the high surrogate range. The values in the keys won't necessarily be
   * disjoint, but they should all be in the low surrogate range. Since the
   * high surrogate range is just {@code 0xD800–0xDBFF} (1024 values), the
   * total size of the output map is at most 1024 entries.
   *
   * @param codePointSet input code point set
   * @return mapping from low surrogate ranges to high surrogate ranges
   */
  public static Map<IntRangeSet, IntRangeSet> supplementaryCodeUnitRanges(IntRangeSet codePointSet) {

    // TODO: this doesn't need to be a map since we scan high codepoints in order
    //       and only ever update the last one
    final var supplementaryCodeUnits = new HashMap<Integer, LinkedList<IntRange>>();

    for (IntRange range : codePointSet.difference(IntRangeSet.of(CodePointSetVisitor.BMP_RANGE)).ranges()) {

      int rangeStartHi = Character.highSurrogate(range.lowerBound());
      int rangeStartLo = Character.lowSurrogate(range.lowerBound());

      int rangeEndHi = Character.highSurrogate(range.upperBound());
      int rangeEndLo = Character.lowSurrogate(range.upperBound());

      if (rangeStartHi == rangeEndHi) {
        // Add the _only_ range
        supplementaryCodeUnits
          .computeIfAbsent(rangeStartHi, k -> new LinkedList<>())
          .addLast(IntRange.between(rangeStartLo, rangeEndLo));
      } else {
        // Add the first range
        supplementaryCodeUnits
          .computeIfAbsent(rangeStartHi, k -> new LinkedList<>())
          .addLast(IntRange.between(rangeStartLo, Character.MAX_LOW_SURROGATE));

        // Add the last range
        supplementaryCodeUnits
          .computeIfAbsent(rangeEndHi, k -> new LinkedList<>())
          .addLast(IntRange.between(Character.MIN_LOW_SURROGATE, rangeEndLo));

        // Add everything in between
        for (int hi = rangeStartHi + 1; hi <= rangeEndHi - 1; hi++) {
          supplementaryCodeUnits
            .computeIfAbsent(hi, k -> new LinkedList<>())
            .addLast(CodePointSetVisitor.LOW_SURROGATE_RANGE);
        }
      }
    }

    return supplementaryCodeUnits
      .entrySet()
      .stream()
      .collect(
        Collectors.groupingBy(
          e -> new IntRangeSet(e.getValue()),
          Collectors.mapping(
            e -> IntRangeSet.of(IntRange.single(e.getKey())),
            Collectors.collectingAndThen(Collectors.toList(), IntRangeSet::union)
          )
        )
      );
  }

  public UnaryOperator<Q> visitCharacterClass(IntRangeSet codePointSet) {
    if (!codePointSet.difference(IntRangeSet.of(CodePointSetVisitor.UNICODE_RANGE)).isEmpty()) {
      throw new IllegalArgumentException("Codepoints outside the unicode range aren't allowed");
    }

    /* Code unit transitions corresponding to the basic multilingual plane.
     * By definition of the BMP, this means these are exactly one code unit.
     */
    final var basicCodeUnits = codePointSet.intersection(IntRangeSet.of(CodePointSetVisitor.BMP_RANGE));

    /* Mapping from the first (high) 16-bit code unit to the range of second
     * (low) 16-bit code units. There are `0xDBFF - 0xD800 + 1 = 1024` high
     * code points, so this map will have between 0 and 1024 entries.
     */
    final var supplementaryCodeUnits = supplementaryCodeUnitRanges(codePointSet);

    return (Q to) -> {
      final Q start = freshState();
      if (!basicCodeUnits.isEmpty()) {
        addCodeUnitsState(start, basicCodeUnits, to);
      }

      for (var loAndHigh : supplementaryCodeUnits.entrySet()) {
        final Q hiEnd = freshState();
        addCodeUnitsState(start, loAndHigh.getValue(), hiEnd);
        addCodeUnitsState(hiEnd, loAndHigh.getKey(), to);
      }
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
        addGroupState(argTo, idx, false, to);
        addGroupState(from, idx, true, argFrom);
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
