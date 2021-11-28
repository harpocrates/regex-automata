package automata

import java.util.regex.MatchResult
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RegexSpec extends AnyFlatSpec with Matchers {
  "((a)∗|b)(ab|b)" should "match 'aaab'" in {
    val pattern = DfaPattern.interpretted("((a)*|b)(ab|b)")

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
}
