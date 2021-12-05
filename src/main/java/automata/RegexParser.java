package automata;

import java.text.ParseException;
import java.util.OptionalInt;

/**
 * Parser for a subset of regular expressions.
 *
 * This is a fairly standard recursive descent parsed. The only more
 * interesting bit is that the results are made available through a visitor
 * instead of as an explicit AST type.
 *
 * TODO: document supported syntax
 */
public final class RegexParser<A, C> {

  // Used when "visiting" the AST bottom up
  final private RegexVisitor<A, C> visitor;

  // Book-keeping around position in source
  private final String input;
  private final int length;
  private int position = 0;
  private int groupCount = 0;

  /**
   * Parse a regular expression pattern from an input string.
   *
   * The regex visitor can be used to either process the AST incrementally or
   * else build up an explicit AST.
   *
   * @param visitor regex visitor used to accept bottom-up parsing progress
   * @param input regular expression pattern
   * @return parsed regular expression
   */
  public static<B, D> B parse(RegexVisitor<B, D> visitor, String input) throws ParseException {
    final var parser = new RegexParser<B, D>(visitor, input);
    final B parsed = parser.parseAlternation();
    if (parser.position < parser.length) {
      throw new ParseException("Expected the end of the regular expression", parser.position);
    }
    return parsed;
  }

  private RegexParser(RegexVisitor<A, C> visitor, String input) {
    this.visitor = visitor;
    this.input = input;
    this.length = input.length();
  }

  /**
   * Parse an alternation.
   */
  private A parseAlternation() throws ParseException {
    A unionLhs = parseConcatenation();
    while (position < length && input.charAt(position) == '|') {
      position++;
      A unionRhs = parseConcatenation();
      unionLhs = visitor.visitAlternation(unionLhs, unionRhs);
    }
    return unionLhs;
  }

  /**
   * Parse a concatenation.
   */
  private A parseConcatenation() throws ParseException {
    // Left is `null` until we need it so as to avoid unnecessary `visitEpsilon`
    A concatLhs = null;

    // Keep parsing concatenations until a lower priority construct is encountered
    while (position < length) {
      final char c = input.charAt(position);
      if (c == ')' || c == '|') break;
      A concatRhs = parseQuantified();
      if (concatLhs == null) {
        concatLhs = concatRhs;
      } else {
        concatLhs = visitor.visitConcatenation(concatLhs, concatRhs);
      }
    }

    return (concatLhs == null) ? visitor.visitEpsilon() : concatLhs;
  }

  /**
   * Parse a quantified repetition.
   *
   * Called on non-empty input.
   */
  private A parseQuantified() throws ParseException {
    A quantified = parseGroup();

    // Used for repetitions
    int atLeast = 0;
    OptionalInt atMost = OptionalInt.empty();

    postfix_parsing:
    while (position < length) {
      final char c = input.charAt(position);

      // Pass over the quanitifier stem
      switch (c) {
        case '*':
        case '?':
        case '+':
          position++;
          break;

        case '{':
          position++;
          // Parse `atLeast`
          atLeast = parseDecimalInteger();

          // Parse `atMost`
          if (position < length && input.charAt(position) == ',') {
            position++;
            if (position < length && input.charAt(position) != '}') {
              atMost = OptionalInt.of(parseDecimalInteger());
            }
          } else {
            atMost = OptionalInt.of(atLeast);
          }

          // Close repetition
          if (position >= length || input.charAt(position) != '}') {
            throw new ParseException("Expected `}` to close repetition", position);
          }
          position++;
          break;

        default:
          break postfix_parsing;
      }

      // `?` suffix indicates the quantifier is lazy (reluctant) instead of being greedy
      boolean isLazy = false;
      if (position < length) {
        final char suffix = input.charAt(position);
        switch (suffix) {
          case '?':
            position ++;
            isLazy = true;
            break;

          case '+':
            throw new ParseException(
              "Possesive quantifiers are not supported (use extra parentheses to express one or " +
              "more of an already quatifier expression: `(a?)+` instead of `a?+`",
              position
            );
        }
      }

      // Visit the quantifier corresponding to the initial character
      switch (c) {
        case '*':
          quantified = visitor.visitKleene(quantified, isLazy);
          break;
        case '?':
          quantified = visitor.visitOptional(quantified, isLazy);
          break;
        case '+':
          quantified = visitor.visitPlus(quantified, isLazy);
          break;
        case '{':
          quantified = visitor.visitRepetition(quantified, atLeast, atMost, isLazy);
          break;
      }
    }

    return quantified;
  }

