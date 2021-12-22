package automata

import scala.collection.mutable

/** NFA corresponding on the regex, but with empty transitions collapsed
  *
  * @param states states with an outgoing character transition, now may transit to multiple other states
  * @param initial initial states
  * @param terminal terminal state
  */
final case class M2(
  states: Map[Int, (List[CodeUnitTransition], Map[Int, List[PathMarker]])],
  initial: Map[Int, List[PathMarker]],
  terminal: Int
) {

  /** Generate the source-code for a valid DOT graph
    *
    * Compile the `.dot` file using `dot -Tsvg m2.dot > m2.svg`
    */
  def graphVizSrc: String = {
    def group(g: PathMarker): String = g.graphVizLabel

    val builder = new StringBuilder()
    builder ++= s"digraph m2 {\n"
    builder ++= s"  rankdir = LR;\n"
    builder ++= s"  node [shape = doublecircle, label = \"\\N\"]; $terminal;\n"
    builder ++= s"  node [shape = none, label = \"\"]; ${initial.keys.map(i => s"\"init$i\"").mkString(" ")};\n"
    builder ++= s"  node [shape = circle, label = \"\\N\"];\n"
    for ((i, groups) <- initial)
      builder ++= s"  \"init$i\" -> \"$i\" [label=<${groups.map(group).mkString}>];\n"

    for ((from, (c, tos)) <- states; (to, groups) <- tos) {
      builder ++= s"  \"$from\" -> \"$to\" [label=<${c.mkString(", ")} / ${groups.map(group).mkString}>];\n"
    }

    builder ++= s"}"
    builder.result()
  }
}

object M2 {

  /** Convert an M1 NFA into an M2 NFA
    *
    * This is basically just collapsing states
    *
    * @param m1 NFA to cnovert
    * @return equivalent NFA
    */
  def fromM1(m1: M1[Int]): M2 = {

    // We only keep the terminal state and states with a `Character` out edge
    val preservedStates: Set[Int] = m1.states
      .collect { case (from, _: M1.Character[Int @unchecked]) => from }
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
    val reachableFrom: Map[Int, mutable.Map[Int, PathMarker]] = m1.states
      .iterator
      .map { case (from, _) => from }
      .concat(List(m1.terminal, m1.initial))
      .map { from => from -> mutable.Map.empty[Int, PathMarker] }
      .toMap
    for ((from, transition) <- m1.states) {
      transition match {
        case _: M1.Character[Int @unchecked] => ()
        case M1.PlusMinus(plus, minus) =>
          reachableFrom(from)
            .addOne(plus -> AlternationMarker.PLUS)
            .addOne(minus -> AlternationMarker.MINUS)
        case M1.GroupStart(groupIdx, to) =>
          reachableFrom(from)
            .addOne(to -> GroupMarker.start(groupIdx))
        case M1.GroupEnd(groupIdx, to) =>
          reachableFrom(from)
            .addOne(to -> GroupMarker.end(groupIdx))
      }
    }
    for (k <- reachableFrom.keys; i <- reachableFrom.keys; j <- reachableFrom.keys) {
      val reachIj: Option[PathMarker] = reachableFrom(i).get(j)
      val reachIk: Option[PathMarker] = reachableFrom(i).get(k)
      val reachKj: Option[PathMarker] = reachableFrom(k).get(j)
      val reachIjThroughK: Option[PathMarker] = reachKj.flatMap(_ => reachIk)
      (reachIj, reachIjThroughK) match {
        // `i` -> `j` was not previously known to be possible
        case (None, Some(t)) => reachableFrom(i).addOne(j -> t)

        // `i` -> `j` is now possible through a `Plus`
        case (_, Some(AlternationMarker.PLUS)) => reachableFrom(i).addOne(j -> AlternationMarker.PLUS)

        // `i` -> `j` is not going to be improved
        case _ =>
      }
    }

    /** Find the shortest path between two different states */
    def shortestEmptyPath(from: Int, to: Int): List[PathMarker] = {
      var from1 = from
      val path = List.newBuilder[PathMarker]
      while (from1 != to) {
        val transition = reachableFrom(from1)(to)
        path += transition
        (m1.states(from1), transition) match {
          case (M1.PlusMinus(plus, _), AlternationMarker.PLUS) => from1 = plus
          case (M1.PlusMinus(_, minus), AlternationMarker.MINUS) => from1 = minus
          case (M1.GroupStart(_, to), _: GroupMarker) => from1 = to
          case (M1.GroupEnd(_, to), _: GroupMarker) => from1 = to
          case _ => throw new IllegalStateException()
        }
      }
      path.result()
    }

    val states = m1.states
      .collect {
        case (from, M1.Character(c, to)) if preservedStates.contains(to) =>
          from -> (c -> Map(to -> Nil))
        case (from, M1.Character(c, to)) =>
          val newTargets = reachableFrom(to)
            .view
            .filterKeys(preservedStates.contains(_))
            .map { case (target, _) => target -> shortestEmptyPath(to, target) }
            .toMap
          from -> (c -> newTargets)
      }
      .toMap

    val initials: Map[Int, List[PathMarker]] = if (preservedStates.contains(m1.initial)) {
      Map(m1.initial -> Nil)
    } else {
      reachableFrom(m1.initial)
        .view
        .filterKeys(preservedStates.contains(_))
        .map { case (target, _) => target -> shortestEmptyPath(m1.initial, target) }
        .toMap
    }

    M2(states, initials, m1.terminal)
  }
}
