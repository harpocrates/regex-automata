package automata;

import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import scala.annotation.unused
import scala.jdk.CollectionConverters._

trait ReGenerator extends ArbitraryExtensions {

  def generateBoundaries: Boolean = true
  
  def generateBuiltinClasses: Boolean = true

  def maximumRangeSize: Option[Int] = None

  // Does not generate values outside of the unicode range (U+0000 to U+10FFFF)
  val arbitraryCodePoint: Arbitrary[Int] = Arbitrary {
    Gen.frequency(
      5 -> Gen.choose(33, 126), // printable ASCII range
      1 -> Gen.choose(0x0000, 0xFFFF), // Basic multilingual place
      1 -> Gen.choose(Character.MIN_VALUE, Character.MAX_VALUE), // Any codepoint
    )
  }

  implicit lazy val charClassArbitrary: Arbitrary[CharClass] = Arbitrary {
    var variants = List[(Int, Gen[CharClass])](
      10 -> Gen.resultOf(Re.Character(_: Int))(arbitraryCodePoint),
      1 -> Gen
        .resultOf(Re.CharacterRange(_: Int, _: Int))(arbitraryCodePoint, arbitraryCodePoint)
        .map { case range @ Re.CharacterRange(from, to) =>
          // Make sure bounds are ordered
          val ordered = if (from > to) Re.CharacterRange(to, from) else range

          // Max sure the range isn't too big
          maximumRangeSize match {
            case Some(max) if max < ordered.toCodePoint - ordered.fromCodePoint =>
              ordered.copy(toCodePoint = ordered.fromCodePoint + max)
            case _ =>
              ordered
          }
        },
      1 -> reducedResultOf(Re.NegatedClass(_)),
      1 -> reducedResultOf(Re.IntersectionClass(_, _)),
      1 -> reducedResultOf(Re.UnionClass(_, _))
    )
    if (generateBuiltinClasses) {
      // The `.` character class doesn't work inside brackets, so don't generate it here
      variants ::= 1 -> Gen
        .oneOf(CharClassVisitor.BuiltinClass.CHARACTERS.values.asScala.toSeq)
        .map(Re.BuiltinClass(_))
    }
    val smallVariants = variants.take(3)

    Gen.size.flatMap { (size: Int) =>
      Gen.frequency((if (size <= 1) smallVariants else variants): _*)
    }
  }

  /* Traverse the regex AST and re-number groups
   *
   * @param input regex with possibly invalid group indices
   * @return re-numbered regex
   */
  def renumberGroups(input: Re): Re = {
    var lastIndex = -1
  
    def nextIndex(): Int = {
      lastIndex += 1
      lastIndex
    }

    def go(re: Re): Re = re match {
      case Re.Epsilon => Re.Epsilon
      case cls: CharClass => cls
      case Re.Concat(lhs, rhs) => Re.Concat(go(lhs), go(rhs))
      case Re.Alternation(lhs, rhs) => Re.Alternation(go(lhs), go(rhs))
      case Re.Optional(lhs, isLazy) => Re.Optional(go(lhs), isLazy)
      case Re.Kleene(lhs, isLazy) => Re.Kleene(go(lhs), isLazy)
      case Re.Plus(lhs, isLazy) => Re.Plus(go(lhs), isLazy)
      case Re.Repetition(lhs, atLeast, atMost, isLazy) => Re.Repetition(go(lhs), atLeast, atMost, isLazy)
      case boundary: Re.Boundary => boundary
      case Re.Group(arg, _) =>
        val idx = nextIndex()
        Re.Group(go(arg), idx)
    }

    go(input)
  }

  implicit lazy val regexArbitrary: Arbitrary[Re] = {

    @unused
    val regexArbitrary: Unit = ()

    // This will generate all groups as index `0`
    // TODO: repetition
    implicit lazy val unorderedGroupRegexArbitrary: Arbitrary[Re] = Arbitrary {
      var variants = List[(Int, Gen[Re])](
        3 -> charClassArbitrary.arbitrary,
        1 -> Gen.const(Re.Epsilon),
        16 -> reducedResultOf(Re.Concat(_, _)).map {
          case Re.Concat(Re.Epsilon, rhs) => rhs
          case Re.Concat(lhs, Re.Epsilon) => lhs
          case other => other
        },
        4 -> reducedResultOf(Re.Alternation(_, _)),
        2 -> reducedResultOf(Re.Optional(_, _)),
        2 -> reducedResultOf(Re.Kleene(_, _)),
        2 -> reducedResultOf(Re.Plus(_, _)),
        2 -> reducedResultOf(Re.Group(_, 0))
      )
      if (generateBoundaries) {
        variants ::= 1 -> Gen.resultOf(Re.Boundary(_))
      }
      if (generateBuiltinClasses) {
        variants ::= 1 -> Gen.const(Re.BuiltinClass(CharClassVisitor.BuiltinClass.DOT))
      }
      val smallVariants = variants.take(3)

      Gen.size.flatMap { (size: Int) =>
        Gen.frequency((if (size <= 1) smallVariants else variants): _*)
      }
    }

    // Re-number groups
    Arbitrary(unorderedGroupRegexArbitrary.arbitrary.map(renumberGroups))
  }

}


/* Extra helper methods to work around ScalaCheck limitations for generating
 * ASTs - see <https://github.com/typelevel/scalacheck/issues/305>
 */
trait ArbitraryExtensions {

  /** Split the current generator size into the specified number of sub-groups.
   *
   * The sum of the sizes should equal 1 less than the initial generator size.
   * The length of the list returned is equal to the requested number of groups.
   *
   * @param n how many sub-groups to split into?
   * @return size of sub-groups
   */
  private[this] def partitionReducedSize(n: Int): Gen[Seq[Int]] =
     for {
       size <- Gen.size
       decrementedSize = size - 1
       if decrementedSize >= 0
       groupSize = decrementedSize / n
       remainder = decrementedSize % n
       groups = List.tabulate(n)(i => if (i < remainder) 1 + groupSize else groupSize)
       shuffledGroups <- Gen.pick(n, groups)
     } yield shuffledGroups.toList


  def reducedResultOf[T1: Arbitrary, R](f: T1 => R): Gen[R] =
    for {
      Seq(s1) <- partitionReducedSize(1)
      t1 <- Gen.resize(s1, Arbitrary.arbitrary[T1])
    } yield f(t1)

  def reducedResultOf[T1: Arbitrary, T2: Arbitrary, R](f: (T1, T2) => R): Gen[R] =
    for {
      Seq(s1, s2) <- partitionReducedSize(2)
      t1 <- Gen.resize(s1, Arbitrary.arbitrary[T1])
      t2 <- Gen.resize(s2, Arbitrary.arbitrary[T2])
    } yield f(t1, t2)

  def reducedResultOf[T1: Arbitrary, T2: Arbitrary, T3: Arbitrary, R](f: (T1, T2, T3) => R): Gen[R] =
    for {
      Seq(s1, s2, s3) <- partitionReducedSize(3)
      t1 <- Gen.resize(s1, Arbitrary.arbitrary[T1])
      t2 <- Gen.resize(s2, Arbitrary.arbitrary[T2])
      t3 <- Gen.resize(s3, Arbitrary.arbitrary[T3])
    } yield f(t1, t2, t3)
}
