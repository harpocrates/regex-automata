package automata

// Follow https://www.labs.hpe.com/techreports/2012/HPL-2012-41R1.pdf

sealed abstract class Re
object Re {

  /** Matches nothing */
  final case object Epsilon extends Re

  /** Matches a literal character */
  final case class Character(c: Char) extends Re

  /** Matches two patterns, one after another */
  final case class Concat(lhs: Re, rhs: Re) extends Re

  /** Matches one of two patterns */
  final case class Union(lhs: Re, rhs: Re) extends Re

  /** Match the same pattern as many/few times as possible */
  final case class Kleene(lhs: Re, isLazy: Boolean) extends Re

  /** Group sub-capture */
  final case class Group(arg: Re, idx: Int) extends Re

  def parse(src: String): Re = {
    var pos: Int = 0
    val len: Int = src.length
    var groupCount: Int = 1

    // Called after seeing `<first-regex>|<rest>`
    def parseUnion(): Re = {
      var union: Re = parseConcat()
      while (pos < len && src.charAt(pos) == '|') {
        pos += 1
        union = Union(union, parseConcat())
      }
      union
    }

    def parseConcat(): Re = {
      var concat: Re = Epsilon
      while (pos < len && src.charAt(pos) != ')' && src.charAt(pos) != '|') {
        val kleene = parseKleene()
        concat = if (concat == Epsilon) kleene else Concat(concat, kleene)
      }
      concat
    }

    // Called on non-empty input
    def parseKleene(): Re = {
      var kleene: Re = parseGroup()
      while (pos < len && src.charAt(pos) == '*') {
        pos += 1
        val isLazy = if (pos < len && src.charAt(pos) == '?') {
          pos += 1
          true
        } else {
          false
        }
        kleene = Kleene(kleene, isLazy)
      }
      kleene
    }

    // Called on a non-empty input
    def parseGroup(): Re = {
      if (src.charAt(pos) == '(') {
        pos += 1
        val group = if (pos + 1 < len && src.charAt(pos) == '?' && src.charAt(pos + 1) == ':') {
          pos += 2
          parseUnion()
        } else {
          val groupIdx = groupCount
          groupCount += 1
          Group(parseUnion(), groupIdx)
        }
        assert(src.charAt(pos) == ')', "expected ')' to complete group")
        pos += 1
        group
      } else {
        parseCharacter()
      }
    }

    // Called on a non-empty input
    def parseCharacter(): Re = {
      if (src.charAt(pos) == '\\') {
        pos += 1
        assert(pos < len, "expected escaped character but got end of regex")
      }
      val re = Character(src.charAt(pos))
      pos += 1
      re
    }

    parseUnion()
  }
}
