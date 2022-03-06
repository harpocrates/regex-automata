package automata.parser

import automata.Re
import org.scalactic.source.Position
import org.scalatest.funspec.AnyFunSpec
import java.util.regex.PatternSyntaxException

class RegexParserErrorsSpec extends AnyFunSpec {

  private def testParseError(
    input: String,
    description: String,
    index: Int
  )(implicit
    pos: Position
  ): Unit = {
    it(input) {
      val err = intercept[PatternSyntaxException](Re.parse(input))
      assert(err.getDescription === description, "error description must match")
      assert(err.getIndex === index, "error index must match")
    }
  }

  describe("mismatched parens") {
    testParseError(
      "(a",
      description = "Unclosed group (expected close paren for group opened at 0)",
      index = 2
    )

    testParseError(
      "a)",
      description = "Expected the end of the regular expression",
      index = 1
    )
  }

  describe("mismatched braces") {
    testParseError(
      "a{1",
      description = "Expected `}` to close repetition",
      index = 3
    )
  }

  describe("mismatched brackets") {
    testParseError(
      "[",
      description = "Unclosed character class (expected close to bracket opened at 0)",
      index = 1
    )


    testParseError(
      "[a",
      description = "Unclosed character class (expected close to bracket opened at 0)",
      index = 2
    )
  }

  describe("unsupported features") {
    testParseError(
      "a*+",
      description = "Possesive quantifiers are not supported",
      index = 2
    )

    testParseError(
      "(?=foo)",
      description = "Lookaround capture groups are not supported",
      index = 2
    )

    testParseError(
      "([abc])-\\1",
      description = "Backreferences are not supported",
      index = 9
    )

    testParseError(
      "[\\Q\\E]",
      description = "Empty literal escapes in character classes are not supported",
      index = 5
    )
  }

  describe("invalid range") {
    testParseError(
      "[a-[",
      description = "Invalid character range, missing right hand side",
      index = 2
    )

    testParseError(
      "[a-\\d]",
      description = "Cannot end class range with character class",
      index = 4
    )

    testParseError(
      "[\\b]",
      description = "Cannot use boundary escapes in character class",
      index = 2
    )
  }

  describe("invalid escape") {
    testParseError(
      "\\",
      description = "Pattern may not end with backslash",
      index = 1
    )

    describe("hexadecimal") {
      testParseError(
        "\\xNM",
        description = "Expected a hexadecimal character",
        index = 2
      )
      testParseError(
        "\\x",
        description = "Expected hexadecimal escape but got end of regex",
        index = 2
      )
      testParseError(
        "\\x{13FFFF}",
        description = "Hexadecimal escape overflowed max code point",
        index = 9
      )
      testParseError(
        "\\x{1FFF",
        description = "Expected `}` but got end of regex",
        index = 7
      )
    }

    describe("octal") {
      testParseError(
        "\\0NM",
        description = "Expected an octal escape character",
        index = 2
      )
    }

    describe("octal") {
      testParseError(
        "\\0",
        description = "Expected 1 to 3 octal escape characters but got end of regex",
        index = 2
      )
    }

    testParseError(
      "\\i",
      description = "Unknown escape sequence: i",
      index = 1
    )

    testParseError(
      "\\c",
      description = "Expected control character escape but got end of regex",
      index = 2
    )

    describe("properties") {
      testParseError(
        "\\p",
        description = "Expected property name but got end of regex",
        index = 2
      )
      testParseError(
        "\\p{blk=NotABlock",
        description = "Expected `}` but got end of regex",
        index = 3
      )

      testParseError(
        "\\p{blk=NotABlock}",
        description = "Unknown unicode block: NotABlock",
        index = 17
      )
      testParseError(
        "\\p{sc=NotAScript}",
        description = "Unknown unicode script: NotAScript",
        index = 17
      )
      testParseError(
        "\\p{other=NotAProperty}",
        description = "Unknown property class key: other",
        index = 22
      )
      testParseError(
        "\\p{InNotABlock}",
        description = "Unknown unicode block: NotABlock",
        index = 15
      )
      testParseError(
        "\\p{IsNotAScript}",
        description = "Unknown unicode script: NotAScript",
        index = 16
      )
    }

    testParseError(
      "\\cë",
      description = "Expected control escape letter in printable ASCII range",
      index = 2
    )
  }
}
