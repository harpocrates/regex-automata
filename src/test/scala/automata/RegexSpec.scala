package automata

import java.util.regex.MatchResult
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RegexSpec extends AnyFlatSpec with Matchers {
  "((a)∗|b)(ab|b)" should "match 'aaab' in interpreted mode" in {
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

}
