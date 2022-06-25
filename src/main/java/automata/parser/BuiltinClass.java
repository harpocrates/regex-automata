package automata.parser;

import automata.util.IntRangeSet;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Special character classes.
 *
 * <p>TODO: support more of these
 */
public enum BuiltinClass {
  /**
   * Any character.
   */
  DOT {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      D dotAll = visitor.visitRange(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT, flags);
      if ((flags & Pattern.DOTALL) == 0) {
        final D lineTerminator = visitor.visitNegated(LINE_TERMINATOR.desugar(visitor, flags));
        dotAll = visitor.visitIntersection(dotAll, lineTerminator);
      }
      return dotAll;
    }
  },

  /**
   * Digit character.
   */
  DIGIT {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      if ((flags & Pattern.UNICODE_CHARACTER_CLASS) != 0) {
        final var unicodeDigits = IntRangeSet.matching(
          CodePoints.UNICODE_RANGE,
          Character::isDigit
        );
        return visitor.visitCodePointSet(unicodeDigits).get();
      } else {
        return visitor.visitRange('0', '9', flags);
      }
    }
  },

  /**
   * Non-digit character.
   */
  NON_DIGIT {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      return visitor.visitNegated(DIGIT.desugar(visitor, flags));
    }
  },

  /**
   * Horizontal whitespace character.
   */
  HORIZONTAL_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      D space = visitor.visitCharacter(' ', flags);
      space = visitor.visitUnion(space, visitor.visitCharacter('\t', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u00A0', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u1680', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u180e', flags));
      space = visitor.visitUnion(space, visitor.visitRange('\u2000', '\u200a', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u202f', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u205f', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u3000', flags));
      return space;
    }
  },

  /**
   * Non-horizontal whitespace character.
   */
  NON_HORIZONTAL_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      return visitor.visitNegated(HORIZONTAL_WHITE_SPACE.desugar(visitor, flags));
    }
  },

  /**
   * Whitespace character.
   */
  WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      D space = visitor.visitCharacter(' ', flags);
      space = visitor.visitUnion(space, visitor.visitCharacter('\t', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\n', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u000B', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\f', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\r', flags));
      return space;
    }
  },

  /**
   * Non-whitespace character.
   */
  NON_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      return visitor.visitNegated(WHITE_SPACE.desugar(visitor, flags));
    }
  },

  /**
   * Vertical whitespace character.
   */
  VERTICAL_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      D space = visitor.visitCharacter('\n', flags);
      space = visitor.visitUnion(space, visitor.visitCharacter('\u000B', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\f', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\r', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u0085', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u2028', flags));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u2029', flags));
      return space;
    }
  },

  /**
   * Non-vertical whitespace character.
   */
  NON_VERTICAL_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      return visitor.visitNegated(VERTICAL_WHITE_SPACE.desugar(visitor, flags));
    }
  },

  /**
   * Word character.
   */
  WORD {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      D word = visitor.visitCharacter('_', flags);
      word = visitor.visitUnion(word, visitor.visitRange('a', 'z', flags));
      word = visitor.visitUnion(word, visitor.visitRange('A', 'Z', flags));
      word = visitor.visitUnion(word, visitor.visitRange('0', '9', flags));
      return word;
    }
  },

  /**
   * Non-word character.
   */
  NON_WORD {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      return visitor.visitNegated(WORD.desugar(visitor, flags));
    }
  },

  /**
   * Line terminator.
   *
   * Note: this is not a user-writable character class. It is also not _exactly_
   * what is understood by a "line terminator" in the context of `^` or `$`
   * under multiline mode: it is missing the two codepoint sequence `\r\n`.
   */
  LINE_TERMINATOR {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor, int flags) {
      D term = visitor.visitCharacter('\n', flags);
      if ((flags & Pattern.UNIX_LINES) == 0) {
        term = visitor.visitUnion(term, visitor.visitCharacter('\r', flags));
        term = visitor.visitUnion(term, visitor.visitCharacter('\u0085', flags));
        term = visitor.visitUnion(term, visitor.visitCharacter('\u2028', flags));
        term = visitor.visitUnion(term, visitor.visitCharacter('\u2029', flags));
      }
      return term;
    }
  };

  public abstract <D> D desugar(CharClassVisitor<D> visitor, int flags);

  /**
   * Mapping from the character used to represent the class to the class.
   */
  public static Map<Character, BuiltinClass> CHARACTERS = Map.of(
    'd', BuiltinClass.DIGIT,
    'D', BuiltinClass.NON_DIGIT,
    'h', BuiltinClass.HORIZONTAL_WHITE_SPACE,
    'H', BuiltinClass.NON_HORIZONTAL_WHITE_SPACE,
    's', BuiltinClass.WHITE_SPACE,
    'S', BuiltinClass.NON_WHITE_SPACE,
    'v', BuiltinClass.VERTICAL_WHITE_SPACE,
    'V', BuiltinClass.NON_VERTICAL_WHITE_SPACE,
    'w', BuiltinClass.WORD,
    'W', BuiltinClass.NON_WORD
  );
}

