package automata;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Finite state machine
 *
 * @param <Q> states in the automata
 * @param <T> annotations on transitions
 */
public interface Fsm<Q, T> {

  /**
   * Initial state
   *
   * @return starting state in the machine
   */
  Set<Q> initials();

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

    for (Q initial : initials()) {
      toVisit.push(initial);
      states.add(initial);
    }

    while (!toVisit.empty()) {
      for (var transition : transitions(toVisit.pop())) {
        final Q target = transition.targetState();
        if (states.add(target)) {
          toVisit.push(target);
        }
      }
    }

    return states;
  }

  /**
   * Look up transitions from a certain state
   *
   * @param state state inside the FSM
   * @return transitions from that state
   */
  Collection<Transition<Q, T>> transitions(Q state);

  /**
   * FSM transition
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
}

