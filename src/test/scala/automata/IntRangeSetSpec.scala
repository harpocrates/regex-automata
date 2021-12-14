package automata

import org.scalatest.funspec.AnyFunSpec

class IntRangeSetSpec extends AnyFunSpec {

  val interval0 = IntRangeSet.FULL
  val interval1 = IntRangeSet.EMPTY
  val interval2 = IntRangeSet.of(IntRange.between('\u0213', '\u0876'))
  val interval3 = IntRangeSet.of(IntRange.between(Character.MIN_VALUE, '\u0876'))
  val interval4 = IntRangeSet.of(IntRange.between('\u0213', Character.MAX_VALUE))
  val interval5 = IntRangeSet.of(IntRange.between(Integer.MIN_VALUE, -21), IntRange.between(6, 29))
  val interval6 = IntRangeSet.of(IntRange.between(-20, 5), IntRange.between(30, Integer.MAX_VALUE))
  val interval7 = IntRangeSet.of(IntRange.between(Integer.MIN_VALUE, 5), IntRange.between(30, Integer.MAX_VALUE))

  val intervals = List(interval0, interval1, interval2, interval3, interval4, interval5, interval6, interval7)

  describe("IntRangeSet.contains") {
    it("IntRangeSet.FULL should contain any element") {
      assert(IntRangeSet.FULL.contains(Character.MIN_VALUE))
      assert(IntRangeSet.FULL.contains(Character.MAX_VALUE))
      assert(IntRangeSet.FULL.contains('\u0042'))
      assert(IntRangeSet.FULL.contains('\u0088'))
      assert(IntRangeSet.FULL.contains('\u0089'))
      assert(IntRangeSet.FULL.contains('\u0090'))
      assert(IntRangeSet.FULL.contains('\uf3ab'))
    }

    it("IntRangeSet.EMPTY should contain no element") {
      assert(!IntRangeSet.EMPTY.contains(Character.MIN_VALUE))
      assert(!IntRangeSet.EMPTY.contains(Character.MAX_VALUE))
      assert(!IntRangeSet.EMPTY.contains('\u0042'))
      assert(!IntRangeSet.EMPTY.contains('\u0088'))
      assert(!IntRangeSet.EMPTY.contains('\u0089'))
      assert(!IntRangeSet.EMPTY.contains('\u0090'))
      assert(!IntRangeSet.EMPTY.contains('\uf3ab'))
    }

    it("IntRangeSet.between(0-89) should contain some elements") {
      val s = IntRangeSet.of(IntRange.between(Character.MIN_VALUE, '\u0089'))
      assert(s.contains(Character.MIN_VALUE))
      assert(!s.contains(Character.MAX_VALUE))
      assert(s.contains('\u0042'))
      assert(s.contains('\u0088'))
      assert(s.contains('\u0089'))
      assert(!s.contains('\u0090'))
      assert(!s.contains('\uf3ab'))
    }

    it("IntRangeSet.of(89..65535) should contain some elements") {
      val s = IntRangeSet.of(IntRange.between('\u0089', Character.MAX_VALUE))
      assert(!s.contains(Character.MIN_VALUE))
      assert(s.contains(Character.MAX_VALUE))
      assert(!s.contains('\u0042'))
      assert(!s.contains('\u0088'))
      assert(s.contains('\u0089'))
      assert(s.contains('\u0090'))
      assert(s.contains('\uf3ab'))
    }

    it("IntRangeSet.between(89..65535) should contain some elements") {
      val s = IntRangeSet.of(IntRange.between('\u0089', Character.MAX_VALUE))
      assert(!s.contains(Character.MIN_VALUE))
      assert(s.contains(Character.MAX_VALUE))
      assert(!s.contains('\u0042'))
      assert(!s.contains('\u0088'))
      assert(s.contains('\u0089'))
      assert(s.contains('\u0090'))
      assert(s.contains('\uf3ab'))
    }
  }

  describe("IntRangeSet.intersection") {
    it("should treat IntRangeSet.FULL as identity") {
      for (i1 <- intervals) {
        assert(IntRangeSet.FULL.intersection(i1) == i1)
        assert(i1.intersection(IntRangeSet.FULL) == i1)
      }
    }

    it("should be commutative") {
      for (i1 <- intervals; i2 <- intervals) {
        assert(i1.intersection(i2) == i2.intersection(i1))
      }
    }

    it("should intersect sets with multiple intervals") {
      val s1 = IntRangeSet.of(IntRange.between('\u0004', '\u0012'), IntRange.single('\u0056'))
      val s2 = IntRangeSet.of(IntRange.between('\u0008', '\u0078'), IntRange.between('\u0100', '\u0107'))
      val s3 = IntRangeSet.of(IntRange.between('\u0008', '\u0012'), IntRange.single('\u0056'))
      assert(s1.intersection(s2) == s3)
    }
  }

