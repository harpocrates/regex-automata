package automata

import automata.parser.{RegexVisitor, BuiltinClass, Boundary}
import scala.annotation.switch
import java.lang.Character.{UnicodeBlock, UnicodeScript}
import java.util.OptionalInt

/** Rendered output
  *
  * @param rendered string output
  * @param priority priority context of the output (used to add brackets/parens)
  * @param singleCodePoint hack to avoid brackets on a char class with only one codepoint
  */
final case class RenderedWithPriority(
  rendered: String,
  priority: Priority.Value,
  singleCodePoint: Option[Int] = None
) {

  /** Add parens only if the priority of this element is lower than the context
    *
    * @param context surrounding context
    * @return possibly parenthesized output
    */
  def parens(context: Priority.Value) =
    if (priority < context) s"(?:$rendered)" else rendered

  /** Add brackets only if the priority of this element is lower than the context
    *
    * @param context surrounding context
    * @return possibly bracketed output
    */
  def brackets(context: Priority.Value) =
    if (priority < context) s"[$rendered]" else rendered
}

/** Priority of operators in regular expressions. Used to determine when
  * parentheses or brackets are required.
  */
object Priority extends Enumeration {
  val Bottom, Alternation, Concatenation, Quantifier, Intersection, Union, Range, Top = Value
}

object RegexPrinter extends RegexVisitor[RenderedWithPriority, RenderedWithPriority] {

  /** Render a code point in the context of an outer regular expression */
  private def regexCodePoint(codePoint: Int): String = {
    val ch = codePoint.toChar
    (ch: @switch) match {
      case '\n' => "\\n"
      case '(' | ')' | '[' | ']' | '{' | '}' | '*' | '+' | '?' | '|' | '^' | '$' | '.' | '\\' => s"\\$ch"
      case _ =>
        if (Character.isBmpCodePoint(codePoint) && !Character.isSurrogate(ch)) ch.toString
        else f"\\x{$codePoint%X}"
    }
  }

  /** Render a code point in the context of a character class */
  private def charClassCodePoint(codePoint: Int) = {
    val ch = codePoint.toChar
    (ch: @switch) match {
      case '\n' => "\\n"
      case '[' | ']' | '^' | '\\' | '-' | '~' | '&' => "\\" + ch
      case _ =>
        if (Character.isBmpCodePoint(codePoint) && !Character.isSurrogate(ch)) ch.toString
        else f"\\x{$codePoint%X}"
    }
  }


  override def visitCharacter(codePoint: Int, flags: Int) =
    RenderedWithPriority(
      rendered = charClassCodePoint(codePoint),
      priority = Priority.Top,
      singleCodePoint = Some(codePoint)
    )

  override def visitRange(fromCodePoint: Int, toCodePoint: Int, flags: Int) =
    RenderedWithPriority(
      rendered = charClassCodePoint(fromCodePoint) + "-" +  charClassCodePoint(toCodePoint),
      priority = Priority.Range
    )

  override def visitNegated(negate: RenderedWithPriority) =
    RenderedWithPriority(
      rendered = s"[^${negate.brackets(Priority.Intersection)}]",
      priority = Priority.Top
    )

  override def visitIntersection(lhs: RenderedWithPriority, rhs: RenderedWithPriority) =
    RenderedWithPriority(
      rendered = lhs.brackets(Priority.Intersection) + "&&" + rhs.brackets(Priority.Union),
      priority = Priority.Intersection
    )

  override def visitUnion(lhs: RenderedWithPriority, rhs: RenderedWithPriority) =
    RenderedWithPriority(
      rendered = lhs.brackets(Priority.Union) + rhs.brackets(Priority.Range),
      priority = Priority.Union
    )

  override def visitEpsilon() =
    RenderedWithPriority(rendered = "", priority = Priority.Top)

  override def visitConcatenation(lhs: RenderedWithPriority, rhs: RenderedWithPriority) =
    RenderedWithPriority(
      rendered = lhs.parens(Priority.Concatenation) + rhs.parens(Priority.Quantifier),
      priority = Priority.Concatenation
    )

  override def visitAlternation(lhs: RenderedWithPriority, rhs: RenderedWithPriority) =
    RenderedWithPriority(
      rendered = lhs.parens(Priority.Alternation) + "|" + rhs.parens(Priority.Concatenation),
      priority = Priority.Alternation
    )

