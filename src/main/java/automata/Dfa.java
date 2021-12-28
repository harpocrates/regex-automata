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

  interface Transition<Q, E, T> {

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
   * Initial state
   *
   * @return starting state in the machine
   */
  Q initial();

  /**
   * Accepting states
   *
   * @return accepting states in the machine
   */
  Set<Q> accepting();

  /**
   * All states
   *
   * @return set of all (reachable) states in the FSM
   */
  default Set<Q> allStates() {
    final Set<Q> states = new HashSet<Q>();
    final Stack<Q> toVisit = new Stack<Q>();

    {
      final Q initial = initial();
      toVisit.push(initial);
      states.add(initial);
    }

    while (!toVisit.empty()) {
      for (var transition : transitionsMap(toVisit.pop()).values()) {
        final Q target = transition.targetState();
        if (states.add(target)) {
          toVisit.push(target);
        }
      }
    }

    return states;
  }

  /**
   * Look up the mapping of transitions from a certain state
   *
   * @param state state inside the DFA
   * @return map of alphabet symbols to transitions
   */
  Map<E, Transition<Q, E, T>> transitionsMap(Q state);

  /**
   * Run the a DFA either to completion or to a stuck state
   *
   * @param dfa deterministic finite automata to run
   * @param onState callback to invoke whenever entering a state
   * @param onJump callback to invoke whenever jumping across a transition
   * @param onMissingJump callback to invoke when there is no valid transition
   * @return whether the DFA reached an accepting state
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
    Set<Q> terminals = dfa.accepting();

    while (input.hasNext()) {
      final E e = input.next();
      final Transition<Q, E, T> transition = dfa.transitionsMap(currentState).get(e);

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

    return terminals.contains(currentState);
  }
}