  /**
   * Parse a group.
   *
   * Called on non-empty input.
   */
  private A parseGroup() throws ParseException {
    switch (input.charAt(position)) {
      case '(':
        break;

      case '^':
        position++;
        return visitor.visitBoundary(RegexVisitor.Boundary.BEGINNING);

      case '$':
        position++;
        return visitor.visitBoundary(RegexVisitor.Boundary.END);

      default:
        return visitor.visitCharacterClass(parseCharacterClass());
    }

    // Track the open paren so we can use it in the error message
    final int openParenPosition = position;
    position++;

    // Detect `?:` non capture groups
    boolean capture = true;
    if (position < length && input.charAt(position) == '?') {
      position++;

      // We don't error if we reach the end because we'll anyways error on an unclosed group below
      if (position < length) {
        if (input.charAt(position) == ':') {
          position++;
          capture = false;
        } else {
          throw new ParseException(
            "Only non-capturing groups are supported (lookaheads and lookbehinds aren't)",
            position
          );
        }
      }
    }

    // If this a capture group, increment the group count
    final var groupIdx = (capture) ? OptionalInt.of(groupCount++) : OptionalInt.empty();

    // Parse the group body and ensure that it is closed
    final A union = parseAlternation();
    if (!(position < length && input.charAt(position) == ')')) {
      throw new ParseException(
        "Unclosed group (expected close paren for group opened at " + openParenPosition,
        position
      );
    } else {
      position++;
    }

    return visitor.visitGroup(union, groupIdx);
  }

  /**
   * Parse a character or character class.
   *
   * Called on non-empty input.
   */
  private C parseCharacterClass() throws ParseException {
    switch (input.charAt(position)) {
      case '.':
        throw new ParseException("Wilcard character is not yet supported", position);

      case '[':
        // Track the open paren so we can use it in the error message
        final int openBracketPosition = position;
        position++;

        // Is the class negated?
        boolean negated = position < length && input.charAt(position) == '^';
        if (negated) {
          position++;
        }
        if (position >= length) {
          throw new ParseException(
            "Unclosed character class (expected close to bracket opened at " + openBracketPosition + ")",
            position
          );
        }

        // Class body
        final C union = parseClassUnion();
        if (!(position < length && input.charAt(position) == ']')) {
          throw new ParseException(
            "Unclosed character class (expected close to bracket opened at " + openBracketPosition + ")",
            position
          );
        }
        position++;
        return negated ? visitor.visitNegated(union) : union;

      default:
        return visitor.visitCharacter(parseCharacter(false));
    }
  }

  /**
   * Parse the code point of an "abstract" character (or escaped character).
   *
   * Called on non-empty input.
   *
   * @param insideClass is the parsing already inside a backet character class
   */
  private int parseCharacter(boolean insideClass) throws ParseException {
    int codePoint;
    if (input.charAt(position) == '\\') {
      position++;
      if (position >= length) {
        throw new ParseException("Pattern may not end with backslash", position);
      }

      final char c = input.charAt(position);
      switch (c) {
        case '\\':
        case '.':
        case '+':
        case '*':
        case '?':
        case '(':
        case ')':
        case '|':
        case '[':
        case ']':
        case '{':
        case '}':
        case '^':
        case '$':
          position++;
          return c;

        // Bell character
        case 'a':
          position++;
          return '\u0007';

        // Escape character
        case 'e':
          position++;
          return '\u001B';

        // Form-feed
        case 'f':
          position++;
          return '\f';

        // Tab
        case 't':
          position++;
          return '\t';

        // Newline
        case 'n':
          position++;
          return '\n';

        // Carriage return
        case 'r':
          position++;
          return '\r';

        // Vertical tab
        case 'v':
          position++;
          return '\u000b';

        // Hexadecimal escape `\xhh` or `\x{h...h}`
        case 'x':
          position++;

          if (position >= length) {
            throw new ParseException("Hexadecimal or `{` but got end of regex", position);
          } else if (input.charAt(position) == '{') {
            position++;

            // Loop over hex characters
            codePoint = 0;
            while (position < length) {
              if (input.charAt(position) == '}') {
                position++;
                return codePoint;
              }

              codePoint = (codePoint << 4) | parseHexadecimalCharacter();
              if (codePoint < Character.MIN_CODE_POINT || codePoint > Character.MAX_CODE_POINT) {
                throw new ParseException(
                  "Hexadecimal escape overflowed max code point",
                  position
                );
              }
            }
            throw new ParseException("Hexadecimal or `}` but got end of regex", position);
          } else {
            if (position + 2 > length) {
              throw new ParseException(
                "Expected 2 hexadecimal escape characters but got end of regex",
                position
               );
            }
            codePoint = parseHexadecimalCharacter();
            codePoint = (codePoint << 4) | parseHexadecimalCharacter();
            return codePoint;
          }

        // Hexadecimal escape `\\uhhhh`
        case 'u':
          position++;
          if (position + 4 > length) {
            throw new ParseException(
              "Expected 4 hexadecimal escape characters but got end of regex",
              position
             );
          }
          codePoint = parseHexadecimalCharacter();
          codePoint = (codePoint << 4) | parseHexadecimalCharacter();
          codePoint = (codePoint << 4) | parseHexadecimalCharacter();
          codePoint = (codePoint << 4) | parseHexadecimalCharacter();
          return codePoint;

        // Back-references
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
        case 'k':
          throw new ParseException("Backreferences are not supported", position);

        default:
          throw new ParseException("Unknown escape sequence", position);
      }
    } else {
      codePoint = input.codePointAt(position);
      position += Character.charCount(codePoint);
      return codePoint;
    }
  }

