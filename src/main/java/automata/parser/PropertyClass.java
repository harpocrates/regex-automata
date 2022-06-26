package automata.parser;

import automata.util.IntRange;
import automata.util.IntRangeSet;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Property classes.
 *
 * These are a best-effort matching of what Java supports.
 */
public enum PropertyClass {

  CN("Cn", Character.UNASSIGNED),

  LU(
    "Lu",
    new int[] { Character.UPPERCASE_LETTER },
    new int[] {
      Character.LOWERCASE_LETTER,
      Character.UPPERCASE_LETTER,
      Character.TITLECASE_LETTER
    }
  ),

  LL(
    "Ll",
    new int[] { Character.LOWERCASE_LETTER },
    new int[] {
      Character.LOWERCASE_LETTER,
      Character.UPPERCASE_LETTER,
      Character.TITLECASE_LETTER
    }
  ),

  LT(
    "Lt",
    new int[] { Character.TITLECASE_LETTER },
    new int[] {
      Character.LOWERCASE_LETTER,
      Character.UPPERCASE_LETTER,
      Character.TITLECASE_LETTER
    }
  ),

  LM("Lm", Character.MODIFIER_LETTER),

  LO("Lo", Character.OTHER_LETTER),

  MN("Mn", Character.NON_SPACING_MARK),

  ME("Me", Character.ENCLOSING_MARK),

  MC("Mc", Character.COMBINING_SPACING_MARK),

  ND("Nd", Character.DECIMAL_DIGIT_NUMBER),

  NL("Nl", Character.LETTER_NUMBER),

  NO("No", Character.OTHER_NUMBER),

  ZS("Zs", Character.SPACE_SEPARATOR),

  ZL("Zl", Character.LINE_SEPARATOR),

  ZP("Zp", Character.PARAGRAPH_SEPARATOR),

  CC("Cc", Character.CONTROL),

  CF("Cf", Character.FORMAT),

  CO("Co", Character.PRIVATE_USE),

  CS("Cs", Character.SURROGATE),

  PD("Pd", Character.DASH_PUNCTUATION),

  PS("Ps", Character.START_PUNCTUATION),

  PE("Pe", Character.END_PUNCTUATION),

  PC("Pc", Character.CONNECTOR_PUNCTUATION),

  PO("Po", Character.OTHER_PUNCTUATION),

  SM("Sm", Character.MATH_SYMBOL),

  SC("Sc", Character.CURRENCY_SYMBOL),

  SK("Sk", Character.MODIFIER_SYMBOL),

  SO("So", Character.OTHER_SYMBOL),

  PI("Pi", Character.INITIAL_QUOTE_PUNCTUATION),

  PF("Pf", Character.FINAL_QUOTE_PUNCTUATION),

  L(
    "L",
    new int[] {
      Character.UPPERCASE_LETTER,
      Character.LOWERCASE_LETTER,
      Character.TITLECASE_LETTER,
      Character.MODIFIER_LETTER,
      Character.OTHER_LETTER
    }
  ),

  M(
    "M",
    new int[] {
      Character.NON_SPACING_MARK,
      Character.ENCLOSING_MARK,
      Character.COMBINING_SPACING_MARK
    }
  ),

  N(
    "N",
    new int[] {
      Character.DECIMAL_DIGIT_NUMBER,
      Character.LETTER_NUMBER,
      Character.OTHER_NUMBER
    }
  ),

  Z(
    "Z",
    new int[] {
      Character.SPACE_SEPARATOR,
      Character.LINE_SEPARATOR,
      Character.PARAGRAPH_SEPARATOR
    }
  ),

  C(
    "C",
    new int[] {
      Character.CONTROL,
      Character.FORMAT,
      Character.PRIVATE_USE,
      Character.SURROGATE,
      Character.UNASSIGNED
    }
  ),

  P(
    "P",
    new int[] {
      Character.DASH_PUNCTUATION,
      Character.START_PUNCTUATION,
      Character.END_PUNCTUATION,
      Character.CONNECTOR_PUNCTUATION,
      Character.OTHER_PUNCTUATION,
      Character.INITIAL_QUOTE_PUNCTUATION,
      Character.FINAL_QUOTE_PUNCTUATION
    }
  ),

