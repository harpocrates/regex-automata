package automata

import org.scalactic.source.Position
import org.scalatest.funspec.AnyFunSpec
import java.util.regex.MatchResult
import scala.collection.immutable.ListMap
import java.lang.{StringBuilder => JavaStringBuilder}

class RegexSpec extends AnyFunSpec {

  private def testMatching(
    pattern: String,
    expectedGroupCount: Int,
    inputs: Map[String, Option[Seq[(Int, Int, String)]]]
  )(implicit
    pos: Position
  ): Unit = {
    def testUsing(p: DfaPattern): Unit = {
      for ((input, expectedOutput) <- inputs) {

        // ScalaTest chokes if a test name is an invalid UTF-16 string, so patch those
        val testName: String = input
          .codePoints()
          .map(i => if (0xD800 <= i && i <= 0xDFFF) '?'.toInt else i)
          .collect[JavaStringBuilder](() => new JavaStringBuilder(), _.appendCodePoint(_), _.append(_))
          .toString

        it(if (testName.isEmpty) "<empty>" else testName) {
          val matches: Boolean = p.checkMatch(input)
          val matched: MatchResult = p.captureMatch(input)
          val matchedLookingAt: MatchResult = p.captureLookingAt(input)

          expectedOutput match {
            case None =>
              assert(!matches, "checkMatch should fail to match")
              assert(matched == null, "captureMatch should return a null match")

            case Some(groups) =>
              assert(matches, "checkMatch should successfully match")
              assert(matched != null, "captureMatch should return a non-null match")
              assert(matchedLookingAt != null, "captureLookingAt should return a non-null match")
              assert(expectedGroupCount == matched.groupCount, "count of capture groups in pattern")

              val patchedGroups = (0, input.length, input) +: groups
              assert(groups.length == matched.groupCount)
              for (i <- 0 until groups.length) {
                val (expectedStart, expectedEnd, expectedCapture) = patchedGroups(i)
                assert(matched.start(i) == expectedStart, s"start index of capture group $i")
                assert(matched.end(i) == expectedEnd, s"end index of capture group $i")
                assert(matched.group(i) == expectedCapture, s"value of capture group $i")
              }
          }
        }
      }
    }

    describe(if (pattern.isEmpty) "<empty>" else pattern) {
      describe("interpreted")(testUsing(DfaPattern.interpreted(pattern)))
      describe("compiled")(testUsing(DfaPattern.compile(pattern)))
    }
  }

  private val NoGroup = (-1, -1, null)

  // Simplest regex
  testMatching(
    pattern = "",
    expectedGroupCount = 0,
    inputs = ListMap(
      "" -> Some(Nil),
      "a" -> None,
      "b" -> None
    )
  )

  // Simple couple states
  testMatching(
    pattern = "uv",
    expectedGroupCount = 0,
    inputs = ListMap(
      "uv" -> Some(Nil),
      "uvw" -> None,
      "uw" -> None
    )
  )

  // Example from <https://www.labs.hpe.com/techreports/2012/HPL-2012-41R1.pdf>
  testMatching(
    pattern = "((a)*|b)(ab|b)",
    expectedGroupCount = 3,
    inputs = ListMap(
      "aaab" -> Some(List(
        (0, 3, "aaa"),
        (2, 3, "a"),
        (3, 4, "b")
      )),
      "bab" -> Some(List(
        (0, 1, "b"),
        NoGroup,
        (1, 3, "ab")
      )),
      "abab" -> None
    )
  )

