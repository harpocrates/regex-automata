package automata.parser;

import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Parser for a subset of regular expressions.
 *
 * This is a fairly standard recursive descent parsed. The only more
 * interesting bit is that the results are made available through a visitor
 * instead of as an explicit AST type.
 *
 * TODO: document supported syntax
 *
 * @author Alec Theriault
 */
public final class RegexParser<A, C> {

  // Used when "visiting" the AST bottom up
  final private RegexVisitor<A, C> visitor;

  // Bookeeping around position in source
  private final String input;
  private final int length;
  private int position = 0;
  private int groupCount = 0;

  // Bit-flag of flags
  private int regexFlags = 0;

  // Single possibly stashed codepoint - see `parseCharacter`
  private int stashedCodePoint = 0;

  /**
   * Parse a regular expression pattern from an input string.
   *
   * The regex visitor can be used to either process the AST incrementally or
   * else build up an explicit AST.
   *
   * @param visitor regex visitor used to accept bottom-up parsing progress
   * @param input regular expression pattern
   * @param flags bitmask of match flags
   * @param wrappingGroup is there an implicit outer group wrapping the regex
   * @param wildcardPrefix accept any prefix (lazily) before the regex
   * @return parsed regular expression
   */
  public static<B, D> B parse(
    RegexVisitor<B, D> visitor,
    String input,
    int flags,
    boolean wrappingGroup,
    boolean wildcardPrefix
  ) throws PatternSyntaxException {
    final var parser = new RegexParser<B, D>(visitor, input, flags);
    if (wrappingGroup) {
      parser.groupCount++;
    }

    // Wildcard prefix: [\x{0}-\x{10FFFF}]*?
    B prefix = null;
    if (wildcardPrefix) {
      final D anyCodePoint = visitor.visitRange(
        Character.MIN_CODE_POINT,
        Character.MAX_CODE_POINT
      );
      prefix = visitor.visitKleene(visitor.visitCharacterClass(anyCodePoint), true);
    }

    // Parse the actual expression
    B parsed = parser.parseAlternation();

    // Add a wrapping group
    if (wrappingGroup) {
      parsed = visitor.visitGroup(parsed, OptionalInt.of(0));
    }

    // Add the wildcard prefix
    if (prefix != null) {
      parsed = visitor.visitConcatenation(prefix, parsed);
    }

    if (parser.position < parser.length) {
      throw parser.error("Expected the end of the regular expression");
    }
    return parsed;
  }

  private RegexParser(RegexVisitor<A, C> visitor, String input, int regexFlags) {
    this.visitor = visitor;
    this.input = input;
    this.regexFlags = regexFlags;
    this.length = input.length();
  }

  private PatternSyntaxException error(String message) {
    return new PatternSyntaxException(message, input, position);
  }

  private boolean checkFlag(int flag) {
    return (regexFlags & flag) != 0;
  }