  S(
    "S",
    new int[] {
      Character.MATH_SYMBOL,
      Character.CURRENCY_SYMBOL,
      Character.MODIFIER_SYMBOL,
      Character.OTHER_SYMBOL
    }
  ),

  LC(
    "LC",
    new int[] {
      Character.UPPERCASE_LETTER,
      Character.LOWERCASE_LETTER,
      Character.TITLECASE_LETTER
    }
  ),

  LD(
    "LD",
    new int[] {
      Character.UPPERCASE_LETTER,
      Character.LOWERCASE_LETTER,
      Character.TITLECASE_LETTER,
      Character.MODIFIER_LETTER,
      Character.OTHER_LETTER,
      Character.DECIMAL_DIGIT_NUMBER
    }
  ),

  L1("L1", IntRangeSet.of(IntRange.between(0, 0xFF))),

  ALL("all", IntRangeSet.of(CodePoints.UNICODE_RANGE)),

  ASCII("ASCII", IntRangeSet.of(IntRange.between(0, 0x7F))),

  ALNUM(
    "Alnum",
    IntRangeSet.of(
      IntRange.between('0', '9'),
      IntRange.between('A', 'Z'),
      IntRange.between('a', 'z')
    )
  ),

  ALPHA(
    "Alpha",
    IntRangeSet.of(
      IntRange.between('A', 'Z'),
      IntRange.between('a', 'z')
    )
  ),

  BLANK("Blank", IntRangeSet.of(IntRange.single(0x09), IntRange.single(0x20))),

  CNTRL(
    "Cntrl",
    IntRangeSet.of(IntRange.between(0, 0x1F), IntRange.single(0x7F))
  ),

  DIGIT("Digit", IntRangeSet.of(IntRange.between('0', '9'))),

  GRAPH(
    "Graph",
    IntRangeSet.of(IntRange.between('!', '~'))),

  LOWER(
    "Lower",
    IntRangeSet.of(IntRange.between('a', 'z')),
    IntRangeSet.of(IntRange.between('A', 'Z'), IntRange.between('a', 'z'))
  ),

  PRINT("Print", IntRangeSet.of(IntRange.between(' ', '~'))),

  PUNCT(
    "Punct",
    IntRangeSet.of(
      IntRange.between('!', '/'),
      IntRange.between(':', '@'),
      IntRange.between('[', '`'),
      IntRange.between('{', '~')
    )
  ),

  SPACE(
    "Space",
    IntRangeSet.of(IntRange.between(0x09, 0x0D), IntRange.single(0x20))
  ),

  UPPER(
    "Upper",
    IntRangeSet.of(IntRange.between('A', 'Z')),
    IntRangeSet.of(IntRange.between('A', 'Z'), IntRange.between('a', 'z'))
  ),

  XDIGIT(
    "XDigit",
    IntRangeSet.of(
      IntRange.between('0', '9'),
      IntRange.between('A', 'F'),
      IntRange.between('a', 'f')
    )
  ),

