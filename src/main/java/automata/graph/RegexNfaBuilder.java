package automata.graph;

import automata.parser.Boundary;
import automata.parser.CodePoints;
import automata.parser.RegexVisitor;
import automata.parser.CodePointSetVisitor;
import automata.util.IntRange;
import automata.util.IntRangeSet;
import java.util.Stack;
import java.util.Optional;
import java.util.OptionalInt;
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
 * @author Alec Theriault
 * @param <Q> states in the automata
 */
public abstract class RegexNfaBuilder<Q>
    extends CodePointSetVisitor
    implements RegexVisitor<UnaryOperator<NfaState<Q>>, IntRangeSet> {

  /**
   * Code units to be used in the NFA.
   */
  public final StandardCodeUnits codeUnits;

  public RegexNfaBuilder(StandardCodeUnits codeUnits) {
    this.codeUnits = codeUnits;
  }

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
   * <p>This should only be called once per unique group marker.
   *
   * @param state state to add
   * @param marker group marker
   * @param unavoidable is the group unavoidable during matching?
   * @param relativeLocation fixed position relative to another marker
   * @param to state at the other end of the group transition
   */
  abstract void addGroupState(
    Q state,
    GroupMarker marker,
    boolean unavoidable,
    Optional<GroupLocation> relativeLocation,
    Q to
  );

  /**
   * Register a new state with an empty boundary transition out of it.
   *
   * @param state state to add
   * @param boundary zero-width check
   * @param to state at the other end of the group transition
   */
  abstract void addBoundaryState(Q state, Boundary boundary, Q to);

  public UnaryOperator<NfaState<Q>> visitEpsilon() {
    return UnaryOperator.identity();
  }

  public UnaryOperator<NfaState<Q>> visitCharacterClass(IntRangeSet codePointSet) {
    if (!codePointSet.difference(IntRangeSet.of(CodePoints.UNICODE_RANGE)).isEmpty()) {
      throw new IllegalArgumentException("Codepoints outside the unicode range aren't allowed");
    }

    final var suffixTrie = codeUnits.codeUnitRangeSuffixTrie(codePointSet);

    // How many code units wide is the class?
    final Optional<Integer> classSize = suffixTrie.inSetDepth();

    // Depth first traversal of the tree
    record TraversalEntry<Q>(
      TrieSet<IntRangeSet> subTrie,
      int distanceToRoot,
      IntRangeSet codeUnitToParent,
      Q parentNode
    ) { }

    return (NfaState<Q> toState) -> {
      final var start = freshState();

      // Depth first traversal of the suffix trie
      final var toVisit = new Stack<TraversalEntry<Q>>();
      for (final var childEntry : suffixTrie.children.entrySet()) {
        toVisit.push(new TraversalEntry<>(
          childEntry.getValue(),
          1,
          childEntry.getKey(),
          toState.state()
        ));
      }
      while (!toVisit.isEmpty()) {
        final var entry = toVisit.pop();
        final Q thisNode = entry.subTrie.inSet ? start : freshState();

        // Visit this node
        addCodeUnitsState(thisNode, entry.codeUnitToParent, entry.parentNode);
        if (entry.subTrie.inSet && !entry.subTrie.children.isEmpty()) {
          throw new IllegalStateException("One code unit sequence cannot be a suffix of another");
        }

        // Plan to visit children
        for (final var childEntry : entry.subTrie.children.entrySet()) {
          toVisit.push(new TraversalEntry<>(
            childEntry.getValue(),
            entry.distanceToRoot + 1,
            childEntry.getKey(),
            thisNode
          ));
        }
      }

      final var fixedGroup = toState
        .fixedGroup()
        .flatMap(loc -> classSize.map(loc::addDistance));

      return new NfaState<Q>(start, toState.insideRepetition(), toState.unavoidable(), fixedGroup);

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
      final NfaState<Q> toNoLoc = new NfaState<Q>(to.state(), to.insideRepetition(), false, Optional.empty());
      final NfaState<Q> lhsFrom = lhs.apply(toNoLoc);
      final NfaState<Q> rhsFrom = rhs.apply(toNoLoc);
      final Q from = freshState();
      addPlusMinusState(from, lhsFrom.state(), rhsFrom.state());
      return new NfaState<Q>(from, to.insideRepetition(), to.unavoidable(), Optional.empty());
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
        .apply(new NfaState<Q>(lhsTo, to.insideRepetition(), false, Optional.empty()))
        .state();
      if (isLazy) {
        addPlusMinusState(lhsTo, to.state(), lhsFrom);
        addPlusMinusState(from, to.state(), lhsFrom);
      } else {
        addPlusMinusState(lhsTo, lhsFrom, to.state());
        addPlusMinusState(from, lhsFrom, to.state());
      }
      return new NfaState<Q>(from, to.insideRepetition(), to.unavoidable(), Optional.empty());
    };
  }

  public UnaryOperator<NfaState<Q>> visitOptional(
    UnaryOperator<NfaState<Q>> lhs,
    boolean isLazy
  ) {
    return (NfaState<Q> to) -> {
      final Q from = freshState();
      final Q lhsFrom = lhs
        .apply(new NfaState<Q>(to.state(), to.insideRepetition(), false, Optional.empty()))
        .state();
      if (isLazy) {
        addPlusMinusState(from, to.state(), lhsFrom);
      } else {
        addPlusMinusState(from, lhsFrom, to.state());
      }
      return new NfaState<Q>(from, to.insideRepetition(), to.unavoidable(), Optional.empty());
    };
  }

  public UnaryOperator<NfaState<Q>> visitPlus(
    UnaryOperator<NfaState<Q>> lhs,
    boolean isLazy
  ) {
    return (NfaState<Q> to) -> {
      final Q lhsTo = freshState();
      final Q lhsFrom = lhs
        .apply(new NfaState<Q>(lhsTo, to.insideRepetition(), to.unavoidable(), Optional.empty()))
        .state();
      if (isLazy) {
        addPlusMinusState(lhsTo, to.state(), lhsFrom);
      } else {
        addPlusMinusState(lhsTo, lhsFrom, to.state());
      }
      return new NfaState<Q>(lhsFrom, to.insideRepetition(), to.unavoidable(), Optional.empty());
    };
  }

  public UnaryOperator<NfaState<Q>> visitRepetition(
    UnaryOperator<NfaState<Q>> lhs,
    int atLeast,
    OptionalInt atMost,
    boolean isLazy
  ) {
    return (NfaState<Q> to) -> {
      final boolean insideRepetition = to.insideRepetition();

      // `atMost` portion - this is either a fixed repetition or a kleene star
      if (atMost.isPresent()) {
        final int atMostInt = atMost.getAsInt();
        for (int i = atLeast; i < atMostInt; i++) {
          final Q from = lhs
            .apply(new NfaState<Q>(to.state(), to.insideRepetition(), false, Optional.empty()))
            .state();
          final Q fork = freshState();
          if (isLazy) {
            addPlusMinusState(fork, to.state(), from);
          } else {
            addPlusMinusState(fork, from, to.state());
          }
          to = new NfaState<Q>(fork, true, to.unavoidable(), Optional.empty());
        }
      } else {
        to = visitKleene(lhs, isLazy).apply(to).withRepetition(true);
      }

      // `atLeast` portion
      for (int i = 0; i < atLeast; i++) {
        to = lhs.apply(to).withRepetition(true);
      }

      return to.withRepetition(insideRepetition);
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
        if (to.insideRepetition()) {
          return arg.apply(to);
        }

        final Q argTo = freshState();
        final Q from = freshState();
        final boolean unavoidable = to.unavoidable();

        addGroupState(argTo, end, unavoidable, to.fixedGroup(), to.state());
        final var toGroup = new GroupLocation.RelativeToGroup(end, 0);
        final NfaState<Q> argFrom = arg
          .apply(new NfaState<Q>(argTo, false, unavoidable, Optional.of(toGroup)));
        addGroupState(from, start, unavoidable, argFrom.fixedGroup(), argFrom.state());
        final var fromGroup = new GroupLocation.RelativeToGroup(start, 0);

        return new NfaState<Q>(from, false, unavoidable, Optional.of(fromGroup));
      };
    } else {
      return arg;
    }
  }

  public UnaryOperator<NfaState<Q>> visitBoundary(Boundary boundary) {
    return (NfaState<Q> to) -> {
      final Q from = freshState();
      addBoundaryState(from, boundary, to.state());
      return new NfaState<Q>(from, to.insideRepetition(), to.unavoidable(), to.fixedGroup());
    };
  }
}
