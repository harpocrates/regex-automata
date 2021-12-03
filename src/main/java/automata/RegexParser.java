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
final class RegexParser<A> {

  // Used when "visiting" the AST bottom up
  final private RegexVisitor<A, A> visitor;

  // Book-keeping around position in source
  private final CharSequence input;
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
  public static<B> B parse(RegexVisitor<B, B> visitor, CharSequence input) throws ParseException {
    final var parser = new RegexParser<B>(visitor, input);
    final B parsed = parser.parseUnion();
    if (parser.position < parser.length) {
      throw new ParseException("Expected the end of the regular expression", parser.position);
    }
    return parsed;
  }

  private RegexParser(RegexVisitor<A, A> visitor, CharSequence input) {
    this.visitor = visitor;
    this.input = input;
    this.length = input.length();
  }

  /**
   * Parse a union from the front of the string
   */
  private A parseUnion() throws ParseException {
    A unionLhs = parseConcatenation();
    while (position < length && input.charAt(position) == '|') {
      position++;
      A unionRhs = parseConcatenation();
      unionLhs = visitor.visitAlternation(unionLhs, unionRhs);
    }
    return unionLhs;
  }

  /**
   * Parse a concatenation from the front of the string
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
   * Parse a quantified repetition from a non-empty input
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
          // Parse `atLeast`
          atLeast = parseDecimalInteger();

          // Parse `atMost`
          if (position < length && input.charAt(position) == ',') {
            position++;
            if (position < length && input.charAt(position) != '}') {
              atMost = OptionalInt.of(parseDecimalInteger());
            }
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
   * Parse a group from a non-empty input
   */
  private A parseGroup() throws ParseException {
    if (input.charAt(position) != '(') {
      return parseCharacter();
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
    final A union = parseUnion();
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
   * Parse a character (or escaped character) from a non-empty input
   */
  private A parseCharacter() throws ParseException {
    char c = input.charAt(position);
    switch (c) {
      case '^':
        position++;
        return visitor.visitBoundary(RegexVisitor.Boundary.BEGINNING);

      case '$':
        position++;
        return visitor.visitBoundary(RegexVisitor.Boundary.END);

      case '.':
        throw new ParseException("Wilcard character is not yet supported", position);

      case '[':
        throw new ParseException("Character classes are not yet supported", position);

      case '\\':
        position++;
        if (position >= length) {
          throw new ParseException("Pattern may not end with backslash", position);
        }

        // Re-used in the switch
        int charCode;

        c = input.charAt(position);
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
            return visitor.visitCharacter(c);

          // Bell character
          case 'a':
            position++;
            return visitor.visitCharacter('\u0007');

          // Escape character
          case 'e':
            position++;
            return visitor.visitCharacter('\u001B');

          // Form-feed
          case 'f':
            position++;
            return visitor.visitCharacter('\f');

          // Tab
          case 't':
            position++;
            return visitor.visitCharacter('\t');

          // Newline
          case 'n':
            position++;
            return visitor.visitCharacter('\n');

          // Carriage return
          case 'r':
            position++;
            return visitor.visitCharacter('\r');

          // Vertical tab
          case 'v':
            position++;
            return visitor.visitCharacter('\u000b');

          // Hexadecimal escape `\xhh` or `\x{h...h}`
          case 'x':
            position++;

            if (position >= length) {
              throw new ParseException("Hexadecimal or `{` but got end of regex", position);
            } else if (input.charAt(position) == '{') {
              position++;

              // Loop over hex characters
              charCode = 0;
              while (position < length) {
                if (input.charAt(position) == '}') {
                  position++;

                  // Is this one or two code points?
                  if (Character.charCount(charCode) == 1) {
                    return visitor.visitCharacter((char)charCode);
                  } else {
                    final var hi = visitor.visitCharacter(Character.highSurrogate(charCode));
                    final var lo = visitor.visitCharacter(Character.lowSurrogate(charCode));
                    return visitor.visitConcatenation(hi, lo);
                  }
                }

                charCode = (charCode << 4) | parseHexadecimalCharacter();
                if (charCode < Character.MIN_CODE_POINT || charCode > Character.MAX_CODE_POINT) {
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
              charCode = parseHexadecimalCharacter();
              charCode = (charCode << 4) | parseHexadecimalCharacter();
              return visitor.visitCharacter((char)charCode);
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
            charCode = parseHexadecimalCharacter();
            charCode = (charCode << 4) | parseHexadecimalCharacter();
            charCode = (charCode << 4) | parseHexadecimalCharacter();
            charCode = (charCode << 4) | parseHexadecimalCharacter();
            return visitor.visitCharacter((char)charCode);

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

      default:
        position++;
        if (Character.isHighSurrogate(c) && position < length) {
          final var hi = visitor.visitCharacter(c);
          final var lo = visitor.visitCharacter(input.charAt(position));
          position++;
          return visitor.visitConcatenation(hi, lo);
        } else {
          return visitor.visitCharacter(c);
        }
    }
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
