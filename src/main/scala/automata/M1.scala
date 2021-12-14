package automata

// Follow https://www.labs.hpe.com/techreports/2012/HPL-2012-41R1.pdf
import scala.jdk.CollectionConverters._

/** NFA corresponding to regex
  *
  * This includes start/end group transitions (which signify the start and end
  * of a regex group) as well as plus/minus transitions (which signify a
  * prioritized alternation).
  *
  * @param states states and the transition out of those states
  * @param initial initial state
  * @param terminal final state
  */
final case class M1(
  states: Map[Int, M1.Transition],
  initial: Int,
  terminal: Int
) {

  /** Generate the source-code for a valid DOT graph
    *
    * Compile the `.dot` file using `dot -Tsvg m1.dot > m1.svg`
    */
  def graphVizSrc: String = {
    val builder = new StringBuilder()
    builder ++= s"digraph m1 {\n"
    builder ++= s"  rankdir = LR;\n"
    builder ++= s"  node [shape = doublecircle, label = \"\\N\"]; $terminal;\n"
    builder ++= s"  node [shape = none, label = \"\"]; \"init\";\n"
    builder ++= s"  node [shape = circle, label = \"\\N\"];\n"
    builder ++= s"  \"init\" -> \"$initial\";\n"

    for ((from, transition) <- states) {
      transition match {
        case M1.Character(c, to) =>
          builder ++= s"  \"$from\" -> \"$to\" [label=\"$c\"];\n"
        case M1.PlusMinus(plus, minus) =>
          builder ++= s"  \"$from\" -> \"$plus\" [label=\"+\"];\n"
          builder ++= s"  \"$from\" -> \"$minus\" [label=\"-\"];\n"
        case M1.GroupStart(groupIdx, to) =>
          builder ++= s"  \"$from\" -> \"$to\" [label=<S<sub>$groupIdx</sub>>];\n"
        case M1.GroupEnd(groupIdx, to) =>
          builder ++= s"  \"$from\" -> \"$to\" [label=<E<sub>$groupIdx</sub>>];\n"
      }
    }

    builder ++= s"}"
    builder.result()
  }

  /** Compute the sequence of group tags obtained from searching from `from` to
    * `to` along non-character paths, following `Plus` before `Minus`
    *
    * @param from where in the DFA to start
    * @param to where in the DFA to end
    */
  def pi(from: Int, to: Int): Option[List[M1.GroupTransition]] = {
    if (from == to || from == terminal) {
      Some(Nil)
    } else {
      states(from) match {
        case M1.PlusMinus(plus, minus) =>
          pi(plus, to) orElse pi(minus, to)
        case group: M1.GroupTransition =>
          pi(group.to, to).map(group :: _)
        case _ =>
          None
      }
    }
  }

}
object M1 {

  sealed abstract class Transition
  sealed abstract class GroupTransition extends Transition {
    def to: Int
  }
  final case class Character(c: Char, to: Int) extends Transition
  final case class PlusMinus(plus: Int, minus: Int) extends Transition
  final case class GroupStart(groupIdx: Int, to: Int) extends GroupTransition
  final case class GroupEnd(groupIdx: Int, to: Int) extends GroupTransition

  /** Convert a regex AST into an equivalent M1 NFA
    *
    * @param re regex to convert
    * @return equivalent NFA
    */
  def fromRe(re: Re): M1 = {

    var nextState: Int = 1
    def freshState(): Int = {
      val s = nextState
      nextState += 1
      s
    }

    val states = Map.newBuilder[Int, M1.Transition]

    /** @param re regex
      * @param to state at which to end
      * @return state at which the regex starts
      */
    def convert(re: Re, to: Int): Int =
      re match {
        case Re.Epsilon =>
          to

        case c: CharClass =>
          val codePointSet = c.acceptCharClass(CodePointSetVisitor.INSTANCE)

          if (!codePointSet.difference(IntRangeSet.of(CodePointSetVisitor.BMP_RANGE)).isEmpty()) {
            throw new IllegalArgumentException("Codepoints outside the BMP aren't supported yet")
          }

          val from = freshState()
          codePointSet.iterator.asScala.map(_.intValue().asInstanceOf[Char]).toList match {
            case Nil => ()
            case ch :: chs =>
              states += from -> chs.foldLeft[Transition](M1.Character(ch, to)) {
                (acc: Transition, c: Char) =>
                  val accFrom = freshState()
                  val cFrom = freshState()
                  states += accFrom -> acc
                  states += cFrom -> M1.Character(c, to)
                  M1.PlusMinus(accFrom, cFrom)
              }
          }
          from


     //   case Re.Character(c) =>
     //     if (java.lang.Character.charCount(c) == 1) {
     //       val from = freshState()
     //       states += from -> M1.Character(c.asInstanceOf[Char], to)
     //       from
     //     } else {
     //       val from = freshState()
     //       val low = freshState()
     //       states += from -> M1.Character(java.lang.Character.highSurrogate(c), low)
     //       states += low -> M1.Character(java.lang.Character.lowSurrogate(c), to)
     //       from
     //     }

        case Re.Concat(lhs, rhs) =>
          val mid = convert(rhs, to)
          convert(lhs, mid)

        case Re.Alternation(lhs, rhs) =>
          val lhsFrom = convert(lhs, to)
          val rhsFrom = convert(rhs, to)
          val from = freshState()
          states += from -> M1.PlusMinus(lhsFrom, rhsFrom)
          from

        case Re.Optional(lhs, isLazy) =>
          val (first, second) = if (isLazy) (Re.Epsilon, lhs) else (lhs, Re.Epsilon)
          convert(Re.Alternation(first, second), to)

        case Re.Kleene(lhs, isLazy) =>
          val lhsTo = freshState()
          val from = freshState()
          val lhsFrom = convert(lhs, lhsTo)
          val (plusState, minusState) = if (isLazy) (to, lhsFrom) else (lhsFrom, to)
          val transition = M1.PlusMinus(plusState, minusState)
          states += lhsTo -> transition
          states += from -> transition
          from

        case Re.Plus(lhs, isLazy) =>
          val lhsTo = freshState()
          val lhsFrom = convert(lhs, lhsTo)
          val (plusState, minusState) = if (isLazy) (to, lhsFrom) else (lhsFrom, to)
          val transition = M1.PlusMinus(plusState, minusState)
          states += lhsTo -> transition
          lhsFrom

        case Re.Group(arg, groupIdx) =>
          val argTo = freshState()
          val from = freshState()
          val argFrom = convert(arg, argTo)
          states += argTo -> M1.GroupEnd(groupIdx, to)
          states += from -> M1.GroupStart(groupIdx, argFrom)
          from

        case Re.Repetition(arg, atLeast, atMost, isLazy) =>
          // `atMost` part - this is either a kleene star or a fixed repetition
          var tailTo = atMost match {
            case None =>
              convert(Re.Kleene(arg, isLazy), to)

            case Some(m) =>
              var i = 0
              var tailToPart = to
              while (i < (m - atLeast)) {
                val tailStart = convert(arg, tailToPart)
                val forkState = freshState()
                val (plusState, minusState) = if (isLazy) (tailToPart, tailStart) else (tailStart, tailToPart)
                states += forkState -> M1.PlusMinus(plusState, minusState)
                tailToPart = forkState
                i += 1
              }
              tailToPart
          }

          var i = 0
          while (i < atLeast) {
            tailTo = convert(arg, tailTo)
            i += 1
          }
          tailTo

        case _ => ???
      }

    val terminal = freshState()
    val initial = convert(re, terminal)
    M1(states.result(), initial, terminal)
  }
}

