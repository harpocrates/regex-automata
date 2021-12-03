package automata;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Deterministic finite automata
 *
 * @param <Q> states in the automata
 * @param <E> input symbol alphabet
 * @param <T> annotations on transitions
 */
public interface Dfa<Q, E, T> {

  /**
   * Initial state
   *
   * @return starting state in the DFA
   */
  Q initial();

  /**
   * All states
   *
   * @return set of all states in the DFA
   */
  default Set<Q> allStates() {
    final Set<Q> states = new HashSet<Q>();
    final Stack<Q> toVisit = new Stack<Q>();

    {
      final var first = initial();
      toVisit.push(first);
      states.add(first);
    }

    while (!toVisit.empty()) {
      for (var transition : transitions(toVisit.pop()).values()) {
        final Q target = transition.targetState();
        if (states.add(target)) {
          toVisit.push(target);
        }
      }
    }

    return states;
  }

  /**
   * Check if the specified state is a terminal one or not
   *
   * @param state state inside the DFA
   * @return whether the state is terminal
   */
  boolean isTerminal(Q state);

  /**
   * Look up transitions from a certain state
   *
   * @param state state inside the DFA
   * @return map of alphabet symbols to transitions
   */
  Map<E, Transition<Q, T>> transitions(Q state);

  /**
   * DFA transition
   */
  interface Transition<Q, T> {

    /**
     * Target of the transition
     *
     * @return state pointed to by this transition
     */
    Q targetState();

    /**
     * Annotation on the transition
     *
     * @return annotation stored on the transition
     */
    T annotation();
  }

  /**
   * Run the a DFA either to completion or to a stuck state
   *
   * @param dfa deterministic finite automata to run
   * @param onState callback to invoke whenever entering a state
   * @param onJump callback to invoke whenever jumping across a transition
   * @param onMissingJump callback to invoke when there is no valid transition
   * @return whether the DFA reached a terminal state
   */
  static <Q, E, T> boolean run(
    Dfa<Q, E, T> dfa,
    Iterator<E> input,
    Consumer<Q> onState,
    BiConsumer<E, T> onJump,
    Consumer<E> onMissingJump
  ) {
    Q currentState = dfa.initial();
    onState.accept(currentState);

    while (input.hasNext()) {
      final E e = input.next();
      final Transition<Q, T> transition = dfa.transitions(currentState).get(e);

      // No transition found
      if (transition == null) {
        onMissingJump.accept(e);
        return false;
      }

      // Accept the jump and new state
      onJump.accept(e, transition.annotation());
      currentState = transition.targetState();
      onState.accept(currentState);
    }

    return dfa.isTerminal(currentState);
  }
}