  /**
   * Parse an alternation.
   */
  private A parseAlternation() throws PatternSyntaxException {
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
  private A parseConcatenation() throws PatternSyntaxException {
    // Left is `null` until we need it so as to avoid unnecessary `visitEpsilon`
    A concatLhs = null;

    // Keep parsing concatenations until a lower priority construct is encountered
    while (position < length) {
      final char c = input.charAt(position);
      if ((c == ')' || c == '|') && !checkFlag(Pattern.LITERAL)) break;
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
  private A parseQuantified() throws PatternSyntaxException {
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
            throw error("Expected `}` to close repetition");
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
            throw error(
              "Possesive quantifiers are not supported (use extra parentheses to express one or " +
              "more of an already quatifier expression: `(a?)+` instead of `a?+`"
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
  @SuppressWarnings("fallthrough")
  private A parseGroup() throws PatternSyntaxException {

    if (checkFlag(Pattern.LITERAL)) {
      return parseLiteralSequence();
    }

    switch (input.charAt(position)) {
      case '(':
        break;

      case '^':
        position++;
        return visitor.visitBoundary(Boundary.BEGINNING_OF_LINE);

      case '$':
        position++;
        return visitor.visitBoundary(Boundary.END_OF_LINE);

      case '\\':
        if (position + 1 < length) {
          final char escaped = input.charAt(position + 1);

          // Boundaries
          final var boundary = Boundary.CHARACTERS.get(escaped);
          if (boundary != null) {
            position += 2;
            return visitor.visitBoundary(boundary);
          }

          // Literal sequences
          if (escaped == 'Q') {
            position += 2;
            return parseLiteralSequence();
          }
        }

      default:
        return visitor.visitCharacterClass(parseCharacterClass());
    }

    // Track the open paren so we can use it in the error message
    final int openParenPosition = position;
    position++;

    // Flags to set back after the group is parsed
    int stashedFlags = this.regexFlags;

    // Detect `?:` non capture groups
    boolean capture = true;

    if (position < length && input.charAt(position) == '?') {
      position++;

      // We don't error if we reach the end because we'll anyways error on an unclosed group below
      if (position < length) {
        switch (input.charAt(position)) {
          case '=':
          case '!':
          case '<':
          case '>':
            throw error(
              "Only non-capturing groups are supported (lookaheads and lookbehinds aren't)"
            );

          default:
            capture = false;

            // Parse out flags
            int flags = parseRegexFlags();
            if (position < length && input.charAt(position) == '-') {
              flags &= ~parseRegexFlags();
            }

            if (position < length) {
              final char afterFlags = input.charAt(position);
              if (afterFlags == ':') {
                position++;
                this.regexFlags = flags;
              } else if (afterFlags == ')') {
                this.regexFlags = flags;
                stashedFlags = flags; // Don't reset flags back
              } else {
                throw error(
                  "Invalid non-capturing group (expected colon or close paren for group opened at " + openParenPosition + ")"
                );
              }
            }
        }
      }
    }

    // If this a capture group, increment the group count
    final var groupIdx = (capture) ? OptionalInt.of(groupCount++) : OptionalInt.empty();

    // Parse the group body and ensure that it is closed
    final A union = parseAlternation();
    if (!(position < length && input.charAt(position) == ')')) {
      throw error(
        "Unclosed group (expected close paren for group opened at " + openParenPosition + ")"
      );
    } else {
      position++;
    }

    // Reset flags for non-capturing groups
    if (!capture) {
      this.regexFlags = stashedFlags;
    }

    return visitor.visitGroup(union, groupIdx);
  }

  /**
   * Parse a literal sequence which can be terminated only with {@code \\E}.
   */
  private A parseLiteralSequence() {
    final String END_LITERAL = "\\E";

    // Figure out where the literal sequence ends
    final String literal;
    final int endIndex = input.indexOf(END_LITERAL, position);
    if (endIndex == -1) {
      literal = input.substring(position);
      position = input.length();
    } else {
      literal = input.substring(position, endIndex);
      position = endIndex + END_LITERAL.length();
    }

    // Make sure the literal flag is no longer set
    this.regexFlags = this.regexFlags & ~Pattern.LITERAL;

    return literal
      .codePoints()
      .<C>mapToObj(visitor::visitCharacter)
      .<A>map(visitor::visitCharacterClass)
      .<A>reduce(visitor::visitConcatenation)
      .orElseGet(visitor::visitEpsilon);
  }

  /**
   * Parse a character or character class.
   *
   * Called on non-empty input.
   *
   * @param insideClass is the parsing already inside a backet character class
   */
  private C parseCharacterClass() throws PatternSyntaxException {
    C cls = parseCharacterClassOrCodePoint(false, false);
    if (cls == null) {
      cls = visitor.visitCharacter(stashedCodePoint);
    }
    return cls;
  }

  /**
   * Parse the code point of an "abstract" character, escaped character, or escaped character class.
   *
   * Called on non-empty input. If a code point is found instead of a character class, the code
   * point will be stashed in `stashedCodePoint`.
   *
   * @param insideClass is the parsing already inside a backet character class
   * @param closingClassRange is a non-null return an error?
   * @return character class or `null` if just a code point was found
   */
  private C parseCharacterClassOrCodePoint(
    boolean insideClass,
    boolean closingClassRange
  ) throws PatternSyntaxException {
    int codePoint;
    switch (input.charAt(position)) {
      case '.':
        if (insideClass) {
          position++;
          stashedCodePoint = '.';
          return null;
        } else {
          position++;
          return visitor.visitBuiltinClass(BuiltinClass.DOT);
        }

      // Character class
      case '[':
        if (closingClassRange) {
          throw error("Cannot end class range with '[', position");
        }

        // Track the open paren so we can use it in the error message
        final int openBracketPosition = position;
        position++;

        // Is the class negated?
        boolean negated = position < length && input.charAt(position) == '^';
        if (negated) {
          position++;
        }
        if (position >= length) {
          throw error(
            "Unclosed character class (expected close to bracket opened at " + openBracketPosition + ")"
          );
        }

        // Class body
        final C union = parseClassIntersection();
        if (!(position < length && input.charAt(position) == ']')) {
          throw error(
            "Unclosed character class (expected close to bracket opened at " + openBracketPosition + ")"
          );
        }
        position++;
        return negated ? visitor.visitNegated(union) : union;

      case '\\':
        position++;
        if (position >= length) {
          throw error("Pattern may not end with backslash");
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
            stashedCodePoint = c;
            return null;

          // Character classes
          case 'd':
          case 'D':
          case 'h':
          case 'H':
          case 's':
          case 'S':
          case 'v':
          case 'V':
          case 'w':
          case 'W':
            if (closingClassRange) {
              throw error("Cannot end class range with character class");
            }
            position++;
            return visitor.visitBuiltinClass(BuiltinClass.CHARACTERS.get(c));

          // Bell character
          case 'a':
            position++;
            stashedCodePoint = '\u0007';
            return null;

          // Escape character
          case 'e':
            position++;
            stashedCodePoint = '\u001B';
            return null;

          // Form-feed
          case 'f':
            position++;
            stashedCodePoint = '\f';
            return null;

          // Tab
          case 't':
            position++;
            stashedCodePoint = '\t';
            return null;

          // Newline
          case 'n':
            position++;
            stashedCodePoint = '\n';
            return null;

          // Carriage return
          case 'r':
            position++;
            stashedCodePoint = '\r';
            return null;

          // Control character
          case 'c':
            position++;
            if (position >= length) {
              throw error("Control character letter but got end of regex");
            } else {
              final char control = input.charAt(position);
              if ('A' <= control && control <= 'Z') {
                position++;
                stashedCodePoint = control - 'A' + 112;
                return null;
              } else {
                throw error("Control character letter");
              }
            }

          // Hexadecimal escape `\xhh` or `\x{h...h}`
          case 'x':
            position++;

            if (position >= length) {
              throw error("Hexadecimal or `{` but got end of regex");
            } else if (input.charAt(position) == '{') {
              position++;

              // Loop over hex characters
              codePoint = 0;
              while (position < length) {
                if (input.charAt(position) == '}') {
                  position++;
                  stashedCodePoint = codePoint;
                  return null;
                }

                codePoint = (codePoint << 4) | parseHexadecimalCharacter();
                if (codePoint < Character.MIN_CODE_POINT || codePoint > Character.MAX_CODE_POINT) {
                  throw error("Hexadecimal escape overflowed max code point");
                }
              }
              throw error("Hexadecimal or `}` but got end of regex");
            } else {
              if (position + 2 > length) {
                throw error(
                  "Expected 2 hexadecimal escape characters but got end of regex"
                );
              }
              codePoint = parseHexadecimalCharacter();
              codePoint = (codePoint << 4) | parseHexadecimalCharacter();
              stashedCodePoint = codePoint;
              return null;
            }

          // Hexadecimal escape `\\uhhhh`
          case 'u':
            position++;
            if (position + 4 > length) {
              throw error(
                "Expected 4 hexadecimal escape characters but got end of regex"
              );
            }
            codePoint = parseHexadecimalCharacter();
            codePoint = (codePoint << 4) | parseHexadecimalCharacter();
            codePoint = (codePoint << 4) | parseHexadecimalCharacter();
            codePoint = (codePoint << 4) | parseHexadecimalCharacter();
            stashedCodePoint = codePoint;
            return null;

          // Octal escape `\\0ooo`
          case '0':
            position++;
            if (position >= length) {
              throw error(
                "Expected 1 to 3 octal escape characters but got end of regex"
              );
            }

            // Parse the first octal escape
            int octal = Character.digit(input.charAt(position), 8);
            if (octal < 0) {
              throw error("Expected an octal escape character");
            }
            position++;
            codePoint = octal;

            // Parse the optional second and third octal escapes
            for (int i = 0; i < 2 && position < length; i++) {
              octal = Character.digit(input.charAt(position), 8);
              if (octal < 0) {
                break;
              }
              position++;
              codePoint = (codePoint << 3) | octal;
            }

            stashedCodePoint = codePoint;
            return null;

          // Code point by name
          case 'N':
            position++;

            if (position >= length || input.charAt(position) != '{') {
              throw error("Expected `{`");
            } else {
              final int start = ++position;

              // Loop over characters
              while (position < length) {
                if (input.charAt(position) == '}') {
                  stashedCodePoint = Character.codePointOf(input.substring(start, position));
                  position++;
                  return null;
                }
                position++;
              }
              throw error("`}` but got end of regex");
            }

          // Properties
          case 'p':
            position++;

            // Name of the property
            final String propertyName;
            if (position >= length) {
              throw error("Expected property name but got end of regex");
            } else if (input.charAt(position) != '{') {
              propertyName = input.substring(position, position+1);
            } else {
              final int startProperty = position++;
              do {
                if (position >= length) {
                  throw error("Expected `}` but got end of regex");
                }
              } while (input.charAt(position++) != '}');
              propertyName = input.substring(startProperty + 1, position - 1);
            }

            // Property
            if (propertyName.startsWith("In")) {
              final var blockName = propertyName.substring(2);
              final Character.UnicodeBlock block;
              try {
                block = Character.UnicodeBlock.forName(blockName);
              } catch (IllegalArgumentException err) {
                throw error("Unknown unicode block " + blockName);
              }
              return visitor.visitUnicodeBlock(block);
            } else if (propertyName.startsWith("Is")) {
              final var scriptName = propertyName.substring(2);
              final Character.UnicodeScript script;
              try {
                script = Character.UnicodeScript.forName(scriptName);
              } catch (IllegalArgumentException err) {
                throw error("Unknown unicode script " + scriptName);
              }
              return visitor.visitUnicodeScript(script);
            } else {
              throw error("Unknown property " + propertyName);
            }

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
            throw error("Backreferences are not supported");

          // Invalid boundary
          case 'b':
          case 'B':
          case 'A':
          case 'Z':
          case 'z':
            if (closingClassRange) {
              throw error("Cannot use boundary escapes in character class");
            } else {
              throw new IllegalStateException("This should be unreachable");
            }

          default:
            if (Character.isLetter(c)) {
              throw error("Unknown escape sequence: " + c);
            } else {
              codePoint = input.codePointAt(position);
              position += Character.charCount(codePoint);
              stashedCodePoint = codePoint;
              return null;
            }
        }

      default:
        codePoint = input.codePointAt(position);
        position += Character.charCount(codePoint);
        stashedCodePoint = codePoint;
        return null;
    }
  }

  /**
   * Parse an intersection inside a bracket-delimited class.
   *
   * Called on non-empty input.
   */
  private C parseClassIntersection() throws PatternSyntaxException {
    final int i = new java.util.Random().nextInt(0, 100);
    C intersection = parseClassUnion();

    while (position + 2 < length) {
      if (input.charAt(position) != '&' || input.charAt(position + 1) != '&') {
        break;
      }
      position += 2;
      C u = parseClassUnion();
      intersection = visitor.visitIntersection(intersection, u);
    }

    return intersection;
  }


  /**
   * Parse a union inside a bracket-delimited class.
   *
   * Called on non-empty input.
   */
  private C parseClassUnion() throws PatternSyntaxException {
    C union = parseCharacterRange();

    // Keep parsing ranges until a lower priority construct is encountered
    union_loop: while (position < length) {
      switch (input.charAt(position)) {
        // Intersection
        case '&':
          if (position + 1 < length && input.charAt(position + 1) == '&') {
            break union_loop;
          }
          break;

        // End of class
        case ']':
          break union_loop;
      }
      union = visitor.visitUnion(union, parseCharacterRange());
    }

    return union;
  }

  /**
   * Parse a character range inside a bracket-delimited class.
   *
   * Called on non-empty input.
   */
  private C parseCharacterRange() throws PatternSyntaxException {
    final C cls = parseCharacterClassOrCodePoint(true, false);
    if (cls != null) {
      return cls;
    }

    final int ch = stashedCodePoint;
    if (position + 1 < length && input.charAt(position) == '-') {
      boolean parseRange = true;
      switch (input.charAt(position + 1)) {
        case '[':
          throw error("Invalid character range, missing right hand side");

        // `-` is literal
        case ']':
          parseRange = false;
          break;

        // Check for intersection syntax
        case '&':
          parseRange = position + 2 >= length || input.charAt(position + 2) != '&';
          break;

        default:
          parseRange = true;
          break;
      }

      if (parseRange) {
        position++;
        parseCharacterClassOrCodePoint(true, true);
        return visitor.visitRange(ch, stashedCodePoint);
      }
    }
    return visitor.visitCharacter(ch);
  }

  /**
   * Parse a hexadecimal character from a non-empty input
   */
  private int parseHexadecimalCharacter() throws PatternSyntaxException {
    final int h = Character.digit(input.charAt(position++), 16);
    if (h < 0) {
      throw error("Expected a hexadecimal character");
    } else {
      return h;
    }
  }

  /**
   * Parse a positive (possibly empty) decimal integer from the input
   */
  private int parseDecimalInteger() throws PatternSyntaxException {
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
        throw error("Decimal integer overflowed");
      }
      position++;
    }
    return (int) integer;
  }

  /**
   * Parse (regex) flags from the input.
   */
  private int parseRegexFlags() {
    int flags = 0;

    flag_parsing:
    while (position < length) {
      switch (input.charAt(position)) {
        case 'i':
          flags |= Pattern.CASE_INSENSITIVE;
          break;

        case 'm':
          flags |= Pattern.MULTILINE;
          break;

        case 's':
          flags |= Pattern.DOTALL;
          break;

        case 'd':
          flags |= Pattern.UNIX_LINES;
          break;

        case 'u':
          flags |= Pattern.UNICODE_CASE;
          break;

        case 'c':
          flags |= Pattern.CANON_EQ;
          break;

        case 'x':
          flags |= Pattern.COMMENTS;
          break;

        case 'U':
          flags |= Pattern.UNICODE_CHARACTER_CLASS;
          flags |= Pattern.UNICODE_CASE;
          break;

        default:
          break flag_parsing;
      }
      position++;
    }

    return flags;
  }
}
