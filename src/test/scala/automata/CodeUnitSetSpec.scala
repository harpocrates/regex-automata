package automata

import org.scalatest.funspec.AnyFunSpec

class CodeUnitSetSpec extends AnyFunSpec {

  val interval0 = CodeUnitSet.FULL
  val interval1 = CodeUnitSet.EMPTY
  val interval2 = CodeUnitSet.of(CodeUnitRange.between('\u0213', '\u0876'))
  val interval3 = CodeUnitSet.of(CodeUnitRange.between(Character.MIN_VALUE, '\u0876'))
  val interval4 = CodeUnitSet.of(CodeUnitRange.between('\u0213', Character.MAX_VALUE))

  describe("CodeUnitSet.contains") {
    it("CodeUnitSet.FULL should contain any element") {
      assert(CodeUnitSet.FULL.contains(Character.MIN_VALUE))
      assert(CodeUnitSet.FULL.contains(Character.MAX_VALUE))
      assert(CodeUnitSet.FULL.contains('\u0042'))
      assert(CodeUnitSet.FULL.contains('\u0088'))
      assert(CodeUnitSet.FULL.contains('\u0089'))
      assert(CodeUnitSet.FULL.contains('\u0090'))
      assert(CodeUnitSet.FULL.contains('\uf3ab'))
    }

    it("CodeUnitSet.EMPTY should contain no element") {
      assert(!CodeUnitSet.EMPTY.contains(Character.MIN_VALUE))
      assert(!CodeUnitSet.EMPTY.contains(Character.MAX_VALUE))
      assert(!CodeUnitSet.EMPTY.contains('\u0042'))
      assert(!CodeUnitSet.EMPTY.contains('\u0088'))
      assert(!CodeUnitSet.EMPTY.contains('\u0089'))
      assert(!CodeUnitSet.EMPTY.contains('\u0090'))
      assert(!CodeUnitSet.EMPTY.contains('\uf3ab'))
    }

    it("CodeUnitSet.between(0-89) should contain some elements") {
      val s = CodeUnitSet.of(CodeUnitRange.between(Character.MIN_VALUE, '\u0089'))
      assert(s.contains(Character.MIN_VALUE))
      assert(!s.contains(Character.MAX_VALUE))
      assert(s.contains('\u0042'))
      assert(s.contains('\u0088'))
      assert(s.contains('\u0089'))
      assert(!s.contains('\u0090'))
      assert(!s.contains('\uf3ab'))
    }

    it("CodeUnitSet.of(89-65535) should contain some elements") {
      val s = CodeUnitSet.of(CodeUnitRange.between('\u0089', Character.MAX_VALUE))
      assert(!s.contains(Character.MIN_VALUE))
      assert(s.contains(Character.MAX_VALUE))
      assert(!s.contains('\u0042'))
      assert(!s.contains('\u0088'))
      assert(s.contains('\u0089'))
      assert(s.contains('\u0090'))
      assert(s.contains('\uf3ab'))
    }

    it("CodeUnitSet.between(89-65535) should contain some elements") {
      val s = CodeUnitSet.of(CodeUnitRange.between('\u0089', Character.MAX_VALUE))
      assert(!s.contains(Character.MIN_VALUE))
      assert(s.contains(Character.MAX_VALUE))
      assert(!s.contains('\u0042'))
      assert(!s.contains('\u0088'))
      assert(s.contains('\u0089'))
      assert(s.contains('\u0090'))
      assert(s.contains('\uf3ab'))
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
      val s1 = CodeUnitSet.of(CodeUnitRange.between('\u0004', '\u0012'), CodeUnitRange.single('\u0056'))
      val s2 = CodeUnitSet.of(CodeUnitRange.between('\u0008', '\u0078'), CodeUnitRange.between('\u0100', '\u0107'))
      val s3 = CodeUnitSet.of(CodeUnitRange.between('\u0008', '\u0012'), CodeUnitRange.single('\u0056'))
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
      val s1 = CodeUnitSet.of(CodeUnitRange.between('\u0004', '\u0012'), CodeUnitRange.single('\u0056'))
      val s2 = CodeUnitSet.of(CodeUnitRange.between('\u0008', '\u0078'), CodeUnitRange.between('\u0100', '\u0107'))
      val s3 = CodeUnitSet.of(CodeUnitRange.between('\u0004', '\u0078'), CodeUnitRange.between('\u0100', '\u0107'))
      assert(s1.union(s2) == s3)
    }

    it("should combine contiguous sets in unions") {
      val s1 = CodeUnitSet.of(CodeUnitRange.between('\u0004', '\u0012'), CodeUnitRange.single('\u0056'))
      val s2 = CodeUnitSet.of(CodeUnitRange.between('\u0013', '\u0055'), CodeUnitRange.single('\u0057'))
      val s3 = CodeUnitSet.of(CodeUnitRange.between('\u0004', '\u0057'))
      assert(s1.union(s2) == s3)
    }
  }
}
