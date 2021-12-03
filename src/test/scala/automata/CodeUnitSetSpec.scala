package automata

import java.util.regex.MatchResult
import org.scalatest.funspec.AnyFunSpec

class CodeUnitSetSpec extends AnyFunSpec {

  val interval0 = CodeUnitSet.FULL
  val interval1 = CodeUnitSet.EMPTY
  val interval2 = CodeUnitSet.of(CodeUnitRange.between(213, 876))
  val interval3 = CodeUnitSet.of(CodeUnitRange.between(CodeUnitRange.MIN_BOUND, 876))
  val interval4 = CodeUnitSet.of(CodeUnitRange.between(213, CodeUnitRange.MAX_BOUND))

  describe("CodeUnitSet.contains") {
    it("CodeUnitSet.FULL should contain any element") {
      assert(CodeUnitSet.FULL.contains(CodeUnitRange.MIN_BOUND))
      assert(CodeUnitSet.FULL.contains(CodeUnitRange.MAX_BOUND))
      assert(CodeUnitSet.FULL.contains(42))
      assert(CodeUnitSet.FULL.contains(88))
      assert(CodeUnitSet.FULL.contains(89))
      assert(CodeUnitSet.FULL.contains(90))
      assert(CodeUnitSet.FULL.contains(-765))
    }

    it("CodeUnitSet.EMPTY should contain no element") {
      assert(!CodeUnitSet.EMPTY.contains(CodeUnitRange.MIN_BOUND))
      assert(!CodeUnitSet.EMPTY.contains(CodeUnitRange.MAX_BOUND))
      assert(!CodeUnitSet.EMPTY.contains(42))
      assert(!CodeUnitSet.EMPTY.contains(88))
      assert(!CodeUnitSet.EMPTY.contains(89))
      assert(!CodeUnitSet.EMPTY.contains(90))
      assert(!CodeUnitSet.EMPTY.contains(-765))
    }

    it("CodeUnitSet.between(0-89) should contain some elements") {
      val s = CodeUnitSet.of(CodeUnitRange.between(CodeUnitRange.MIN_BOUND, 89))
      assert(s.contains(CodeUnitRange.MIN_BOUND))
      assert(!s.contains(CodeUnitRange.MAX_BOUND))
      assert(s.contains(42))
      assert(s.contains(88))
      assert(s.contains(89))
      assert(!s.contains(90))
      assert(!s.contains(-765))
    }

    it("CodeUnitSet.of(89-65535) should contain some elements") {
      val s = CodeUnitSet.of(CodeUnitRange.between(89, CodeUnitRange.MAX_BOUND))
      assert(!s.contains(CodeUnitRange.MIN_BOUND))
      assert(s.contains(CodeUnitRange.MAX_BOUND))
      assert(!s.contains(42))
      assert(!s.contains(88))
      assert(s.contains(89))
      assert(s.contains(90))
      assert(s.contains(-765))
    }

    it("CodeUnitSet.between(89-65535) should contain some elements") {
      val s = CodeUnitSet.of(CodeUnitRange.between(89, CodeUnitRange.MAX_BOUND))
      assert(!s.contains(CodeUnitRange.MIN_BOUND))
      assert(s.contains(CodeUnitRange.MAX_BOUND))
      assert(!s.contains(42))
      assert(!s.contains(88))
      assert(s.contains(89))
      assert(s.contains(90))
      assert(s.contains(-765))
    }
  }

  describe("CodeUnitSet.intersection") {
    it("should treat CodeUnitSet.FULL as identity") {
      for (i1 <- List(interval0, interval1, interval2, interval3, interval4)) {
        assert(CodeUnitSet.FULL.intersection(i1) == i1)
        assert(i1.intersection(CodeUnitSet.FULL) == i1)
      }
    }

    it("should be commutative") {
      for {
        i1 <- List(interval0, interval1, interval2, interval3, interval4)
        i2 <- List(interval0, interval1, interval2, interval3, interval4)
      } {
        assert(i1.intersection(i2) == i2.intersection(i1))
      }
    }

    it("should intersect sets with multiple intervals") {
      val s1 = CodeUnitSet.of(CodeUnitRange.between(4, 12), CodeUnitRange.single(56))
      val s2 = CodeUnitSet.of(CodeUnitRange.between(8, 78), CodeUnitRange.between(100, 107))
      val s3 = CodeUnitSet.of(CodeUnitRange.between(8, 12), CodeUnitRange.single(56))
      assert(s1.intersection(s2) == s3)
    }
  }

  describe("CodeUnitSet.union") {
    it("should treat CodeUnitSet.EMPTY as identity") {
      for {
        i1 <- List(interval0, interval1, interval2, interval3, interval4)
      } {
        assert(CodeUnitSet.EMPTY.union(i1) == i1)
        assert(i1.union(CodeUnitSet.EMPTY) == i1)
      }
    }

    it("should be commmutative") {
      for {
        i1 <- List(interval0, interval1, interval2, interval3, interval4)
        i2 <- List(interval0, interval1, interval2, interval3, interval4)
      } {
        assert(i1.union(i2) == i2.union(i1))
      }
    }

    it("should union sets with multiple intervals") {
      val s1 = CodeUnitSet.of(CodeUnitRange.between(4, 12), CodeUnitRange.single(56))
      val s2 = CodeUnitSet.of(CodeUnitRange.between(8, 78), CodeUnitRange.between(100, 107))
      val s3 = CodeUnitSet.of(CodeUnitRange.between(4, 78), CodeUnitRange.between(100, 107))
      assert(s1.union(s2) == s3)
    }

    it("should combine contiguous sets in unions") {
      val s1 = CodeUnitSet.of(CodeUnitRange.between(4, 12), CodeUnitRange.single(56))
      val s2 = CodeUnitSet.of(CodeUnitRange.between(13, 55), CodeUnitRange.single(57))
      val s3 = CodeUnitSet.of(CodeUnitRange.between(4, 57))
      assert(s1.union(s2) == s3)
    }
  }
}
