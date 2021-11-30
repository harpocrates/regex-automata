package automata

import scala.annotation.switch
import java.lang.{Character => JavaCharacter}

import java.util.OptionalInt;
// Follow https://www.labs.hpe.com/techreports/2012/HPL-2012-41R1.pdf

sealed abstract class Re
object Re extends RegexVisitor[Re, Re] {

  /** Matches nothing */
  final case object Epsilon extends Re

  /** Matches a literal character */
  final case class Character(c: Char) extends Re

  /** Matches two patterns, one after another */
  final case class Concat(lhs: Re, rhs: Re) extends Re

  /** Matches one of two patterns */
  final case class Union(lhs: Re, rhs: Re) extends Re

  /** Match the same pattern as many/few times as possible */
  final case class Kleene(lhs: Re, isLazy: Boolean) extends Re

  /** Match the same pattern as many/few times as possible, but at least once */
  final case class Plus(lhs: Re, isLazy: Boolean) extends Re

  /** Group sub-capture */
  final case class Group(arg: Re, idx: Int) extends Re

  def transform[O](visitor: RegexVisitor[Re, O], re: Re): O =
    re match {
      case Epsilon => visitor.visitEpsilon()
      case Character(c) => visitor.visitCharacter(c)
      case Concat(lhs, rhs) => visitor.visitConcatenation(lhs, rhs)
      case Union(Epsilon, rhs) => visitor.visitOptional(rhs, true)
      case Union(lhs, Epsilon) => visitor.visitOptional(lhs, false)
      case Union(lhs, rhs) => visitor.visitAlternation(lhs, rhs)
      case Kleene(lhs, isLazy) => visitor.visitKleene(lhs, isLazy)
      case Plus(lhs, isLazy) => visitor.visitPlus(lhs, isLazy)
      case Group(arg, idx) => visitor.visitGroup(arg, OptionalInt.of(idx))
    }

  override def visitEpsilon() =
    Epsilon

  override def visitCharacter(char: Char) =
    Character(char)

  override def visitConcatenation(lhs: Re, rhs: Re) =
    Concat(lhs, rhs)

  override def visitAlternation(lhs: Re, rhs: Re) =
    Union(lhs, rhs)

  override def visitKleene(lhs: Re, isLazy: Boolean) =
    Kleene(lhs, isLazy)

  override def visitOptional(lhs: Re, isLazy: Boolean) =
    if (isLazy) Union(Epsilon, lhs) else  Union(lhs, Epsilon)

  override def visitPlus(lhs: Re, isLazy: Boolean) =
    Plus(lhs, isLazy)

  override def visitGroup(arg: Re, groupIndex: OptionalInt) =
    if (groupIndex.isPresent()) Group(arg, groupIndex.getAsInt()) else arg

  def parse(src: String): Re = RegexParser.parse(Re, src)
}
