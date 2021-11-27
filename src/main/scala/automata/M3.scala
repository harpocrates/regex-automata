package automata

import scala.collection.mutable

/** DFA obtained from a powerset construction on a reversed M2 NFA
  *
  * @note since this uses the powerset construction, each state is represented by a set
  * @param states states along with transition functions for each character
  * @param initial initial state
  * @param terminals terminal states
  */
final case class M3(
  states: Map[Set[Int], Map[Char, Set[Int]]],
  initial: Set[Int],
  terminals: Set[Set[Int]]
) {

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
    * @return the array of states if it matched (in reverse order seen)
    */
  def simulate(input: String): Option[Array[Set[Int]]] = {
    var currentState = initial
    var pos = input.length
    val simulated = new Array[Set[Int]](input.length + 1)

    while (pos > 0) {
      simulated(pos) = currentState
      pos -= 1
      val c = input.charAt(pos)
      val transitions = states.getOrElse(currentState, Map.empty).get(c)
      transitions match {
        case None => return None
        case Some(nextState) => currentState = nextState
      }
    }

    simulated(0) = currentState
    if (terminals.contains(currentState)) Some(simulated) else None
  }
}


object M3 {

  // This is essentially a powerset construction on a reversed NFA
  def fromM2(m2: M2): M3 = {

    // Reversed edges from `m2` - looks up incoming edges to any state from `m2`
    val reverseTransitions: Map[Int, Set[(Char, Int)]] = m2
      .states
      .toList
      .flatMap {
        case (from, (c, tos)) =>
          val reversedTransition = c -> from
          tos.keys.toList.map(_ -> reversedTransition)
      }
      .toSet[(Int, (Char, Int))]
      .groupMap(_._1)(_._2)

    val initial = Set(m2.terminal)
    val states: mutable.Map[Set[Int], Map[Char, Set[Int]]] = mutable.Map.empty
    val toVisit: mutable.Set[Set[Int]] = mutable.Set(initial)

    while (toVisit.nonEmpty) {
      val powerState: Set[Int] = toVisit.head
      toVisit.remove(powerState)

      // Look up all the transitions from the constituent states
      val transitions: Map[Char, Set[Int]] = powerState
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

