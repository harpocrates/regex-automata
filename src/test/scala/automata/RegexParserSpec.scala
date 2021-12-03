package automata

import org.scalactic.source.Position
import org.scalatest.funspec.AnyFunSpec

class RegexParserSpec extends AnyFunSpec {

  private def testParse(
    input: String,
    parsesTo: Re,
    skip: Boolean = false
  )(implicit
    pos: Position
  ): Unit = {
    def test = assert(Re.parse(input) === parsesTo, "regex must parse correctly")
    if (skip) ignore(input)(test) else it(input)(test)
  }

  describe("characters") {
    describe("literal") {
      testParse("a", Re.Character('a')) // simple ascii
      testParse("ø", Re.Character('ø')) // outside ascii
      testParse("𐐷", Re.Concat(Re.Character('\uD801'), Re.Character('\uDC37'))) // outside BMP
    }

    describe("backslash-escaped") {
      for (c <- "\\.+*?()|[]{}^$") testParse(s"\\$c", Re.Character(c))

      testParse("\\a", Re.Character('\u0007'))
      testParse("\\e", Re.Character('\u001B'))
      testParse("\\f", Re.Character('\f'))
      testParse("\\t", Re.Character('\t'))
      testParse("\\r", Re.Character('\r'))
      testParse("\\v", Re.Character('\u000b'))
    }

    describe("hexadecimal codes") {
      testParse("\\x76", Re.Character('v'))
      testParse("\\u248e", Re.Character('\u248e'))
      testParse("\\x{1a3}", Re.Character('\u01a3'))
      testParse("\\x{10437}", Re.Concat(Re.Character('\uD801'), Re.Character('\uDC37')))
    }
  }
}
