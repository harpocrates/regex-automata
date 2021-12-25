package automata

import java.lang.{Iterable => JavaIterable}
import scala.collection.mutable
import java.util.{Map => JavaMap, Set => JavaSet, Collections}
import scala.jdk.CollectionConverters._

/** DFA obtained by augumenting M2 using M1 and M3
  *
  * @note transitions use states from M3, so are sets of ints
  * @param states states along with transition functions for each character
  * @param initial initial state
  * @param terminals terminal states
  */
final case class M4(
  states: Map[Int, Map[IntSet, M4.Transition]],
  initial: Int,
  terminal: Int
) extends Dfa[Int, IntSet, JavaIterable[GroupMarker]] {

  val accepting: JavaSet[Int] = JavaSet.of(terminal)

  def transitionsMap(state: Int): JavaMap[IntSet, Fsm.Transition[Int, IntSet, JavaIterable[GroupMarker]]] =
    states.get(state) match {
      case None => Collections.emptyMap()
      case Some(ts) =>
        ts.view.toMap[IntSet, Fsm.Transition[Int, IntSet, JavaIterable[GroupMarker]]].asJava
    }

  /** Count the number of groups */
  def groupCount: Int = 1 + states
    .iterator
    .flatMap(_._2.iterator)
    .flatMap { case (_, M4.Transition(_, groups)) => groups.iterator }
    .map(_.groupIndex)
    .maxOption
    .getOrElse(-1)

  /** Run against the output from an M3 automata, but in reverse
    *
    * @param input input test string
    * @param m3Path states visited in M3 (see the output of `M3.simulate`)
    * @param printDebugInfo print to STDERR a trace of what is happening
    * @return array of match results
    */
  def simulate(input: CharSequence, m3Path: Array[IntSet], printDebugInfo: Boolean): Option[ArrayMatchResult] = {
    var currentState = initial
    var pos = m3Path.length - 1
    var strOffset = 0
    val captureGroups = Array.fill(groupCount * 2)(-1)
    if (printDebugInfo) System.err.println(s"[M4] starting run on: $m3Path")

    while (pos >= 0) {
      val step = m3Path(pos)
      if (printDebugInfo) System.err.println(s"[M4] entering $currentState")
      val transitions = states.getOrElse(currentState, Map.empty).get(step)
      transitions match {
        case None => return None // TODO: unreachable?
        case Some(M4.Transition(nextState, groups)) =>
          for (group <- groups) {
            val idx = if (group.isStart) group.groupIndex * 2 else group.groupIndex * 2 + 1
            captureGroups(idx) = strOffset
            if (printDebugInfo) System.err.println(s"[M4] capturing $group: groups($idx) = $strOffset")
          }
          currentState = nextState
      }
      pos -= 1
      strOffset += 1
    }

    if (terminal != currentState) None // TODO: unreachable?
    else Some(new ArrayMatchResult(input.toString, captureGroups))
  }
}

object M4 {

  /** @param to target state
    * @param groups groups to track in this transition
    */
  final case class Transition(
    to: Int,
    groups: List[GroupMarker]
  ) extends Fsm.Transition[Int, IntSet, JavaIterable[GroupMarker]] {
    def targetState = to
    val annotation: JavaIterable[GroupMarker] = groups.asJava
    def dotLabel(from: Int, transition: IntSet): String = {
      val groupsString = if (groups.isEmpty) "" else " / " + groups.map(_.dotLabel).mkString
      transition.toString + groupsString
    }
  }

  def compareMarkers(m1: PathMarker, m2: PathMarker): Int = {
    if (m1 == m2) 0
    else
      (m1, m2) match {
        case (a1: AlternationMarker, a2: AlternationMarker) => a1.compareTo(a2)
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
  def comparePaths(path1: List[PathMarker], path2: List[PathMarker]): Int = {
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

  /** Construct M4 from M2, and M3
    *
    * @param m2 M2 NFA (tracks only character transitions)
    * @param m3 M3 DFA (powerset construction on inverted M2)
    * @return M4 DFA
    */
  def fromM2M3(m2: M2, m3: M3): M4 = {

    // Mapping from states in M2 to states in M3 containing the M2 state
    val stateToPowerState: Map[Integer, Set[IntSet]] = m3
      .nonEmptyPowerStates
      .flatMap { powerState =>
        val builder = Set.newBuilder[Integer]
        powerState.stream.boxed.forEach(builder += _)
        builder.result().map(_ -> powerState)
      }
      .groupMap(_._1)(_._2)

    // Given outgoing transitions in M2, determine outgoing transitions in M4
    def mapTransition(nextStates: Map[Integer, List[PathMarker]]): Map[IntSet, M4.Transition] = {
      val output = mutable.Map.empty[IntSet, (Integer, List[PathMarker])]

      // We iterate through the current edges - M4 is just M2 with edges "filtered"
      for ((toState, viaPath) <- nextStates) {
        for (m3State <- stateToPowerState.getOrElse(toState, Set.empty)) {
          output.get(m3State) match {
            case Some((_, prevViaPath)) if comparePaths(prevViaPath, viaPath) <= 0 =>
              // If the existing path is "greater", keep that path

            case _ =>
              output.addOne(m3State -> (toState -> viaPath))
          }
        }
      }

      output
        .view
        .mapValues { case (to, markers) =>
          Transition(to, markers.collect { case g: GroupMarker => g })
        }
        .toMap
    }

    val states = mutable.Map.empty[Int, Map[IntSet, M4.Transition]]
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
