package automata

import scala.collection.mutable

/** DFA obtained by augumenting M2 using M1 and M3
  *
  * @note transitions use states from M3, so are sets of ints
  * @param states states along with transition functions for each character
  * @param initial initial state
  * @param terminals terminal states
  */
final case class M4(
  states: Map[Int, Map[Set[Int], M4.Transition]],
  initial: Int,
  terminal: Int
) {

  /** Generate the source-code for a valid DOT graph
    *
    * Compile the `.dot` file using `dot -Tsvg m4.dot > m4.svg`
    */
  def graphVizSrc: String = {

    def renderEdge(ks: Seq[Set[Int]], gs: List[M2.GroupMarker]) = {
      val builder = new StringBuilder()
      builder ++= ks
        .map(k => s"${k.toList.sorted.mkString("{",",","}")}")
        .mkString(" ")
      if (gs.nonEmpty) {
        builder ++= " / "
        for (g <- gs) {
          builder += (if (g.isStart) 'S' else 'E')
          builder ++= s"<sub>${g.groupIdx}</sub>"
        }
      }
      builder.result()
    }

    val builder = new StringBuilder()
    builder ++= s"digraph m3 {\n"
    builder ++= s"  rankdir = LR;\n"
    builder ++= s"  node [shape = doublecircle, label = \"\\N\"]; \"$terminal\";\n"
    builder ++= s"  node [shape = none, label = \"\"]; \"init\";\n"
    builder ++= s"  node [shape = circle, label = \"\\N\"];\n"
    
    builder ++= s"  \"init\" -> \"$initial\";\n"

    for {
      (from, transitions) <- states
      (M4.Transition(to, gs), keys) <- transitions.toList.groupMap(_._2)(_._1)
    } {
      builder ++= s"  \"$from\" -> \"$to\" [label=<${renderEdge(keys, gs)}>];\n"
    }

    builder ++= s"}"
    builder.result()
  }

  /** Count the number of groups */
  def groupCount: Int = states
    .iterator
    .flatMap(_._2.iterator)
    .flatMap { case (_, M4.Transition(_, groups)) => groups.iterator }
    .map(_.groupIdx)
    .maxOption
    .getOrElse(0)

  /** Run against the output from an M3 automata
    *
    * @param m3Path states visited in M3 (see the output of `M3.simulate`)
    * @return array of match results
    */
  def simulate(inputString: String, m3Path: Array[Set[Int]]): Option[ArrayMatchResult] = {
    var currentState = initial
    var pos = m3Path.length - 1
    val captureGroups = new Array[Int](groupCount * 2)

    while (pos >= 0) {
      val step = m3Path(pos)
      val transitions = states.getOrElse(currentState, Map.empty).get(step)
      transitions match {
        case None => return None // TODO: unreachable?
        case Some(M4.Transition(nextState, groups)) =>
          currentState = nextState
          for (group <- groups) {
            val idx = if (group.isStart) group.groupIdx * 2 else group.groupIdx * 2 + 1
            captureGroups(idx) = pos
          }
      }
      pos -= 1
    }

    if (terminal == currentState) Some(new ArrayMatchResult(inputString, captureGroups)) else None
  }

}

object M4 {

  /** @param to target state
    * @param groups groups to track in this transition
    */
  final case class Transition(to: Int, groups: List[M2.GroupMarker])

  def compareMarkers(m1: M2.PathMarker, m2: M2.PathMarker): Int = {
    if (m1 == m2) 0
    else
      (m1, m2) match {
        case (M2.Plus, M2.Minus) => -1
        case (M2.Minus, M2.Plus) => 1
        case _ => throw new IllegalStateException(s"Comparing in-comparable markers: $m1 and $m2")
      }
  }

  /** Compare two paths using the partial lexicographic order where `+ > -` and
    * everything else is incomparable
    *
    * @param path1 first path
    * @param path2 second path
    * @return -1 if path1 is greater, 1 if path2 is greater, 0 if the paths are equal
    */
  def comparePaths(path1: List[M2.PathMarker], path2: List[M2.PathMarker]): Int = {
    var p1 = path1
    var p2 = path2
    while (p1.nonEmpty && p2.nonEmpty) {
      val headCmp = compareMarkers(p1.head, p2.head)
      if (headCmp != 0) return headCmp
      p1 = p1.tail
      p2 = p2.tail
    }
    java.lang.Boolean.compare(p1.nonEmpty, p2.nonEmpty)
  }

  /** Construct M4 from M1, M2, and M3
    *
    * @param m1 M1 NFA (tracks empty transitions)
    * @param m2 M2 NFA (tracks only character transitions)
    * @param m3 M3 DFA (powerset construction on inverted M2)
    * @return M4 DFA
    */
  def fromM1M2M3(m1: M1, m2: M2, m3: M3): M4 = {

    // Mapping from states in M2 to states in M3 containing the M2 state
    val stateToPowerState: Map[Int, Set[Set[Int]]] = m3
      .nonEmptyPowerStates
      .flatMap { powerState => powerState.map(_ -> powerState) }
      .groupMap(_._1)(_._2)

    // Given outgoing transitions in M2, determine outgoing transitions in M4
    def mapTransition(nextStates: Map[Int, List[M2.PathMarker]]): Map[Set[Int], M4.Transition] = {
      val output = mutable.Map.empty[Set[Int], (Int, List[M2.PathMarker])]
      
      // We iterate through the current edges - M4 is just M2 with edges "filtered"
      for ((toState, viaPath) <- nextStates) {
        for (m3State <- stateToPowerState.getOrElse(toState, Set.empty)) {
          output.get(m3State) match {
            case Some((_, prevViaPath)) if comparePaths(prevViaPath, viaPath) <= 0 =>
              // If the existing path is "greater", keep that path

            case _ =>
              output.addOne(m3State, (toState, viaPath))
          }
        }
      }

      output
        .view
        .mapValues { case (to, markers) =>
          Transition(to, markers.collect { case g: M2.GroupMarker => g })
        }
        .toMap
    }

    val states = mutable.Map.empty[Int, Map[Set[Int], M4.Transition]]
    val toVisit = mutable.Set.empty[Int]
    val initial = 0
    toVisit += initial

    while (toVisit.nonEmpty) {
      val m2State = toVisit.head
      toVisit -= m2State

      // Compute and add the transition
      val nextStates = if (m2State == 0) m2.initial else m2.states.get(m2State).map(_._2).getOrElse(Map.empty)
      val transition = mapTransition(nextStates)
      states += m2State -> transition

      // Update the set of transitions from M2 that are now reachable
      for (Transition(to, _) <- transition.values; if !states.contains(to)) {
        toVisit += to
      }
    }

    M4(
      states.toMap,
      initial,
      m2.terminal // TODO: confirm this is right
    )
  }
}
