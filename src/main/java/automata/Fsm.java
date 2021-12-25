package automata;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Finite state machine
 *
 * @param <Q> states in the automata
 * @param <E> input symbol alphabet
 * @param <T> annotations on transitions
 */
public interface Fsm<Q, E, T> {

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
        final Q target = transition.getValue().targetState();
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
  Collection<Map.Entry<E, Transition<Q, E, T>>> transitions(Q state);

  /**
   * FSM transition
   */
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

    /**
     * Label for edges in the DOT graph rendering.
     *
     * @param from state where this transition began
     * @param alphabetSymbol symbol associated with the transition
     */
    String dotLabel(Q from, E alphabetSymbol);
  }

  /**
   * Generate the source-code for a valid DOT graph.
   *
   * Compile the {@code .dot} file using {@code dot -Tsvg fsm.dot > fsm.svg}.
   *
   * @param graphName name of the directed graph
   */
  default String dotGraphSource(String graphName) {
    final var builder = new StringBuilder();
    builder.append("digraph " + graphName + "{\n");
    builder.append("  rankdir = LR;\n");

    // Terminal states
    builder.append("  node [shape = doublecircle, label = \"\\N\"];\n");
    for (Q terminal : accepting()) {
      builder.append("  \"" + terminal + "\";\n");
    }

    // Initial states
    int initCount = 0;
    builder.append("  node [shape = none, label = \"\"];\n");
    for (Q initial : initials()) {
      builder.append("  \"init" + initCount + "\";\n");
      initCount++;
    }
    builder.append("  node [shape = circle, label = \"\\N\"];\n");
    for (Q initial : initials()) {
      initCount--;
      builder.append("  \"init" + initCount + "\" -> \"" + initial + "\";\n");
    }

    // Transitions
    for (Q from : allStates()) {
      for (Map.Entry<E, Transition<Q, E, T>> transition : transitions(from)) {
        Q to = transition.getValue().targetState();
        String lbl = transition.getValue().dotLabel(from, transition.getKey());
        builder.append("  \"" + from + "\" -> \"" + to + "\" [label=<" + lbl + ">];\n");
      }
    }

    builder.append("}");
    return builder.toString();
  }
}

