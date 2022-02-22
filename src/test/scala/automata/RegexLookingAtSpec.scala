package automata

import org.scalactic.source.Position
import org.scalatest.funspec.AnyFunSpec
import scala.collection.immutable.ListMap
import java.lang.{StringBuilder => JavaStringBuilder}

class RegexLookingAtSpec extends AnyFunSpec {

  private def testLookingAt(
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
          val matcher = p.matcher(input)
          val matched = matcher.lookingAt()

          expectedOutput match {
            case None =>
              assert(!matched, "lookingAt should not find a match")

            case Some(groups) =>
              assert(matched, "lookingAt should find a match")
              assert(expectedGroupCount == matcher.groupCount, "count of capture groups in pattern")

              assert(groups.length - 1 == matcher.groupCount)
              for (i <- 0 until groups.length) {
                val (expectedStart, expectedEnd, expectedCapture) = groups(i)
                assert(matcher.start(i) == expectedStart, s"start index of capture group $i")
                assert(matcher.end(i) == expectedEnd, s"end index of capture group $i")
                assert(matcher.group(i) == expectedCapture, s"value of capture group $i")
              }

              // Whenever there is a `lookingAt` match, a `find` match can also be found
              matcher.reset()
              assert(matcher.find(), "find should find a match")
          }
        }
      }
    }

    describe(if (pattern.isEmpty) "<empty>" else pattern) {
      describe("interpreted")(testUsing(DfaPattern.interpreted(pattern)))
      describe("compiled")(testUsing(DfaPattern.compile(pattern)))
    }
  }

  // Greedy vs. reluctant
  testLookingAt(
    pattern = "a*",
    expectedGroupCount = 0,
    inputs = ListMap(
      "" -> Some(List((0, 0, ""))),
      "a" -> Some(List((0, 1, "a"))),
      "b" -> Some(List((0, 0, ""))),
      "aaa" -> Some(List((0, 3, "aaa"))),
      "aabc" -> Some(List((0, 2, "aa"))),
      "bacd" -> Some(List((0, 0, "")))
    )
  )
  testLookingAt(
    pattern = "a*?",
    expectedGroupCount = 0,
    inputs = ListMap(
      "" -> Some(List((0, 0, ""))),
      "a" -> Some(List((0, 0, ""))),
      "b" -> Some(List((0, 0, ""))),
      "aaa" -> Some(List((0, 0, ""))),
      "aabc" -> Some(List((0, 0, ""))),
      "bacd" -> Some(List((0, 0, "")))
    )
  )

  // Alternation order
  testLookingAt(
    pattern = "a*|a*b*",
    expectedGroupCount = 0,
    inputs = ListMap(
      "" -> Some(List((0, 0, ""))),
      "a" -> Some(List((0, 1, "a"))),
      "b" -> Some(List((0, 0, ""))),
      "aaa" -> Some(List((0, 3, "aaa"))),
      "aabc" -> Some(List((0, 2, "aa"))),
      "bacd" -> Some(List((0, 0, ""))),
      "bbbc" -> Some(List((0, 0, "")))
    )
  )
  testLookingAt(
    pattern = "a*b*|b*",
    expectedGroupCount = 0,
    inputs = ListMap(
      "" -> Some(List((0, 0, ""))),
      "a" -> Some(List((0, 1, "a"))),
      "b" -> Some(List((0, 1, "b"))),
      "aaa" -> Some(List((0, 3, "aaa"))),
      "aabc" -> Some(List((0, 3, "aab"))),
      "bacd" -> Some(List((0, 1, "b"))),
      "bbbc" -> Some(List((0, 3, "bbb")))
    )
  )
}

