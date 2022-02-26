package automata

import java.util.OptionalInt;
import automata.parser.RegexParser

trait RegexStripGroups extends ReBuilder {

  /** Decision for whether to keep a group in the output regex
    *
    * @param groupIdx index of group under consideration
    * @return whether to keep the group
    */
  def keepGroup(groupIdx: Int): Boolean

  override def visitGroup(arg: Re, groupIndex: OptionalInt) =
    if (groupIndex.isPresent()) {
      val groupIdx = groupIndex.getAsInt()
      if (keepGroup(groupIdx)) Re.Group(arg, groupIdx) else arg
    } else {
      arg
    }
}
object RegexStripGroups extends RegexStripGroups {
  def keepGroup(groupIdx: Int): Boolean = false

  /** Strip explicit capture groups from a pattern and re-print it */
  def stripCaptureGroups(pattern: String): String = RegexParser
    .parse(RegexStripGroups, pattern, 0, false, false)
    .acceptRegex(RegexPrinter)
    .rendered
}
