package automata;

/**
 * Bottom-up traversal of the character class AST.
 *
 * @param <C> output from traversing the pattern AST
 */
public interface CharClassVisitor<C> {

  /**
   * Matches a single abstract character.
   *
   * An abstract character is represented by its unicode code point. Note that
   * this is not equivalent to a Java `char`, which is instead a code unit (and
   * where unicode code points get encoded as one or two code units in UTF-16).
   *
   * @param codePoint unicode code point to match
   */
  C visitCharacter(int codePoint);

  /**
   * Matches a range of abstract characters.
   *
   * @param startCodePoint first code point in the range (inclusive)
   * @param endCodePoint last code point in the range (inclusive)
   */
  C visitRange(int startCodePoint, int endCodePoint);

  /**
   * Matches all characters that don't match another pattern.
   *
   * @param negate negated pattern
   */
  C visitNegated(C negate);

  /**
   * Matches characters in either class.
   *
   * Note that "union" in a character class has no symbolic operator - classes
   * are implicitly unioned. Unlike a regular expression union, this _is_
   * symmetric.
   *
   * @param lhs first class of characters
   * @param rhs second class of characters
   */
  C visitUnion(C lhs, C rhs);

  /**
   * Matches characters in both classes.
   *
   * @param lhs first class of characters
   * @param rhs second class of characters
   */
  C visitIntersection(C lhs, C rhs);

  /**
   * Matches characters inside a builtin character class.
   *
   * @param cls builtin class
   */
  C visitBuiltinClass(BuiltinClass cls);

  /**
   * Special character classes.
   *
   * TODO: support more of these
   */
  enum BuiltinClass {
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
  }
}

