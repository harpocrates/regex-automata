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
public interface Dfa<Q, E, T> extends Fsm<Q, E, T> {

  /**
   * Initial state
   *
   * @return starting state in the machine
   */
  Q initial();

  default Set<Q> initials() {
    return Set.of(initial());
  }

  /**
   * Look up the mapping of transitions from a certain state
   *
   * @param state state inside the DFA
   * @return map of alphabet symbols to transitions
   */
  Map<E, Transition<Q, E, T>> transitionsMap(Q state);

  default Collection<Map.Entry<E, Transition<Q, E, T>>> transitions(Q state) {
    return transitionsMap(state).entrySet();
  }

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

