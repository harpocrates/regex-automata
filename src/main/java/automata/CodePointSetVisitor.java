package automata;

/**
 * Interpret character classes into ranges of unicode code points.
 *
 * The outputs of this visitor should always be subsets of {@code UNICODE_RANGE}.
 */
public class CodePointSetVisitor implements CharClassVisitor<IntRangeSet> {

  final public static CodePointSetVisitor INSTANCE = new CodePointSetVisitor();
  final public static IntRange UNICODE_RANGE = IntRange.between(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
  final public static IntRange BMP_RANGE = IntRange.between(Character.MIN_VALUE, Character.MAX_VALUE);
  final public static IntRange LOW_SURROGATE_RANGE = IntRange.between(Character.MIN_LOW_SURROGATE, Character.MAX_LOW_SURROGATE);
  final public static IntRange HIGH_SURROGATE_RANGE = IntRange.between(Character.MIN_HIGH_SURROGATE, Character.MAX_HIGH_SURROGATE);


  @Override
  public IntRangeSet visitCharacter(int codePoint) {
    return IntRangeSet.of(IntRange.single(codePoint));
  }

  @Override
  public IntRangeSet visitRange(int startCodePoint, int endCodePoint) {
    return IntRangeSet.of(IntRange.between(startCodePoint, endCodePoint));
  }

  @Override
  public IntRangeSet visitNegated(IntRangeSet negate) {
    return IntRangeSet.of(UNICODE_RANGE).difference(negate);
  }

  @Override
  public IntRangeSet visitUnion(IntRangeSet lhs, IntRangeSet rhs) {
    return lhs.union(rhs);
  }

  @Override
  public IntRangeSet visitIntersection(IntRangeSet lhs, IntRangeSet rhs) {
    return lhs.intersection(rhs);
  }

  @Override
  public IntRangeSet visitBuiltinClass(CharClassVisitor.BuiltinClass cls) {
    return cls.desugar(this);
  }
}
