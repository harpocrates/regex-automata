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
      testParse("𐐷", Re.Character(0x10437)) // outside BMP
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
      testParse("\\x{10437}", Re.Character(0x10437))
    }
  }

  describe("boundaries") {
    testParse("^", Re.Boundary(RegexVisitor.Boundary.BEGINNING))
    testParse("$", Re.Boundary(RegexVisitor.Boundary.END))
  }

  describe("quantifiers") {
    describe("optional") {
      testParse("a?", Re.Optional(Re.Character('a'), isLazy = false))
      testParse("a??", Re.Optional(Re.Character('a'), isLazy = true))
    }

    describe("kleene") {
      testParse("b*", Re.Kleene(Re.Character('b'), isLazy = false))
      testParse("b*?", Re.Kleene(Re.Character('b'), isLazy = true))
    }

    describe("plus") {
      testParse("b+", Re.Plus(Re.Character('b'), isLazy = false))
      testParse("b+?", Re.Plus(Re.Character('b'), isLazy = true))
    }

    describe("repetition") {
      testParse(
        "b{4}",
        Re.Repetition(Re.Character('b'), atLeast = 4, atMost = Some(4), isLazy = false)
      )
      testParse(
        "b{082}?",
        Re.Repetition(Re.Character('b'), atLeast = 82, atMost = Some(82), isLazy = true)
      )
      testParse(
        "x{7650,}",
        Re.Repetition(Re.Character('x'), atLeast = 7650, atMost = None, isLazy = false)
      )
      testParse(
        "x{32,}?",
        Re.Repetition(Re.Character('x'), atLeast = 32, atMost = None, isLazy = true)
      )
      testParse(
        "x{,7650}",
        Re.Repetition(Re.Character('x'), atLeast = 0, atMost = Some(7650), isLazy = false)
      )
      testParse(
        "x{,32}?",
        Re.Repetition(Re.Character('x'), atLeast = 0, atMost = Some(32), isLazy = true)
      )
      testParse(
        "x{5,10}",
        Re.Repetition(Re.Character('x'), atLeast = 5, atMost = Some(10), isLazy = false)
      )
      testParse(
        "x{5,10}?",
        Re.Repetition(Re.Character('x'), atLeast = 5, atMost = Some(10), isLazy = true)
      )
    }
  }

  describe("concatenation") {
    testParse("", Re.Epsilon)
    testParse("abc", Re.Concat(Re.Concat(Re.Character('a'), Re.Character('b')), Re.Character('c')))
    testParse(
      "^a\\+c",
      Re.Concat(
        Re.Concat(
          Re.Concat(Re.Boundary(RegexVisitor.Boundary.BEGINNING), Re.Character('a')),
          Re.Character('+')
        ),
        Re.Character('c')
      )
    )
    testParse(
      "ab?c",
      Re.Concat(
        Re.Concat(Re.Character('a'), Re.Optional(Re.Character('b'), isLazy = false)),
        Re.Character('c')
      )
    )
    testParse(
      "ab*?c",
      Re.Concat(
        Re.Concat(Re.Character('a'), Re.Kleene(Re.Character('b'), isLazy = true)),
        Re.Character('c')
      )
    )
    testParse(
      "a𐐷+c",
      Re.Concat(
        Re.Concat(
          Re.Character('a'),
          Re.Plus(Re.Character(0x10437), isLazy = false)
        ),
        Re.Character('c')
      )
    )
    testParse(
      "ab{5,7}?c",
      Re.Concat(
        Re.Concat(
          Re.Character('a'),
          Re.Repetition(Re.Character('b'), atLeast = 5, atMost = Some(7), isLazy = true)
        ),
        Re.Character('c')
      )
    )
    testParse(
      "abcdefgh",
      Re.Concat(
        Re.Concat(
          Re.Concat(
            Re.Concat(
              Re.Concat(
                Re.Concat(
                  Re.Concat(
                    Re.Character('a'),
                    Re.Character('b')
                  ),
                  Re.Character('c')
                ),
                Re.Character('d')
              ),
              Re.Character('e')
            ),
            Re.Character('f')
          ),
          Re.Character('g')
        ),
        Re.Character('h')
      )
    )
  }

  describe("union") {
    testParse("||", Re.Alternation(Re.Alternation(Re.Epsilon, Re.Epsilon), Re.Epsilon))
    testParse(
      "ab|c",
      Re.Alternation(Re.Concat(Re.Character('a'), Re.Character('b')), Re.Character('c'))
    )
    testParse(
      "a|bc*|d",
      Re.Alternation(
        Re.Alternation(
          Re.Character('a'),
          Re.Concat(Re.Character('b'), Re.Kleene(Re.Character('c'), isLazy = false))
        ),
        Re.Character('d')
      )
    )
  }

  describe("parentheses") {
    describe("non-capturing group") {
      testParse("(?:)", Re.Epsilon)
      testParse(
        "a(?:b|c)d",
        Re.Concat(
          Re.Concat(Re.Character('a'), Re.Alternation(Re.Character('b'), Re.Character('c'))),
          Re.Character('d')
        )
      )
      testParse(
        "a(?:bb)+d",
        Re.Concat(
          Re.Concat(
            Re.Character('a'),
            Re.Plus(Re.Concat(Re.Character('b'), Re.Character('b')), isLazy = false),
          ),
          Re.Character('d')
        )
      )
    }

    describe("capturing group") {
      testParse("()", Re.Group(Re.Epsilon, index = 0))
      testParse(
        "a(b|c)d",
        Re.Concat(
          Re.Concat(
            Re.Character('a'),
            Re.Group(Re.Alternation(Re.Character('b'), Re.Character('c')), index = 0)
          ),
          Re.Character('d')
        )
      )
      testParse(
        "a(bb)+d",
        Re.Concat(
          Re.Concat(
            Re.Character('a'),
            Re.Plus(
              Re.Group(Re.Concat(Re.Character('b'), Re.Character('b')), index = 0),
              isLazy = false
            )
          ),
          Re.Character('d')
        )
      )
    }

    describe("nested capture groups") {
      testParse(
        "(()())",
        Re.Group(
          Re.Concat(Re.Group(Re.Epsilon, index = 1), Re.Group(Re.Epsilon, index = 2)),
          index = 0
        )
      )

      testParse(
        "(a|(b)|(c))",
        Re.Group(
          Re.Alternation(
            Re.Alternation(
              Re.Character('a'),
              Re.Group(Re.Character('b'), index = 1)
            ),
            Re.Group(Re.Character('c'), index = 2)
          ),
          index = 0
        )
      )
    }
  }

  describe("character classes") {

    describe("union") {
      testParse("[x]", Re.Character('x'))
      testParse(
        "[ace]",
        Re.UnionClass(
          Re.UnionClass(Re.Character('a'), Re.Character('c')),
          Re.Character('e')
        )
      )
      testParse("[.]", Re.Character('.'))
      testParse("[+]", Re.Character('+'))
      testParse("[(]", Re.Character('('))
      testParse("[]]", Re.Character(']'))
      testParse("[-]", Re.Character('-'))
      testParse("[-a]", Re.UnionClass(Re.Character('-'), Re.Character('a')))
      testParse("[\\x45]", Re.Character(0x45))
    }

    describe("range") {
      testParse(
        "[a-zA-Z0-9_]",
        Re.UnionClass(
          Re.UnionClass(
            Re.UnionClass(Re.CharacterRange('a','z'), Re.CharacterRange('A', 'Z')),
            Re.CharacterRange('0', '9')
          ),
          Re.Character('_')
        )
      )
      testParse("[-4-6]", Re.UnionClass(Re.Character('-'), Re.CharacterRange('4', '6')))
      testParse("[\\x45-\\x98]", Re.CharacterRange(0x45, 0x98))
    }

    describe("negations") {
      testParse(
        "[^a-z-]",
        Re.NegatedClass(Re.UnionClass(Re.CharacterRange('a', 'z'), Re.Character('-')))
      )
      testParse("[^]]", Re.NegatedClass(Re.Character(']')))
    }

    describe("intersection") {
      testParse(
        "[a-q&&o]",
        Re.IntersectionClass(Re.CharacterRange('a', 'q'), Re.Character('o'))
      )
      testParse(
        "[ab&&a-zk]",
        Re.UnionClass(
          Re.UnionClass(
            Re.Character('a'),
            Re.IntersectionClass(Re.Character('b'), Re.CharacterRange('a', 'z'))
          ),
          Re.Character('k')
        )
      )
    }

    describe("nested") {
      testParse(
        "[-\\]a-z&&[^q-u]z]",
        Re.UnionClass(
          Re.UnionClass(
            Re.UnionClass(
              Re.Character('-'),
              Re.Character(']')
            ),
            Re.IntersectionClass(
              Re.CharacterRange('a', 'z'),
              Re.NegatedClass(Re.CharacterRange('q', 'u'))
            ),
          ),
          Re.Character('z')
        )
      )
    }
  }
}
