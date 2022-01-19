package automata.parser;

import automata.util.IntRange;
import automata.util.IntRangeSet;

/**
 * Interpret character classes into range sets of unicode code points.
 *
 * The outputs of this visitor should be subsets of {@link #UNICODE_RANGE}.
 *
 * @author Alec Theriault
 */
public class CodePointSetVisitor implements CharClassVisitor<IntRangeSet> {

  /**
   * Shared instance of the visitor.
   */
  final public static CodePointSetVisitor INSTANCE = new CodePointSetVisitor();

  /**
   * Range of unicode code points: {@code U+0000-U+10FFFF}.
   */
  final public static IntRange UNICODE_RANGE = IntRange.between(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);

  /**
   * Range of basic multilingual place (BMP) code points: {@code U+0000-U+FFFF}.
   */
  final public static IntRange BMP_RANGE = IntRange.between(Character.MIN_VALUE, Character.MAX_VALUE);

  /**
   * Range of low surrogate code points: {@code U+DC00-U+DFFF}.
   */
  final public static IntRange LOW_SURROGATE_RANGE = IntRange.between(Character.MIN_LOW_SURROGATE, Character.MAX_LOW_SURROGATE);

  /**
   * Range of high surrogate code points: {@code U+D800-U+DBFF}.
   */
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
  public IntRangeSet visitBuiltinClass(BuiltinClass cls) {
    return cls.desugar(this);
  }
}
