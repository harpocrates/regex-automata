package automata.parser;

import static java.util.AbstractMap.SimpleImmutableEntry;

import automata.util.IntRange;
import automata.util.IntRangeSet;

/**
 * Interpret character classes into range sets of unicode code points.
 *
 * <p>Any set returned from this visitor should be a subset of
 * {@link CodePoints#UNICODE_RANGE}.
 *
 * @author Alec Theriault
 */
public class CodePointSetVisitor implements CharClassVisitor<IntRangeSet> {

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
    return IntRangeSet.of(CodePoints.UNICODE_RANGE).difference(negate);
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

  @Override
  public IntRangeSet visitUnicodeScript(Character.UnicodeScript script, boolean negated) {
    final var scriptCodePoints = CodePoints.scriptCodePoints(script);
    return negated ? visitNegated(scriptCodePoints) : scriptCodePoints;
  }

  @Override
  public IntRangeSet visitUnicodeBlock(Character.UnicodeBlock block, boolean negated) {
    final var blockCodePoints = CodePoints.blockCodePoints(block);
    return negated ? visitNegated(blockCodePoints) : blockCodePoints;
  }
}