  // Catastrophic backtracking from <https://www.regular-expressions.info/catastrophic.html>
  testMatching(
    pattern = "(x+x+)+y",
    expectedGroupCount = 1,
    inputs = ListMap(
      "xxxy" -> Some(List(
        (0, 3, "xxx")
      )),
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxy" -> Some(List(
        (0, 45, "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
      )),
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" -> None
    )
  )

  // Matching codepoints in supplementary plane
  testMatching(
    pattern = "(€[a𐐷])%",
    expectedGroupCount = 1,
    inputs = ListMap(
      "€a%" -> Some(List((0, 2, "€a"))),
      "€𐐷%" -> Some(List((0, 3, "€𐐷"))),
      "€\uD801\uDC37%" -> Some(List((0, 3, "€𐐷"))),
      "€b%" -> None,
      "€\uD801%" -> None,
      "€\uD801\uDC38%" -> None
    )
  )

  // Matching character classes that are too big to enumerate
  testMatching(
    pattern = "a.c",
    expectedGroupCount = 0,
    inputs = ListMap(
      "abc" -> Some(Nil),
      "a𐐷c" -> Some(Nil),
      "ab" -> None,
      "a\uD801\uD801b" -> None
    )
  )

  // Matching decimal numbers divisble by 4
  testMatching(
    pattern = raw"-?(?:\d*[02468][048]|\d*[13579][26]|[048])",
    expectedGroupCount = 0,
    inputs = ListMap(
      "0" -> Some(Nil),
      "3" -> None,
      "4" -> Some(Nil),
      "21" -> None,
      "1484" -> Some(Nil),
      "3503" -> None,
      "123456" -> Some(Nil)
    )
  )

  // Matching binary numbers divisible by 7
  testMatching(
    pattern = raw"(?:0|111|100(?:(?:1|00)0)*011|(?:101|100(?:(?:1|00)0)*(?:1|00)1)(?:1(?:(?:1|00)0)*(?:1|00)1)*(?:01|1(?:(?:1|00)0)*011)|(?:110|100(?:(?:1|00)0)*010|(?:101|100(?:(?:1|00)0)*(?:1|00)1)(?:1(?:(?:1|00)0)*(?:1|00)1)*(?:00|1(?:(?:1|00)0)*010))(?:1|0(?:1(?:(?:1|00)0)*(?:1|00)1)*(?:00|1(?:(?:1|00)0)*010))*0(?:1(?:(?:1|00)0)*(?:1|00)1)*(?:01|1(?:(?:1|00)0)*011))*",
    expectedGroupCount = 0,
    inputs = ListMap(
      Integer.toBinaryString(0) -> Some(Nil),
      Integer.toBinaryString(7 * 34 + 4) -> None,
      Integer.toBinaryString(7 * 73) -> Some(Nil),
      Integer.toBinaryString(7 * 129 + 2) -> None,
      Integer.toBinaryString(7 * 132) -> Some(Nil),
    )
  )

  // Matching phone numbers
  testMatching(
    pattern = raw"(?:\+?(\d{1,3}))?[-. (]*(\d{3})[-. )]*(\d{3})[-. ]*(\d{4})(?: *x(\d+))?",
    expectedGroupCount = 5,
    inputs = ListMap(
      "18005551234" -> Some(List(
        (0, 1, "1"),
        (1, 4, "800"),
        (4, 7, "555"),
        (7, 11, "1234"),
        NoGroup
      )),
      "1 800 555 1234" -> Some(List(
        (0, 1, "1"),
        (2, 5, "800"),
        (6, 9, "555"),
        (10, 14, "1234"),
        NoGroup
      )),
      "+1 800 555-1234" -> Some(List(
        (1, 2, "1"),
        (3, 6, "800"),
        (7, 10, "555"),
        (11, 15, "1234"),
        NoGroup
      )),
      "86 800 555-1234" -> Some(List(
        (0, 2, "86"),
        (3, 6, "800"),
        (7, 10, "555"),
        (11, 15, "1234"),
        NoGroup
      )),
      "1-800-555-1234" -> Some(List(
        (0, 1, "1"),
        (2, 5, "800"),
        (6, 9, "555"),
        (10, 14, "1234"),
        NoGroup
      )),
      "1 (800) 555-1234" -> Some(List(
        (0, 1, "1"),
        (3, 6, "800"),
        (8, 11, "555"),
        (12, 16, "1234"),
        NoGroup
      )),
      "(800)555-1234" -> Some(List(
        NoGroup,
        (1, 4, "800"),
        (5, 8, "555"),
        (9, 13, "1234"),
        NoGroup
      )),
      "(800) 555-1234" -> Some(List(
        NoGroup,
        (1, 4, "800"),
        (6, 9, "555"),
        (10, 14, "1234"),
        NoGroup
      )),
      "(800)5551234" -> Some(List(
        NoGroup,
        (1, 4, "800"),
        (5, 8, "555"),
        (8, 12, "1234"),
        NoGroup
      )),
      "800-555-1234" -> Some(List(
        NoGroup,
        (0, 3, "800"),
        (4, 7, "555"),
        (8, 12, "1234"),
        NoGroup
      )),
      "800.555.1234" -> Some(List(
        NoGroup,
        (0, 3, "800"),
        (4, 7, "555"),
        (8, 12, "1234"),
        NoGroup
      )),
      "800 555 1234x5678" -> Some(List(
        NoGroup,
        (0, 3, "800"),
        (4, 7, "555"),
        (8, 12, "1234"),
        (13, 17, "5678")
      )),
      "8005551234 x5678" -> Some(List(
        NoGroup,
        (0, 3, "800"),
        (3, 6, "555"),
        (6, 10, "1234"),
        (12, 16, "5678")
      )),
      "1    800    555-1234" -> Some(List(
        (0, 1, "1"),
        (5, 8, "800"),
        (12, 15, "555"),
        (16, 20, "1234"),
        NoGroup
      )),
      "1----800----555-1234" -> Some(List(
        (0, 1, "1"),
        (5, 8, "800"),
        (12, 15, "555"),
        (16, 20, "1234"),
        NoGroup
      )),
    )
  )
}
