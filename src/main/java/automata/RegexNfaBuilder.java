package automata;

import java.util.Optional;
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
public abstract class RegexNfaBuilder<Q>
    extends CodePointSetVisitor
    implements RegexVisitor<UnaryOperator<NfaState<Q>>, IntRangeSet> {

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
   * @param marker group marker
   * @param relativeLocation fixed position relative to another marker
   * @param to state at the other end of the group transition
   */
  abstract void addGroupState(
    Q state,
    GroupMarker marker,
    Optional<RelativeGroupLocation> relativeLocation,
    Q to
  );

  /**
   * Register a new state with an empty boundary transition out of it.
   *
   * @param state state to add
   * @param boundary zero-width check
   * @param to state at the other end of the group transition
   */
  abstract void addBoundaryState(Q state, RegexVisitor.Boundary boundary, Q to);

  public UnaryOperator<NfaState<Q>> visitEpsilon() {
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
  public static Map<IntRangeSet, IntRangeSet> supplementaryCodeUnitRanges(
    IntRangeSet codePointSet
  ) {

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

  public UnaryOperator<NfaState<Q>> visitCharacterClass(IntRangeSet codePointSet) {
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

    // How many code units wide is the class?
    final Optional<Integer> classSize =
      supplementaryCodeUnits.isEmpty() ? Optional.of(1) :
      basicCodeUnits.isEmpty() ? Optional.of(2) :
      Optional.empty();

    return (NfaState<Q> toState) -> {
      final Q to = toState.state();
      final Q start = freshState();
      if (!basicCodeUnits.isEmpty()) {
        addCodeUnitsState(start, basicCodeUnits, to);
      }

      for (var loAndHigh : supplementaryCodeUnits.entrySet()) {
        final Q hiEnd = freshState();
        addCodeUnitsState(start, loAndHigh.getValue(), hiEnd);
        addCodeUnitsState(hiEnd, loAndHigh.getKey(), to);
      }

      final var fixedGroup = toState
        .fixedGroup()
        .flatMap(loc -> classSize.map(loc::addDistance));

      return new NfaState<Q>(start, toState.unavoidable(), fixedGroup);
    };
  }

  public UnaryOperator<NfaState<Q>> visitConcatenation(
    UnaryOperator<NfaState<Q>> lhs,
    UnaryOperator<NfaState<Q>> rhs
  ) {
    return (NfaState<Q> to) -> {
      final NfaState<Q> mid = rhs.apply(to);
      return lhs.apply(mid);
    };
  }

  public UnaryOperator<NfaState<Q>> visitAlternation(
    UnaryOperator<NfaState<Q>> lhs,
    UnaryOperator<NfaState<Q>> rhs
  ) {
    return (NfaState<Q> to) -> {
      final NfaState<Q> toNoLoc = new NfaState<Q>(to.state(), false, Optional.empty());
      final NfaState<Q> lhsFrom = lhs.apply(toNoLoc);
      final NfaState<Q> rhsFrom = rhs.apply(toNoLoc);
      final Q from = freshState();
      addPlusMinusState(from, lhsFrom.state(), rhsFrom.state());
      return new NfaState<Q>(from, to.unavoidable(), Optional.empty());
    };
  }

  public UnaryOperator<NfaState<Q>> visitKleene(
    UnaryOperator<NfaState<Q>> lhs,
    boolean isLazy
  ) {
    return (NfaState<Q> to) -> {
      final Q lhsTo = freshState();
      final Q from = freshState();
      final Q lhsFrom = lhs
        .apply(new NfaState<Q>(lhsTo, false, Optional.empty()))
        .state();
      if (isLazy) {
        addPlusMinusState(lhsTo, to.state(), lhsFrom);
        addPlusMinusState(from, to.state(), lhsFrom);
      } else {
        addPlusMinusState(lhsTo, lhsFrom, to.state());
        addPlusMinusState(from, lhsFrom, to.state());
      }
      return new NfaState<Q>(from, to.unavoidable(), Optional.empty());
    };
  }

  public UnaryOperator<NfaState<Q>> visitOptional(
    UnaryOperator<NfaState<Q>> lhs,
    boolean isLazy
  ) {
    return (NfaState<Q> to) -> {
      final Q from = freshState();
      final Q lhsFrom = lhs
        .apply(new NfaState<Q>(to.state(), false, Optional.empty()))
        .state();
      if (isLazy) {
        addPlusMinusState(from, to.state(), lhsFrom);
      } else {
        addPlusMinusState(from, lhsFrom, to.state());
      }
      return new NfaState<Q>(from, to.unavoidable(), Optional.empty());
    };
  }

  public UnaryOperator<NfaState<Q>> visitPlus(
    UnaryOperator<NfaState<Q>> lhs,
    boolean isLazy
  ) {
    return (NfaState<Q> to) -> {
      final Q lhsTo = freshState();
      final Q lhsFrom = lhs
        .apply(new NfaState<Q>(lhsTo, to.unavoidable(), Optional.empty()))
        .state();
      if (isLazy) {
        addPlusMinusState(lhsTo, to.state(), lhsFrom);
      } else {
        addPlusMinusState(lhsTo, lhsFrom, to.state());
      }
      return new NfaState<Q>(lhsFrom, to.unavoidable(), Optional.empty());
    };
  }

  public UnaryOperator<NfaState<Q>> visitRepetition(
    UnaryOperator<NfaState<Q>> lhs,
    int atLeast,
    OptionalInt atMost,
    boolean isLazy
  ) {
    return (NfaState<Q> to) -> {
      // `atMost` portion - this is either a fixed repetition or a kleene star
      if (atMost.isPresent()) {
        final int atMostInt = atMost.getAsInt();
        for (int i = atLeast; i < atMostInt; i++) {
          final Q from = lhs
            .apply(new NfaState<Q>(to.state(), false, Optional.empty()))
            .state();
          final Q fork = freshState();
          if (isLazy) {
            addPlusMinusState(fork, to.state(), from);
          } else {
            addPlusMinusState(fork, from, to.state());
          }
          to = new NfaState<Q>(fork, to.unavoidable(), Optional.empty());
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

  public UnaryOperator<NfaState<Q>> visitGroup(
    UnaryOperator<NfaState<Q>> arg,
    OptionalInt groupIndex
  ) {
    if (groupIndex.isPresent()) {
      final int idx = groupIndex.getAsInt();
      final var end = GroupMarker.end(idx);
      final var start = GroupMarker.start(idx);

      return (NfaState<Q> to) -> {
        final Q argTo = freshState();
        final Q from = freshState();
        final boolean unavoidable = to.unavoidable();

        final var toGroup = to
          .fixedGroup()
          .orElseGet(() -> new RelativeGroupLocation(end, unavoidable, 0));
        final NfaState<Q> argFrom = arg
          .apply(new NfaState<Q>(argTo, unavoidable, Optional.of(toGroup)));
        final var fromGroup = argFrom
          .fixedGroup()
          .orElseGet(() -> new RelativeGroupLocation(start, unavoidable, 0));

        addGroupState(argTo, end, to.fixedGroup(), to.state());
        addGroupState(from, start, argFrom.fixedGroup(), argFrom.state());
        return new NfaState<Q>(from, unavoidable, Optional.of(fromGroup));
      };
    } else {
      return arg;
    }
  }

  public UnaryOperator<NfaState<Q>> visitBoundary(Boundary boundary) {
    return (NfaState<Q> to) -> {
      final Q from = freshState();
      addBoundaryState(from, boundary, to.state());
      return new NfaState<Q>(from, to.unavoidable(), to.fixedGroup());
    };
  }
}
