package automata

import java.util.stream.{Collectors, Stream}
import scala.jdk.CollectionConverters._
import java.util.{Set => JavaSet, HashMap => JavaHashMap, Map => JavaMap}

/** NFA corresponding on the regex, but with empty transitions collapsed
  *
  * @param states states with an outgoing character transition, now may transit to multiple other states
  * @param initial initial states
  * @param terminal terminal state
  */
final case class M2(
  states: Map[Integer, (List[CodeUnitTransition], Map[Integer, List[PathMarker]])],
  initial: Map[Integer, List[PathMarker]],
  terminal: Integer
) {

  /** Generate the source-code for a valid DOT graph
    *
    * Compile the `.dot` file using `dot -Tsvg m2.dot > m2.svg`
    */
  def graphVizSrc: String = {
    def group(g: PathMarker): String = g.dotLabel

    val builder = new StringBuilder()
    builder ++= s"digraph m2 {\n"
    builder ++= s"  rankdir = LR;\n"
    builder ++= s"  node [shape = doublecircle, label = \"\\N\"]; $terminal;\n"
    builder ++= s"  node [shape = none, label = \"\"]; ${initial.keys.map(i => s"\"init$i\"").mkString(" ")};\n"
    builder ++= s"  node [shape = circle, label = \"\\N\"];\n"
    for ((i, groups) <- initial)
      builder ++= s"  \"init$i\" -> \"$i\" [label=<${groups.map(group).mkString}>];\n"

    for ((from, (codeUnitTransitions, tos)) <- states; c <- codeUnitTransitions; (to, groups) <- tos) {
      builder ++= s"  \"$from\" -> \"$to\" [label=<${c.dotLabel} / ${groups.map(group).mkString}>];\n"
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
  def fromM1(m1: M1[Integer]): M2 = { //  Dfa[Integer, M1Transition, Unit]): M2 = { //

    val allStates = m1.allStates

    // We only keep the terminal state and states with a `CodeUnitTransition` out edge
    val preservedStates: JavaSet[Integer] = Stream
      .concat(
        m1.accepting.stream,
        allStates
          .stream
          .filter(s => m1.transitionsMap(s).keySet.stream.anyMatch(_.isInstanceOf[CodeUnitTransition]))
      )
      .collect(Collectors.toSet[Integer])

    /* Use Floyd–Warshall's algorithm to get states reachable via empty edges and track the
     * "shortest" paths where a path whose head is a `+` is shorter than one whose head is a `-`
     *
     * Entry `k -> Map(v1 -> l1, v2 -> l2)` means that
     *
     *   - `k` can reach `v1` and the shortest path starts with `l1`
     *   - `k` can reach `v2` and the shortest path starts with `l2`
     */
    val reachableFrom: JavaMap[Integer, JavaHashMap[Integer, PathMarker]] = allStates
      .stream
      .collect(Collectors.toMap((from: Integer) => from, (_: Integer) => new JavaHashMap[Integer, PathMarker]()))
    for (from <- allStates.iterator.asScala; (transition, to) <- m1.transitionsMap(from).asScala) {
      transition match {
        case pm: PathMarker => reachableFrom.get(from).put(to.targetState, pm)
        case _ => ()
      }
    }
    for (k <- allStates.iterator.asScala; i <- allStates.iterator.asScala; j <- allStates.iterator.asScala) {
      val reachIj: Option[PathMarker] = Option(reachableFrom.get(i).get(j))
      val reachIk: Option[PathMarker] = Option(reachableFrom.get(i).get(k))
      val reachKj: Option[PathMarker] = Option(reachableFrom.get(k).get(j))
      val reachIjThroughK: Option[PathMarker] = reachKj.flatMap(_ => reachIk)
      (reachIj, reachIjThroughK) match {
        // `i` -> `j` was not previously known to be possible
        case (None, Some(t)) => reachableFrom.get(i).put(j, t)

        // `i` -> `j` is now possible through a `Plus`
        case (_, Some(AlternationMarker.PLUS)) => reachableFrom.get(i).put(j, AlternationMarker.PLUS)

        // `i` -> `j` is not going to be improved
        case _ =>
      }
    }

    /** Find the shortest path between two different states */
    def shortestEmptyPath(from: Int, to: Int): List[PathMarker] = {
      var from1 = from
      val path = List.newBuilder[PathMarker]
      while (from1 != to) {
        val transition = reachableFrom.get(from1).get(to)
        path += transition
        val found = m1.transitionsMap(from1).get(transition)
        if (found == null) throw new IllegalStateException()
        else from1 = found.targetState()
      }
      path.result()
    }

    val states = m1.states
      .collect {
        case (from, M1.Character(c, to)) if preservedStates.contains(to) =>
          from -> (c -> Map(to -> Nil))
        case (from, M1.Character(c, to)) =>
          val newTargets = reachableFrom
            .get(to)
            .keySet
            .stream
            .filter(preservedStates.contains(_))
            .collect(Collectors.toMap((target: Integer) => target, (target: Integer) => shortestEmptyPath(to, target)))
            .asScala
            .toMap
          from -> (c -> newTargets)
      }
      .toMap

    val initials: Map[Integer, List[PathMarker]] = if (preservedStates.contains(m1.initial)) {
      Map(m1.initial -> Nil)
    } else {
      reachableFrom
        .get(m1.initial)
        .keySet
        .stream
        .filter(preservedStates.contains(_))
        .collect(Collectors.toMap((target: Integer) => target, (target: Integer) => shortestEmptyPath(m1.initial, target)))
        .asScala
        .toMap
    }

    val terminals = m1.accepting
    if (terminals.size > 1) {
      throw new IllegalArgumentException("Invalid M1 DFA: expected only one terminal state but got " + terminals)
    }

    M2(states, initials, terminals.iterator.next())
  }
}
