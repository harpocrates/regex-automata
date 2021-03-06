package automata.parser;

import static java.util.AbstractMap.SimpleImmutableEntry;

import automata.util.IntRange;
import automata.util.IntRangeSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Utility class for computing sets of code units.
 *
 * @author Alec Theriault
 */
public final class CodePoints {

  private CodePoints() { }

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

  /**
   * Tabulate the set of code points inside a unicode script.
   *
   * @param script unicode script
   * @return set of code points contained in the script
   */
  public static IntRangeSet scriptCodePoints(Character.UnicodeScript script) {
    return IntRangeSet.matching(
      UNICODE_RANGE,
      codePoint -> script.equals(Character.UnicodeScript.of(codePoint))
    );
  }

  /**
   * Tabulate the set of code points inside a unicode block.
   *
   * @param block unicode block
   * @return set of code points contained in the block
   */
  public static IntRangeSet blockCodePoints(Character.UnicodeBlock block) {
    return IntRangeSet.matching(
      UNICODE_RANGE,
      codePoint -> block.equals(Character.UnicodeBlock.of(codePoint))
    );
  }

  /**
   * Tabulate the set of code points inside unicode categories.
   *
   * @param categories unicode categories
   * @return set of code points contained in the block
   */
  public static IntRangeSet categoryCodePoints(int... categories) {

    /* Since there are only thirty categories (0 to 30 inclusive), use a bitmask
     * to check containment. This reduces the work in `matching` to be the same
     * regardless of how many categories are included.
     */
    final int categoryBitmask = IntStream
      .of(categories)
      .map(category -> 1 << category)
      .reduce(0, (m1, m2) -> m1 | m2);

    return IntRangeSet.matching(
      UNICODE_RANGE,
      codePoint -> ((1 << Character.getType(codePoint)) & categoryBitmask) != 0
    );
  }

  /**
   * Given a set of code points, compute the closure under case transformation
   * of ASCII characters.
   *
   * @param codePoints input set of code points
   * @return closure under ASCII case transformation
   */
  public static IntRangeSet asciiCaseInsensitive(IntRangeSet codePoints) {
    var upperAscii = codePoints.intersection(IntRangeSet.of(IntRange.between('A', 'Z')));
    var lowerAscii = codePoints.intersection(IntRangeSet.of(IntRange.between('a', 'z')));
    final int caseDiff = 'A' - 'a';
    if (!upperAscii.isEmpty() || !lowerAscii.isEmpty()) {
      return IntRangeSet.union(
        List.of(
          codePoints,
          new IntRangeSet(
            upperAscii
              .ranges()
              .stream()
              .map(r -> IntRange.between(r.lowerBound() - caseDiff, r.upperBound() - caseDiff))
              .toList()
          ),
          new IntRangeSet(
            lowerAscii
              .ranges()
              .stream()
              .map(r -> IntRange.between(r.lowerBound() + caseDiff, r.upperBound() + caseDiff))
              .toList()
          )
        )
      );
    }
    return codePoints;
  }
}
