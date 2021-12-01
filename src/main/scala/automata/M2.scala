package automata

import scala.collection.mutable

/** NFA corresponding on the regex, but with empty transitions collapsed
  *
  * @param states states with an outgoing character transition, now may transit to multiple other states
  * @param initial initial states
  * @param terminal terminal state
  */
final case class M2(
  states: Map[Int, (Char, Map[Int, List[M2.PathMarker]])],
  initial: Map[Int, List[M2.PathMarker]],
  terminal: Int
) {

  /** Generate the source-code for a valid DOT graph
    *
    * Compile the `.dot` file using `dot -Tsvg m2.dot > m2.svg`
    */
  def graphVizSrc: String = {
    def group(g: M2.PathMarker): String =
      g match {
        case M2.Plus => "+"
        case M2.Minus => "-"
        case M2.GroupMarker(true, groupIdx) => s"S<SUB>$groupIdx</SUB>"
        case M2.GroupMarker(false, groupIdx) => s"E<SUB>$groupIdx</SUB>"
      }

    val builder = new StringBuilder()
    builder ++= s"digraph m2 {\n"
    builder ++= s"  rankdir = LR;\n"
    builder ++= s"  node [shape = doublecircle, label = \"\\N\"]; $terminal;\n"
    builder ++= s"  node [shape = none, label = \"\"]; ${initial.keys.map(i => s"\"init$i\"").mkString(" ")};\n"
    builder ++= s"  node [shape = circle, label = \"\\N\"];\n"
    for ((i, groups) <- initial)
      builder ++= s"  \"init$i\" -> \"$i\" [label=<${groups.map(group).mkString}>];\n"

    for ((from, (c, tos)) <- states; (to, groups) <- tos) {
      builder ++= s"  \"$from\" -> \"$to\" [label=<$c / ${groups.map(group).mkString}>];\n"
    }

    builder ++= s"}"
    builder.result()
  }
}

object M2 {

  sealed abstract class EmptyTransition
  sealed abstract class PathMarker extends EmptyTransition
  final case object Nop extends EmptyTransition
  final case object Plus extends PathMarker
  final case object Minus extends PathMarker

  /** @param isStart is this the start or end of a group?
    * @param groupIdx index of the group
    */
  final case class GroupMarker(isStart: Boolean, groupIdx: Int) extends PathMarker

  /** Convert an M1 NFA into an M2 NFA
    *
    * This is basically just collapsing states
    *
    * @param m1 NFA to cnovert
    * @return equivalent NFA
    */
  def fromM1(m1: M1): M2 = {

    // We only keep the terminal state and states with a `Character` out edge
    val preservedStates: Set[Int] = m1.states
      .collect { case (from, _: M1.Character) => from }
      .toSet
      .+(m1.terminal)

    /* Use Floyd–Warshall's algorithm to get states reachable via empty edges and track the
     * "shortest" paths where a path whose head is a `+` is shorter than one whose head is a `-`
     *
     * Entry `k -> Map(v1 -> l1, v2 -> l2)` means that
     *
     *   - `k` can reach `v1` and the shortest path starts with `l1`
     *   - `k` can reach `v2` and the shortest path starts with `l2`
     */
    val reachableFrom: Map[Int, mutable.Map[Int, EmptyTransition]] = m1.states
      .iterator
      .map { case (from, _) => from }
      .concat(List(m1.terminal))
      .map { from => from -> mutable.Map.apply[Int, EmptyTransition](from -> Nop) }
      .toMap
    for ((from, transition) <- m1.states) {
      transition match {
        case _: M1.Character => ()
        case M1.PlusMinus(plus, minus) =>
          reachableFrom(from)
            .addOne(plus, Plus)
            .addOne(minus, Minus)
        case M1.GroupStart(groupIdx, to) =>
          reachableFrom(from)
            .addOne(to, GroupMarker(true, groupIdx))
        case M1.GroupEnd(groupIdx, to) =>
          reachableFrom(from)
            .addOne(to, GroupMarker(false, groupIdx))
      }
    }
    for (k <- reachableFrom.keys; i <- reachableFrom.keys; j <- reachableFrom.keys) {
      val reachIj: Option[EmptyTransition] = reachableFrom(i).get(j)
      val reachIk: Option[EmptyTransition] = reachableFrom(i).get(k)
      val reachKj: Option[EmptyTransition] = reachableFrom(k).get(j)
      val reachIjThroughK: Option[EmptyTransition] = reachKj.flatMap(_ => reachIk)
      (reachIj, reachIjThroughK) match {
        // `i` -> `j` was not previously known to be possible
        case (None, Some(t)) => reachableFrom(i).addOne(j, t)

        // `i` -> `j` is now possible through a `Plus`
        case (_, Some(Plus)) => reachableFrom(i).addOne(j, Plus)

        // `i` -> `j` is not going to be improved
        case _ =>
      }
    }

    def shortestGroupPath(from: Int, to: Int): List[PathMarker] = {
      val path = List.newBuilder[PathMarker]
      var current = from
      var firstIteration = true
      while (m1.states.contains(current) && (current != to || firstIteration)) {
        firstIteration = false
        current = m1.states(current) match {
          case M1.Character(_, to) =>
            to
          case M1.PlusMinus(plus, minus) =>
            reachableFrom(current).apply(to) match {
              case Plus =>
                path += Plus
                plus
              case Minus =>
                path += Minus
                minus
              case c =>
                val msg = s"shortestPath($from, $to): invalid transition $c out of $current"
                throw new IllegalStateException(msg)
            }
          case (M1.GroupStart(groupIdx, to)) =>
            path += GroupMarker(true, groupIdx)
            to
          case (M1.GroupEnd(groupIdx, to)) =>
            path += GroupMarker(false, groupIdx)
            to
        }
      }
      path.result()
    }

    val states = m1.states
      .collect {
        case (from, M1.Character(c, to)) =>
          val newTargets = reachableFrom(to)
            .view
            .filterKeys(preservedStates.contains(_))
            .map { case (target, _) => target -> shortestGroupPath(from, target) }
            .toMap
          from -> (c -> newTargets)
      }
      .toMap

    M2(
      states,
      reachableFrom(m1.initial)
        .view
        .filterKeys(preservedStates.contains(_))
        .map { case (target, _) => target -> shortestGroupPath(m1.initial, target) }
        .toMap,
      m1.terminal
    )
  }
}