  JAVA_LOWERCASE(
    "javaLowerCase",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isLowerCase),
    IntRangeSet.matching(
      CodePoints.UNICODE_RANGE,
      c -> Character.isLowerCase(c) ||
           Character.isUpperCase(c) ||
           Character.isTitleCase(c)
    )
  ),

  JAVA_UPPERCASE(
    "javaUpperCase",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isUpperCase),
    IntRangeSet.matching(
      CodePoints.UNICODE_RANGE,
      c -> Character.isLowerCase(c) ||
           Character.isUpperCase(c) ||
           Character.isTitleCase(c)
    )
  ),

  JAVA_ALPHABETIC(
    "javaAlphabetic",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isAlphabetic)
  ),

  JAVA_IDEOGRAPHIC(
    "javaIdeographic",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isIdeographic)
  ),

  JAVA_TITLECASE(
    "javaTitleCase",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isTitleCase),
    IntRangeSet.matching(
      CodePoints.UNICODE_RANGE,
      c -> Character.isLowerCase(c) ||
           Character.isUpperCase(c) ||
           Character.isTitleCase(c)
    )
  ),

  JAVA_DIGIT(
    "javaDigit",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isDigit)
  ),

  JAVA_ISDEFINED(
    "javaDefined",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isDefined)
  ),

  JAVA_LETTER(
    "javaLetter",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isLetter)
  ),

  JAVA_LETTERORDIGIT(
    "javaLetterOrDigit",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isLetterOrDigit)
  ),

  JAVA_JAVAIDENTIFIERSTART(
    "javaJavaIdentifierStart",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isJavaIdentifierStart)
  ),

  JAVA_JAVAIDENTIFIERPART(
    "javaJavaIdentifierPart",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isJavaIdentifierPart)
  ),

  JAVA_JAVAUNICODEIDENTIFIERSTART(
    "javaJavaUnicodeIdentifierStart",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isUnicodeIdentifierStart)
  ),

  JAVA_JAVAUNICODEIDENTIFIERPART(
    "javaJavaUnicodeIdentifierPart",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isUnicodeIdentifierPart)
  ),

  JAVA_IDENTIFIERIGNORABLE(
    "javaIdentifierIgnorable",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isIdentifierIgnorable)
  ),

  JAVA_SPACECHAR(
    "javaSpaceChar",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isSpaceChar)
  ),

  JAVA_WHITESPACE(
    "javaWhitespace",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isWhitespace)
  ),

  JAVA_ISOCONTROL(
    "javaISOControl",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isISOControl)
  ),

  JAVA_MIRRORED(
    "javaMirrored",
    IntRangeSet.matching(CodePoints.UNICODE_RANGE, Character::isMirrored)
  );

  /**
   * Name associated with the property class.
   */
  public final String label;

  /**
   * Set of contained code points.
   */
  private final IntRangeSet codePoints;

  /**
   * Set of case insensitive code points.
   */
  private final IntRangeSet caseInsensitiveCodePoints;

  /**
   * Create a property class from the case sensitive and insensitive sets of
   * associated codepoints.
   *
   * @param label identifier expected inside the `\p{...}`
   * @param codePoints case-sensitive associated codepoints
   * @param caseInsensitiveCodePoints case-insensitive associated codepoints
   */
  private PropertyClass(
    String label,
    IntRangeSet codePoints,
    IntRangeSet caseInsensitiveCodePoints
  ) {
    this.label = label;
    this.codePoints = codePoints;
    this.caseInsensitiveCodePoints = caseInsensitiveCodePoints;
  }

  /**
   * Create a property class from the associated codepoints.
   *
   * @param label identifier expected inside the `\p{...}`
   * @param codePoints associated codepoints
   */
  private PropertyClass(
    String label,
    IntRangeSet codePoints
  ) {
    this(label, codePoints, codePoints);
  }

  /**
   * Create a property class from the case sensitive and insensitive categories
   * of associated codepoints.
   *
   * @param label identifier expected inside the `\p{...}`
   * @param categories case-sensitive associated categories
   * @param caseInsensitiveCategories case-insensitive associated categories
   */
  private PropertyClass(
    String label,
    int[] categories,
    int[] caseInsensitiveCategories
  ) {
    this(
      label,
      CodePoints.categoryCodePoints(categories),
      CodePoints.categoryCodePoints(caseInsensitiveCategories)
    );
  }

  /**
   * Create a property class from the categories of associated codepoints.
   *
   * @param label identifier expected inside the `\p{...}`
   * @param categories associated categories
   */
  private PropertyClass(
    String label,
    int... categories
  ) {
    this(
      label,
      CodePoints.categoryCodePoints(categories)
    );
  }

  public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
    final boolean caseSensitive = (flags & Pattern.CASE_INSENSITIVE) == 0;
    return visitor
      .visitCodePointSet(caseSensitive ? codePoints : caseInsensitiveCodePoints)
      .get();
  }

  /**
   * Mapping from the name of the property class to the class.
   */
  public static Map<String, PropertyClass> CLASSES = Stream
    .of(PropertyClass.values())
    .collect(Collectors.toMap(pc -> pc.label, pc -> pc));
}