  /**
   * Parse a union inside a bracket-delimited class.
   *
   * Called on non-empty input.
   */
  private C parseClassUnion() throws ParseException {
    C union = parseClassIntersection();

    // Keep parsing unions until a lower priority construct is encountered
    union_loop: while (position < length) {
      switch (input.charAt(position)) {
        // Intersection
        case '&':
          if (position + 1 >= length || input.charAt(position + 1) == '&') {
            break;
          }
          break union_loop;

        // End of class
        case ']':
          break union_loop;
      }
      union = visitor.visitUnion(union, parseClassIntersection());
    }

    return union;
  }

  /**
   * Parse an intersection inside a bracket-delimited class.
   *
   * Called on non-empty input.
   */
  private C parseClassIntersection() throws ParseException {
    C intersection = parseCharacterRange();

    while (position + 2 < length) {
      if (input.charAt(position) != '&' || input.charAt(position + 1) != '&') {
        break;
      }
      position += 2;
      if (position >= length) {
        throw new ParseException("Invalid class intersection, missing right hand side", position);
      }
      intersection = visitor.visitIntersection(intersection, parseCharacterRange());
    }

    return intersection;
  }

  /**
   * Parse a character range inside a bracket-delimited class.
   *
   * Called on non-empty input.
   */
  private C parseCharacterRange() throws ParseException {
    if (input.charAt(position) == '[') {
      return parseCharacterClass();
    }

    final int ch = parseCharacter(true);
    if (position + 1 < length && input.charAt(position) == '-') {
      switch (input.charAt(position + 1)) {
        case '[':
          throw new ParseException("Invalid character range, missing right hand side", position);

        // `-` is literal
        case ']':
          break;

        // Check for intersection syntax
        case '&':
          if (position + 2 < length && input.charAt(position + 2) == '&') {
            break;
          }
      }

      position++;
      return visitor.visitRange(ch, parseCharacter(true));
    }
    return visitor.visitCharacter(ch);
  }

  /**
   * Parse a hexadecimal character from a non-empty input
   */
  private int parseHexadecimalCharacter() throws ParseException {
    final int h = Character.digit(input.charAt(position++), 16);
    if (h < 0) {
      throw new ParseException("Expected a hexadecimal character", position);
    } else {
      return h;
    }
  }

  /**
   * Parse a positive (possibly empty) decimal integer from the input
   */
  private int parseDecimalInteger() throws ParseException {
    long integer = 0;
    while (position < length) {
      // Try to read another decimal character
      final int d = Character.digit(input.charAt(position), 10);
      if (d < 0) {
        break;
      }

      // Increment the number
      integer = 10 * integer + d;
      if (integer > Integer.MAX_VALUE) {
        throw new ParseException("Decimal integer overflowed", position);
      }
      position++;
    }
    return (int) integer;
  }

}