  override def visitKleene(lhs: RenderedWithPriority, isLazy: Boolean) =
    RenderedWithPriority(
      rendered = {
        val left = lhs.parens(Priority.Intersection)
        (if (left == "") "(?:)" else left) + (if (isLazy) "*?" else "*")
      },
      priority = Priority.Quantifier
    )

  override def visitOptional(lhs: RenderedWithPriority, isLazy: Boolean) =
    RenderedWithPriority(
      rendered = {
        val left = lhs.parens(Priority.Intersection)
        (if (left == "") "(?:)" else left) + (if (isLazy) "??" else "?")
      },
      priority = Priority.Quantifier
    )

  override def visitPlus(lhs: RenderedWithPriority, isLazy: Boolean) =
    RenderedWithPriority(
      rendered = {
        val left = lhs.parens(Priority.Intersection)
        (if (left == "") "(?:)" else left) + (if (isLazy) "+?" else "+")
      },
      priority = Priority.Quantifier
    )

  override def visitRepetition(lhs: RenderedWithPriority, atLeast: Int, atMost: OptionalInt, isLazy: Boolean) =
    RenderedWithPriority(
      rendered = {
        var quantifier = s"{$atLeast,"
        if (atMost.isPresent) quantifier += atMost.getAsInt.toString
        quantifier += "}"
        if (isLazy) quantifier += "?"
        val left = lhs.parens(Priority.Intersection)
        (if (left == "") "(?:)" else left) + quantifier
      },
      priority = Priority.Quantifier
    )

  override def visitGroup(arg: RenderedWithPriority, groupIndex: OptionalInt) =
    RenderedWithPriority(
      rendered = if (groupIndex.isPresent) s"(${arg.rendered})" else arg.parens(Priority.Top),
      priority = Priority.Top
    )

  override def visitBoundary(boundary: Boundary) =
    RenderedWithPriority(
      rendered = boundary match {
        case Boundary.BEGINNING_OF_LINE => "^"
        case Boundary.END_OF_LINE => "$"
        case Boundary.WORD_BOUNDARY => "\\b"
        case Boundary.NON_WORD_BOUNDARY => "\\B"
        case Boundary.BEGINNING_OF_INPUT => "\\A"
        case Boundary.END_OF_INPUT_OR_TERMINATOR => "\\Z"
        case Boundary.END_OF_INPUT => "\\z"
      },
      priority = Priority.Top
    )

  override def visitCharacterClass(cls: RenderedWithPriority) =
    RenderedWithPriority(
      rendered = cls.singleCodePoint match {
        case Some(codePoint) => regexCodePoint(codePoint)
        case None => cls.brackets(Priority.Top)
      },
      priority = Priority.Top
    )

  override def visitBuiltinClass(cls: BuiltinClass, flags: Int) =
    RenderedWithPriority(
      rendered = cls match {
        case BuiltinClass.DOT => "."
        case BuiltinClass.DIGIT => "\\d"
        case BuiltinClass.NON_DIGIT => "\\D"
        case BuiltinClass.HORIZONTAL_WHITE_SPACE => "\\h"
        case BuiltinClass.NON_HORIZONTAL_WHITE_SPACE => "\\H"
        case BuiltinClass.WHITE_SPACE => "\\s"
        case BuiltinClass.NON_WHITE_SPACE => "\\S"
        case BuiltinClass.VERTICAL_WHITE_SPACE => "\\v"
        case BuiltinClass.NON_VERTICAL_WHITE_SPACE => "\\V"
        case BuiltinClass.WORD => "\\w"
        case BuiltinClass.NON_WORD => "\\W"
      },
      priority = Priority.Top
    )

  override def visitUnicodeBlock(block: UnicodeBlock, negated: Boolean, flags: Int) =
    RenderedWithPriority(
      rendered = s"\\${if (negated) "P" else "p"}{In$block}",
      priority = Priority.Top
    )

  override def visitUnicodeScript(script: UnicodeScript, negated: Boolean, flags: Int) =
    RenderedWithPriority(
      rendered = s"\\${if (negated) "P" else "p"}{Is$script}",
      priority = Priority.Top
    )
}
