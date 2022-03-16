package automata.parser;

import java.util.Map;

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
    public <D> D desugar(CharClassVisitor<D> visitor) {
      return visitor.visitRange(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
    }
  },

  /**
   * Digit character.
   */
  DIGIT {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      return visitor.visitRange('0', '9');
    }
  },

  /**
   * Non-digit character.
   */
  NON_DIGIT {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      return visitor.visitNegated(DIGIT.desugar(visitor));
    }
  },

  /**
   * Horizontal whitespace character.
   */
  HORIZONTAL_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      D space = visitor.visitCharacter(' ');
      space = visitor.visitUnion(space, visitor.visitCharacter('\t'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u00A0'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u1680'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u180e'));
      space = visitor.visitUnion(space, visitor.visitRange('\u2000', '\u200a'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u202f'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u205f'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u3000'));
      return space;
    }
  },

  /**
   * Non-horizontal whitespace character.
   */
  NON_HORIZONTAL_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      return visitor.visitNegated(HORIZONTAL_WHITE_SPACE.desugar(visitor));
    }
  },

  /**
   * Whitespace character.
   */
  WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      D space = visitor.visitCharacter(' ');
      space = visitor.visitUnion(space, visitor.visitCharacter('\t'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\n'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u000B'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\f'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\r'));
      return space;
    }
  },

  /**
   * Non-whitespace character.
   */
  NON_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      return visitor.visitNegated(WHITE_SPACE.desugar(visitor));
    }
  },

  /**
   * Vertical whitespace character.
   */
  VERTICAL_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      D space = visitor.visitCharacter('\n');
      space = visitor.visitUnion(space, visitor.visitCharacter('\u000B'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\f'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\r'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u0085'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u2028'));
      space = visitor.visitUnion(space, visitor.visitCharacter('\u2029'));
      return space;
    }
  },

  /**
   * Non-vertical whitespace character.
   */
  NON_VERTICAL_WHITE_SPACE {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      return visitor.visitNegated(VERTICAL_WHITE_SPACE.desugar(visitor));
    }
  },

  /**
   * Word character.
   */
  WORD {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      D word = visitor.visitCharacter('_');
      word = visitor.visitUnion(word, visitor.visitRange('a', 'z'));
      word = visitor.visitUnion(word, visitor.visitRange('A', 'Z'));
      word = visitor.visitUnion(word, visitor.visitRange('0', '9'));
      return word;
    }
  },

  /**
   * Non-word character.
   */
  NON_WORD {
    @Override
    public <D> D desugar(CharClassVisitor<D> visitor) {
      return visitor.visitNegated(WORD.desugar(visitor));
    }
  };

  public abstract <D> D desugar(CharClassVisitor<D> visitor);

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

