package automata

import scala.collection.mutable
import java.util.{AbstractMap, Map => JavaMap, Set => JavaSet, Collections}
import scala.jdk.CollectionConverters._
import java.util.stream.{Collector, Collectors}

/** DFA obtained from a powerset construction on a reversed M2 NFA
  *
  * @note since this uses the powerset construction, each state is represented by a set
  * @param states states along with transition functions for each character
  * @param initial initial state
  * @param terminals terminal states
  */
final case class M3(
  states: Map[IntSet, Map[CodeUnitTransition, IntSet]],
  initial: IntSet,
  terminals: Set[IntSet]
) extends Dfa[IntSet, CodeUnitTransition, Unit] {

  val accepting: JavaSet[IntSet] = terminals.asJava

  def transitionsMap(state: IntSet): JavaMap[CodeUnitTransition, Fsm.Transition[IntSet, CodeUnitTransition, Unit]] = {

    final class NoAnnotTrans(to: IntSet) extends Fsm.Transition[IntSet, CodeUnitTransition, Unit] {
      def annotation = ()
      def targetState = to
      def dotLabel(from: IntSet, over: CodeUnitTransition) = over.dotLabel
    }

    states.get(state) match {
      case None => Collections.emptyMap()
      case Some(ts) =>
        ts.view
          .mapValues(new NoAnnotTrans(_))
          .toMap[CodeUnitTransition, Fsm.Transition[IntSet, CodeUnitTransition, Unit]]
          .asJava
    }
  }

  /** All reachable states
    *
    * In the worst case, this is exactly the powerset
    */
  def nonEmptyPowerStates: Set[IntSet] =
    states
      .keySet
      .union(terminals)
      .+(initial)
      .toSet

  /** Run a regex on an input
    *
    * @param input input string, must contain only characters from the BMP (no surrogates)
    * @return the array of states if it matched (in the order seen)
    */
  def captureSimulate(input: CharSequence): Option[Array[IntSet]] = {
    var currentState: IntSet = initial
    var pos: Int = input.length
    var simulatedOff: Int = 0
    val simulated = new Array[IntSet](input.length + 1)

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
    var currentState: IntSet = initial
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
    val reverseTransitions: Map[Integer, Set[(CodeUnitTransition, Integer)]] = m2
      .states
      .toList
      .flatMap {
        case (from, (chars, tos)) =>
          for {
            to <- tos.keys
            c <- chars
          } yield to -> (c -> from)
      }
      .toSet[(Integer, (CodeUnitTransition, Integer))]
      .groupMap(_._1)(_._2)

    val initial = IntSet.of(m2.terminal)
    val states: mutable.Map[IntSet, Map[CodeUnitTransition, IntSet]] = mutable.Map.empty
    val toVisit: mutable.Set[IntSet] = mutable.Set(initial)

    while (toVisit.nonEmpty) {
      val powerState: IntSet = toVisit.head
      toVisit.remove(powerState)

      // Look up all the transitions from the constituent states
      val transitions: Map[CodeUnitTransition, IntSet] = powerState
        .stream
        .boxed
        .collect(Collectors.toSet[Integer])
        .asScala
        .flatMap(reverseTransitions.getOrElse(_, Set.empty))
        .groupMap(_._1)(_._2)
        .view
        .mapValues(ints => new IntSet(ints.asJava))
        .toMap

      states += powerState -> transitions

      for ((_, outState) <- transitions) {
        if (!states.contains(outState)) toVisit.addOne(outState)
      }
    }

    val terminals: Set[IntSet] = states
      .keys
      .filter(key => key.stream.anyMatch(m2.initial.keySet.contains(_)))
      .toSet

    M3(
      states.toMap,
      initial,
      terminals
    )
  }

}