  describe("IntRangeSet.union") {
    it("should treat IntRangeSet.EMPTY as identity") {
      for (i1 <- intervals) {
        assert(IntRangeSet.EMPTY.union(i1) == i1)
        assert(i1.union(IntRangeSet.EMPTY) == i1)
      }
    }

    it("should be commmutative") {
      for (i1 <- intervals; i2 <- intervals) {
        assert(i1.union(i2) == i2.union(i1))
      }
    }

    it("should union sets with multiple intervals") {
      val s1 = IntRangeSet.of(IntRange.between('\u0004', '\u0012'), IntRange.single('\u0056'))
      val s2 = IntRangeSet.of(IntRange.between('\u0008', '\u0078'), IntRange.between('\u0100', '\u0107'))
      val s3 = IntRangeSet.of(IntRange.between('\u0004', '\u0078'), IntRange.between('\u0100', '\u0107'))
      assert(s1.union(s2) == s3)
    }

    it("should combine contiguous sets in unions") {
      val s1 = IntRangeSet.of(IntRange.between('\u0004', '\u0012'), IntRange.single('\u0056'))
      val s2 = IntRangeSet.of(IntRange.between('\u0013', '\u0055'), IntRange.single('\u0057'))
      val s3 = IntRangeSet.of(IntRange.between('\u0004', '\u0057'))
      assert(s1.union(s2) == s3)
    }
  }

  describe("IntRangeSet.complement") {
    it("should take the complement of a small set") {
      val s1 = IntRangeSet.of(IntRange.between(-3, 3))
      val s2 = IntRangeSet.of(IntRange.between(Integer.MIN_VALUE, -4), IntRange.between(4, Integer.MAX_VALUE))
      assert(s1.complement() == s2)
    }

    it("should take the complement of a set that starts at the minimum") {
      val s1 = IntRangeSet.of(IntRange.between(Integer.MIN_VALUE, -20), IntRange.between(5, 30))
      val s2 = IntRangeSet.of(IntRange.between(-19, 4), IntRange.between(31, Integer.MAX_VALUE))
      assert(s1.complement() == s2)
    }

    it("should take the complement of a set that ends at the maximum") {
      val s1 = IntRangeSet.of(IntRange.between(-20, 5), IntRange.between(30, Integer.MAX_VALUE))
      val s2 = IntRangeSet.of(IntRange.between(Integer.MIN_VALUE, -21), IntRange.between(6, 29))
      assert(s1.complement() == s2)
    }

    it("should take the complement of a set that starts at the minimum and ends at the maximum") {
      val s1 = IntRangeSet.of(IntRange.between(Integer.MIN_VALUE, 5), IntRange.between(30, Integer.MAX_VALUE))
      val s2 = IntRangeSet.of(IntRange.between(6, 29))
      assert(s1.complement() == s2)
    }

    it("should be its self-inverse") {
      for (i1 <- intervals) {
        assert(i1 == i1.complement().complement())
      }
    }
  }

  describe("IntRangeSet.difference") {
    it("should take the difference where the subtracted set is a subset") {
      val s1 = IntRangeSet.of(IntRange.between('0', '9'))
      val s2 = IntRangeSet.of(IntRange.single('4'))
      val s3 = IntRangeSet.of(IntRange.between('0', '3'), IntRange.between('5', '9'))
      assert(s1.difference(s2) == s3)
    }

    it("should take the difference where the subtracted set is a superset") {
      val s1 = IntRangeSet.of(IntRange.single('4'))
      val s2 = IntRangeSet.of(IntRange.between('0', '9'))
      assert(s1.difference(s2) == IntRangeSet.EMPTY)
    }

    it("should take the difference where the subtracted set is overlapping") {
      val s1 = IntRangeSet.of(IntRange.between('0', '6'))
      val s2 = IntRangeSet.of(IntRange.between('4', '9'))
      val s3 = IntRangeSet.of(IntRange.between('0', '3'))
      assert(s1.difference(s2) == s3)
    }

    it("should take the difference where the subtracted set is non-overlapping") {
      val s1 = IntRangeSet.of(IntRange.between('0', '6'))
      val s2 = IntRangeSet.of(IntRange.between('8', '9'))
      assert(s1.difference(s2) == s1)
    }
  }

  describe("IntRangeSet.symmetricDifference") {
    it("should take the symmetric difference of overlapping sets") {
      val s1 = IntRangeSet.of(IntRange.between('a', 'g'))
      val s2 = IntRangeSet.of(IntRange.between('b', 'h'))
      val s3 = IntRangeSet.of(IntRange.single('a'), IntRange.single('h'))
      assert(s1.symmetricDifference(s2) == s3)
    }
  }
}
