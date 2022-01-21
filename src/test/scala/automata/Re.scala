package automata

import automata.parser.{RegexParser, BuiltinClass => JBuiltinClass, Boundary => JBoundary, RegexVisitor, CharClassVisitor}
import scala.jdk.OptionConverters._
import java.util.OptionalInt;

sealed abstract class Re {
  def acceptRegex[A](regexVisitor: RegexVisitor[A, _]): A

  /** Pretty-print the regular expression into a string pattern */
  def rendered: String = acceptRegex(RegexPrinter).rendered
}
sealed abstract class CharClass extends Re {
  override def acceptRegex[A](regexVisitor: RegexVisitor[A, _]): A =
    acceptHelper(regexVisitor)

  def acceptCharClass[A](charClassVisitor: CharClassVisitor[A]): A

  // Help the type inference of `acceptRegex`
  private[this] def acceptHelper[A, B](regexVisitor: RegexVisitor[A, B]): A =
    regexVisitor.visitCharacterClass(acceptCharClass(regexVisitor))
}
object Re extends RegexVisitor[Re, CharClass] {

  def formatCodePoint(codePoint: Int): String =
    if (34 <= codePoint && codePoint <= 126) String.format("'%c'", codePoint)
    else String.format("U+%X", codePoint)

  /** Matches nothing */
  final case object Epsilon extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A =
      visitor.visitEpsilon()
  }

  /** Matches a literal unicode codepoint */
  final case class Character(codePoint: Int) extends CharClass {
    override def toString(): String =
      s"Character(${formatCodePoint(codePoint)})"

    override def acceptCharClass[A](visitor: CharClassVisitor[A]): A =
      visitor.visitCharacter(codePoint)
  }
  object Character {
    def apply(character: Char): Character =
      Character(character.asInstanceOf[Int])
  }

  /** Matches two patterns, one after another */
  final case class Concat(lhs: Re, rhs: Re) extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A = {
      val lhsA = lhs.acceptRegex(visitor)
      val rhsA = rhs.acceptRegex(visitor)
      visitor.visitConcatenation(lhsA, rhsA)
    }
  }

  /** Matches one of two patterns */
  final case class Alternation(lhs: Re, rhs: Re) extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A = {
      val lhsA = lhs.acceptRegex(visitor)
      val rhsA = rhs.acceptRegex(visitor)
      visitor.visitAlternation(lhsA, rhsA)
    }
  }

  /** Match the same pattern zero or once */
  final case class Optional(lhs: Re, isLazy: Boolean) extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A =
      visitor.visitOptional(lhs.acceptRegex(visitor), isLazy)
  }

  /** Match the same pattern as many/few times as possible */
  final case class Kleene(lhs: Re, isLazy: Boolean) extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A =
      visitor.visitKleene(lhs.acceptRegex(visitor), isLazy)
  }

  /** Match the same pattern as many/few times as possible, but at least once */
  final case class Plus(lhs: Re, isLazy: Boolean) extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A =
      visitor.visitPlus(lhs.acceptRegex(visitor), isLazy)
  }

  /** Match the same pattern a fixed range of times */
  final case class Repetition(
    lhs: Re,
    atLeast: Int,
    atMost: Option[Int],
    isLazy: Boolean
  ) extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A = {
      val lhsA = lhs.acceptRegex(visitor)
      visitor.visitRepetition(lhsA, atLeast, atMost.toJavaPrimitive, isLazy)
    }
  }

  /** Group sub-capture */
  final case class Group(arg: Re, index: Int) extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A =
      visitor.visitGroup(arg.acceptRegex(visitor), OptionalInt.of(index))
  }

  /** Zero-width boundary */
  final case class Boundary(boundary: JBoundary) extends Re {
    override def acceptRegex[A](visitor: RegexVisitor[A, _]): A =
      visitor.visitBoundary(boundary)
  }

  /** Range of characters */
  final case class CharacterRange(fromCodePoint: Int, toCodePoint: Int) extends CharClass {
    override def toString(): String =
      s"CharacterRange(${formatCodePoint(fromCodePoint)}, ${formatCodePoint(toCodePoint)})"

    override def acceptCharClass[A](visitor: CharClassVisitor[A]): A =
      visitor.visitRange(fromCodePoint, toCodePoint)
  }
  object CharacterRange {
    def apply(start: Char, end: Char): CharacterRange =
      CharacterRange(start.asInstanceOf[Int], end.asInstanceOf[Int])
  }


  /** Negated character class */
  final case class NegatedClass(rhs: CharClass) extends CharClass {
    override def acceptCharClass[A](visitor: CharClassVisitor[A]): A =
      visitor.visitNegated(rhs.acceptCharClass(visitor))
  }

  /** Intersection character class */
  final case class IntersectionClass(lhs: CharClass, rhs: CharClass) extends CharClass {
    override def acceptCharClass[A](visitor: CharClassVisitor[A]): A = {
      val lhsA = lhs.acceptCharClass(visitor)
      val rhsA = rhs.acceptCharClass(visitor)
      visitor.visitIntersection(lhsA, rhsA)
    }
  }

  /** Union character class */
  final case class UnionClass(lhs: CharClass, rhs: CharClass) extends CharClass {
    override def acceptCharClass[A](visitor: CharClassVisitor[A]): A = {
      val lhsA = lhs.acceptCharClass(visitor)
      val rhsA = rhs.acceptCharClass(visitor)
      visitor.visitUnion(lhsA, rhsA)
    }
  }

  /** Builtin character class */
  final case class BuiltinClass(cls: JBuiltinClass) extends CharClass {
    override def acceptCharClass[A](visitor: CharClassVisitor[A]): A =
      visitor.visitBuiltinClass(cls)
  }

  override def visitCharacter(codePoint: Int) =
    Re.Character(codePoint)

  override def visitRange(fromCodePoint: Int, toCodePoint: Int) =
    Re.CharacterRange(fromCodePoint, toCodePoint)

  override def visitNegated(negate: CharClass) =
    Re.NegatedClass(negate)

  override def visitIntersection(lhs: CharClass, rhs: CharClass) =
    Re.IntersectionClass(lhs, rhs)

  override def visitUnion(lhs: CharClass, rhs: CharClass) =
    Re.UnionClass(lhs, rhs)

  override def visitEpsilon() =
    Re.Epsilon

  override def visitConcatenation(lhs: Re, rhs: Re) =
    Re.Concat(lhs, rhs)

  override def visitAlternation(lhs: Re, rhs: Re) =
    Re.Alternation(lhs, rhs)

  override def visitKleene(lhs: Re, isLazy: Boolean) =
    Re.Kleene(lhs, isLazy)

  override def visitOptional(lhs: Re, isLazy: Boolean) =
    Re.Optional(lhs, isLazy)

  override def visitPlus(lhs: Re, isLazy: Boolean) =
    Re.Plus(lhs, isLazy)

  override def visitRepetition(lhs: Re, atLeast: Int, atMost: OptionalInt, isLazy: Boolean) =
    Re.Repetition(lhs, atLeast, atMost.toScala, isLazy)

  override def visitGroup(arg: Re, groupIndex: OptionalInt) =
    if (groupIndex.isPresent()) Re.Group(arg, groupIndex.getAsInt()) else arg

  override def visitBoundary(boundary: JBoundary) =
    Re.Boundary(boundary)

  override def visitCharacterClass(cls: CharClass) =
    cls

  override def visitBuiltinClass(cls: JBuiltinClass) =
    Re.BuiltinClass(cls)

  def parse(src: String): Re = RegexParser.parse(Re, src, false, false)
}
