package automata.parser;

import static java.util.AbstractMap.SimpleImmutableEntry;

import automata.util.IntRange;
import automata.util.IntRangeSet;
import java.util.Map;
import java.util.Optional;

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
   * Tabulate the set of code points inside a unicode category.
   *
   * @param category unicode category
   * @return set of code points contained in the block
   */
  public static IntRangeSet categoryCodePoints(int category) {
    return IntRangeSet.matching(
      UNICODE_RANGE,
      codePoint -> Character.getType(codePoint) == category
    );
  }
}
