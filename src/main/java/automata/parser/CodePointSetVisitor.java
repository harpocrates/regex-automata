package automata.parser;

import static java.util.AbstractMap.SimpleImmutableEntry;

import automata.util.IntRange;
import automata.util.IntRangeSet;
import java.util.Optional;
import java.util.regex.Pattern;

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
  public IntRangeSet visitCharacter(int codePoint, int flags) {
    final var single = IntRangeSet.of(IntRange.single(codePoint));
    return ((flags & Pattern.CASE_INSENSITIVE) == 0)
      ? single
      : CodePoints.asciiCaseInsensitive(single);
  }

  @Override
  public IntRangeSet visitRange(int startCodePoint, int endCodePoint, int flags) {
    final var range = IntRangeSet.of(IntRange.between(startCodePoint, endCodePoint));
    return ((flags & Pattern.CASE_INSENSITIVE) == 0)
      ? range
      : CodePoints.asciiCaseInsensitive(range);
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
  public IntRangeSet visitBuiltinClass(BuiltinClass cls, int flags) {
    return cls.desugar(this, flags);
  }

  @Override
  public IntRangeSet visitUnicodeScript(Character.UnicodeScript script, boolean negated, int flags) {
    final var scriptCodePoints = CodePoints.scriptCodePoints(script);
    return negated ? visitNegated(scriptCodePoints) : scriptCodePoints;
  }

  @Override
  public IntRangeSet visitUnicodeBlock(Character.UnicodeBlock block, boolean negated, int flags) {
    final var blockCodePoints = CodePoints.blockCodePoints(block);
    return negated ? visitNegated(blockCodePoints) : blockCodePoints;
  }

  @Override
  public Optional<IntRangeSet> visitCodePointSet(IntRangeSet codePoints) {
    return Optional.of(codePoints);
  }
}

