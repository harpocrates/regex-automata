package automata

import scala.collection.mutable
import java.util.{Map => JavaMap, Set => JavaSet, Collections}
import scala.jdk.CollectionConverters._

/** DFA obtained from a powerset construction on a reversed M2 NFA
  *
  * @note since this uses the powerset construction, each state is represented by a set
  * @param states states along with transition functions for each character
  * @param initial initial state
  * @param terminals terminal states
  */
final case class M3(
  states: Map[Set[Int], Map[CodeUnitTransition, Set[Int]]],
  initial: Set[Int],
  terminals: Set[Set[Int]]
) extends Dfa[Set[Int], CodeUnitTransition, Unit] {

  val accepting: JavaSet[Set[Int]] = terminals.asJava

  def transitionsMap(state: Set[Int]): JavaMap[CodeUnitTransition, Fsm.Transition[Set[Int], Unit]] = {

    final class NoAnnotTrans(to: Set[Int]) extends Fsm.Transition[Set[Int], Unit] {
      def annotation = ()
      def targetState = to
    }

    states.get(state) match {
      case None => Collections.emptyMap()
      case Some(ts) =>
        ts.view
          .mapValues(new NoAnnotTrans(_))
          .toMap[CodeUnitTransition, Fsm.Transition[Set[Int], Unit]]
          .asJava
    }
  }

  /** All reachable states
    *
    * In the worst case, this is exactly the powerset
    */
  def nonEmptyPowerStates: Set[Set[Int]] =
    states
      .keySet
      .union(terminals)
      .+(initial)
      .toSet

  /** Generate the source-code for a valid DOT graph
    *
    * Compile the `.dot` file using `dot -Tsvg m3.dot > m3.svg`
    */
  def graphVizSrc: String = {

    def renderSet(k: Set[Int]): String = k.toList.sorted.mkString("{",",","}")

    val builder = new StringBuilder()
    builder ++= s"digraph m3 {\n"
    builder ++= s"  rankdir = LR;\n"
    builder ++= s"  node [shape = doublecircle, label = \"\\N\"]; ${terminals.map(t => s"\"${renderSet(t)}\"").mkString(" ")};\n"
    builder ++= s"  node [shape = none, label = \"\"]; \"init\";\n"
    builder ++= s"  node [shape = circle, label = \"\\N\"];\n"

    builder ++= s"  \"init\" -> \"${renderSet(initial)}\";\n"

    for ((from, transitions) <- states; (c, to) <- transitions) {
      builder ++= s"  \"${renderSet(from)}\" -> \"${renderSet(to)}\" [label=\"$c\"];\n"
    }

    builder ++= s"}"
    builder.result()
  }

  /** Run a regex on an input
    *
    * @param input input string, must contain only characters from the BMP (no surrogates)
    * @return the array of states if it matched (in the order seen)
    */
  def captureSimulate(input: CharSequence): Option[Array[Set[Int]]] = {
    var currentState: Set[Int] = initial
    var pos: Int = input.length
    var simulatedOff: Int = 0
    val simulated = new Array[Set[Int]](input.length + 1)

    while (pos > 0) {
      simulated(simulatedOff) = currentState
      simulatedOff += 1
      pos -= 1
      val c = input.charAt(pos)
      val transitions = states
        .getOrElse(currentState, Map.empty)
        .collectFirst { case (cs, v) if cs.codeUnitSet.contains(c) => v }
      transitions match {
        case None => return None
        case Some(nextState) => currentState = nextState
      }
    }

    simulated(simulatedOff) = currentState
    if (terminals.contains(currentState)) Some(simulated) else None
  }

  /** Run a regex on an input
    *
    * Equivalent to {{{captureSimulate(input).nonEmpty}}} but more efficient
    *
    * @param input input string, must contain only characters from the BMP (no surrogates)
    * @return whether the input matches
    */
  def checkSimulate(input: CharSequence): Boolean = {
    var currentState: Set[Int] = initial
    var pos: Int = input.length

    while (pos > 0) {
      pos -= 1
      val c = input.charAt(pos)
      val transitions = states
        .getOrElse(currentState, Map.empty)
        .collectFirst { case (cs, v) if cs.codeUnitSet.contains(c) => v }
      transitions match {
        case None => return false
        case Some(nextState) => currentState = nextState
      }
    }

    terminals.contains(currentState)
  }
}


object M3 {

  // This is essentially a powerset construction on a reversed NFA
  def fromM2(m2: M2): M3 = {

    // Reversed edges from `m2` - looks up incoming edges to any state from `m2`
    val reverseTransitions: Map[Int, Set[(CodeUnitTransition, Int)]] = m2
      .states
      .toList
      .flatMap {
        case (from, (chars, tos)) =>
          for {
            to <- tos.keys
            c <- chars
          } yield to -> (c -> from)
      }
      .toSet[(Int, (CodeUnitTransition, Int))]
      .groupMap(_._1)(_._2)

    val initial = Set(m2.terminal)
    val states: mutable.Map[Set[Int], Map[CodeUnitTransition, Set[Int]]] = mutable.Map.empty
    val toVisit: mutable.Set[Set[Int]] = mutable.Set(initial)

    while (toVisit.nonEmpty) {
      val powerState: Set[Int] = toVisit.head
      toVisit.remove(powerState)

      // Look up all the transitions from the constituent states
      val transitions: Map[CodeUnitTransition, Set[Int]] = powerState
        .flatMap(reverseTransitions.getOrElse(_, Set.empty))
        .groupMap(_._1)(_._2)

      states += powerState -> transitions

      for ((_, outState) <- transitions) {
        if (!states.contains(outState)) toVisit.addOne(outState)
      }
    }

    val terminals: Set[Set[Int]] = states
      .keys
      .filter(key => key.intersect(m2.initial.keySet).nonEmpty)
      .toSet

    M3(
      states.toMap,
      initial,
      terminals
    )
  }

}

