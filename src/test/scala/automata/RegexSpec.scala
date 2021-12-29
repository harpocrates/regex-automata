package automata

import scala.jdk.CollectionConverters._
import java.util.regex.MatchResult
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RegexSpec extends AnyFlatSpec with Matchers {
  "((a)*|b)(ab|b)" should "match 'aaab' in interpreted mode" in {
    val pattern = DfaPattern.interpreted("((a)*|b)(ab|b)")

    val matches: Boolean = pattern.checkMatch("aaab")
    val matched: MatchResult = pattern.captureMatch("aaab")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 3, "regex has right number of captures")

    assert(matched.start(0) == 0, "first group start")
    assert(matched.end(0) == 3, "first group end")
    assert(matched.group(0) == "aaa", "first group")

    assert(matched.start(1) == 2, "second group start")
    assert(matched.end(1) == 3, "second group end")
    assert(matched.group(1) == "a", "second group")

    assert(matched.start(2) == 3, "third group start")
    assert(matched.end(2) == 4, "third group end")
    assert(matched.group(2) == "b", "third group")
  }

  it should "match 'aaab' in compiled mode" in {
    val pattern = DfaPattern.compile("((a)*|b)(ab|b)")

    val matches: Boolean = pattern.checkMatch("aaab")
    val matched: MatchResult = pattern.captureMatch("aaab")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 3, "regex has right number of captures")

    assert(matched.start(0) == 0, "first group start")
    assert(matched.end(0) == 3, "first group end")
    assert(matched.group(0) == "aaa", "first group")

    assert(matched.start(1) == 2, "second group start")
    assert(matched.end(1) == 3, "second group end")
    assert(matched.group(1) == "a", "second group")

    assert(matched.start(2) == 3, "third group start")
    assert(matched.end(2) == 4, "third group end")
    assert(matched.group(2) == "b", "third group")
  }

  it should "match 'bab' in interpreted mode" in {
    val pattern = DfaPattern.interpreted("((a)*|b)(ab|b)")

    val matches: Boolean = pattern.checkMatch("bab")
    val matched: MatchResult = pattern.captureMatch("bab")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 3, "regex has right number of captures")

    assert(matched.start(0) == 0, "first group start")
    assert(matched.end(0) == 1, "first group end")
    assert(matched.group(0) == "b", "first group")

    assert(matched.start(1) == -1, "second group start")
    assert(matched.end(1) == -1, "second group end")
    assert(matched.group(1) == null, "second group")

    assert(matched.start(2) == 1, "third group start")
    assert(matched.end(2) == 3, "third group end")
    assert(matched.group(2) == "ab", "third group")
  }

  it should "match 'bab' in compiled mode" in {
    val pattern = DfaPattern.compile("((a)*|b)(ab|b)")

    val matches: Boolean = pattern.checkMatch("bab")
    val matched: MatchResult = pattern.captureMatch("bab")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 3, "regex has right number of captures")

    assert(matched.start(0) == 0, "first group start")
    assert(matched.end(0) == 1, "first group end")
    assert(matched.group(0) == "b", "first group")

    assert(matched.start(1) == -1, "second group start")
    assert(matched.end(1) == -1, "second group end")
    assert(matched.group(1) == null, "second group")

    assert(matched.start(2) == 1, "third group start")
    assert(matched.end(2) == 3, "third group end")
    assert(matched.group(2) == "ab", "third group")
  }

  it should "not match 'abab' in interpreted mode" in {
    val pattern = DfaPattern.interpreted("((a)*|b)(ab|b)")

    val matches: Boolean = pattern.checkMatch("abab")
    val matched: MatchResult = pattern.captureMatch("abab")
    assert(!matches, "regex does not checkMatch")
    assert(matched == null, "regex does not captureMatch")
  }

  it should "not match 'abab' in compiled mode" in {
    val pattern = DfaPattern.compile("((a)*|b)(ab|b)")

    val matches: Boolean = pattern.checkMatch("abab")
    val matched: MatchResult = pattern.captureMatch("abab")
    assert(!matches, "regex does not checkMatch")
    assert(matched == null, "regex does not captureMatch")
  }

  "(x+x+)+y" should "match 'xxxy' in interpreted mode" in {
    val pattern = DfaPattern.interpreted("(x+x+)+y")

    val matches: Boolean = pattern.checkMatch("xxxy")
    val matched: MatchResult = pattern.captureMatch("xxxy")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 1, "regex has right number of captures")

    assert(matched.start(0) == 0, "first group start")
    assert(matched.end(0) == 3, "first group end")
    assert(matched.group(0) == "xxx", "first group")
  }

  it should "match 'xxxy' in compiled mode" in {
    val pattern = DfaPattern.compile("(x+x+)+y")

    val matches: Boolean = pattern.checkMatch("xxxy")
    val matched: MatchResult = pattern.captureMatch("xxxy")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 1, "regex has right number of captures")

    assert(matched.start(0) == 0, "first group start")
    assert(matched.end(0) == 3, "first group end")
    assert(matched.group(0) == "xxx", "first group")
  }

  it should "not match 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx' in interpreted mode" in {
    val pattern = DfaPattern.interpreted("(x+x+)+y")

    val matches: Boolean = pattern.checkMatch("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
    val matched: MatchResult = pattern.captureMatch("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
    assert(!matches, "regex does not checkMatch")
    assert(matched == null, "regex does not captureMatch")
  }

  it should "not match 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx' in compiled mode" in {
    val pattern = DfaPattern.compile("(x+x+)+y")

    val matches: Boolean = pattern.checkMatch("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
    val matched: MatchResult = pattern.captureMatch("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
    assert(!matches, "regex does not checkMatch")
    assert(matched == null, "regex does not captureMatch")
  }

  "uv" should "match 'uv' in interpreted mode" in {
    val pattern = DfaPattern.interpreted("uv")

    val matches: Boolean = pattern.checkMatch("uv")
    val matched: MatchResult = pattern.captureMatch("uv")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 0, "regex has right number of captures")
  }

  it should "match 'uv' in compiled mode" in {
    val pattern = DfaPattern.compile("uv")

    val matches: Boolean = pattern.checkMatch("uv")
    val matched: MatchResult = pattern.captureMatch("uv")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 0, "regex has right number of captures")
  }

  "[a𐐷]€\\$" should "match '𐐷€$' in interpreted mode" in {
    val pattern = DfaPattern.interpreted("[a𐐷]€\\$")

    val matches: Boolean = pattern.checkMatch("𐐷€$")
    val matched: MatchResult = pattern.captureMatch("𐐷€$")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 0, "regex has right number of captures")
  }

  "[a𐐷]€\\$" should "match '𐐷€$' in compiled mode" in {
    val pattern = DfaPattern.compile("[a𐐷]€\\$")

    val matches: Boolean = pattern.checkMatch("𐐷€$")
    val matched: MatchResult = pattern.captureMatch("𐐷€$")
    assert(matches, "regex checkMatch-es")
    assert(matched != null, "regex captureMatch-es")

    assert(matched.groupCount == 0, "regex has right number of captures")
  }

  val phoneRe = raw"(?:\+?(\d{1,3}))?[-. (]*(\d{3})[-. )]*(\d{3})[-. ]*(\d{4})(?: *x(\d+))?"
  phoneRe should "match phone numbers in compiled mode" in {
    val pattern = DfaPattern.compile(phoneRe)

    val shouldMatch = List(
      "18005551234" -> List("1", "800", "555", "1234", null),
      "1 800 555 1234" -> List("1", "800", "555", "1234", null),
      "+1 800 555-1234" -> List("1", "800", "555", "1234", null),
      "+86 800 555 1234" -> List("86", "800", "555", "1234", null),
      "1-800-555-1234" -> List("1", "800", "555", "1234", null),
      "1 (800) 555-1234" -> List("1", "800", "555", "1234", null),
      "(800)555-1234" -> List(null, "800", "555", "1234", null),
      "(800) 555-1234" -> List(null, "800", "555", "1234", null),
      "(800)5551234" -> List(null, "800", "555", "1234", null),
      "800-555-1234" -> List(null, "800", "555", "1234", null),
      "800.555.1234" -> List(null, "800", "555", "1234", null),
      "800 555 1234x5678" -> List(null, "800", "555", "1234", "5678"),
      "8005551234 x5678" -> List(null, "800", "555", "1234", "5678"),
      "1    800    555-1234" -> List("1", "800", "555", "1234", null),
      "1----800----555-1234" -> List("1", "800", "555", "1234", null)
    )

    for ((str, matchedGroups) <- shouldMatch) {
      val matches: Boolean = pattern.checkMatch(str)
      val matched: ArrayMatchResult = pattern.captureMatch(str)
      assert(matches, "regex checkMatch-es")
      assert(matched != null, "regex captureMatch-es")

      assert(matchedGroups == matched.groups.asScala.toList, "groups match")
    }
  }
}
