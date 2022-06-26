package automata.parser;

import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Parser for a subset of regular expressions.
 *
 * This is a fairly standard recursive descent parser. The only more
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
        Character.MAX_CODE_POINT,
        parser.regexFlags
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

  private PatternSyntaxException error(String message, int position) {
    return new PatternSyntaxException(message, input, position);
  }

  private UnsupportedPatternSyntaxException unsupported(String unsupported) {
    return new UnsupportedPatternSyntaxException(unsupported, input, position);
  }

  /**
   * Check if certain regex flags are enabled.
   *
   * @param flags bit mask of the flags to check
   * @return whether the flags are enabled
   */
  private boolean checkFlags(int flags) {
    return (regexFlags & flags) != 0;
  }

  /**
   * Advance the cursor past any whitespace or comments.
   */
  private void skipSpaceAndComments() {
    if (!checkFlags(Pattern.COMMENTS)) return;
    while (position < length) {
      int codePoint = input.codePointAt(position);
      if (codePoint == '#') {
        position++;
        advancePastComment();
      } else if (!Character.isWhitespace(codePoint)) {
        break;
      } else {
        position += Character.charCount(codePoint);
      }
    }
  }

  /**
   * Advance the cursor past a line comment.
   */
  private void advancePastComment() {
    while (position < length) {
      int ch = input.charAt(position);
      position++;

      if (ch == '\n') {
        return;
      }

      // Unix lines mode means _only_ `\n` is a newline
      if (!checkFlags(Pattern.UNIX_LINES) &&
          (ch == '\r' || ch == '\u2028' || ch == '\u2029' || ch == '\u0085')) {
        return;
      }
    }
  }

  /**
   * Peek the next character in the input without advancing the position.
   *
   * @return next character or else -1 if there is none
   */
  int peekChar() {
    skipSpaceAndComments();
    return position < length ? input.charAt(position) : -1;
  }

  /**
   * Skip over the next character.
   */
  void skipChar() {
    skipSpaceAndComments();
    position++;
  }

  /**
   * Get the next character from the input advancing the position.
   *
   * @return next character
   */
  char nextChar() {
    skipSpaceAndComments();
    return input.charAt(position++);
  }

  /**
   * Advance past the next character only if it matches the expected.
   *
   * @param matching desired character
   * @return whether the character was found
   */
  boolean nextCharIf(char matching) {
    skipSpaceAndComments();
    final boolean matches = position < length && input.charAt(position) == matching;
    if (matches) {
      position++;
    }
    return matches;
  }

  /**
   * Parse an alternation.
   */
  private A parseAlternation() throws PatternSyntaxException {
    A unionLhs = parseConcatenation();
    while (nextCharIf('|')) {
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
    int c;
    while ((c = peekChar()) != -1) {
      if ((c == ')' || c == '|') && !checkFlags(Pattern.LITERAL)) break;
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

    int c;
    postfix_parsing:
    while ((c = peekChar()) != -1) {

      // Pass over the quanitifier stem
      switch (c) {
        case '*':
        case '?':
        case '+':
          skipChar();
          break;

        case '{':
          int openBracePosition = position;
          skipChar();
          // Parse `atLeast`
          atLeast = parseDecimalInteger();

          // Parse `atMost`
          if (nextCharIf(',')) {
            if (peekChar() != '}') {
              atMost = OptionalInt.of(parseDecimalInteger());
            }
          } else {
            atMost = OptionalInt.of(atLeast);
          }

          // Close repetition
          if (!nextCharIf('}')) {
            throw error("Expected `}` to close repetition (opened at " + openBracePosition + ")");
          }
          break;

        default:
          break postfix_parsing;
      }

      // `?` suffix indicates the quantifier is lazy (reluctant) instead of being greedy
      boolean isLazy = false;
      if (nextCharIf('?')) {
        isLazy = true;
      } else if (peekChar() == '+') {
        throw unsupported("Possesive quantifiers");
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

    if (checkFlags(Pattern.LITERAL)) {
      return parseLiteralSequence();
    }

    switch (peekChar()) {
      case '(':
        break;

      case '^':
        skipChar();
        return visitor.visitBoundary(Boundary.BEGINNING_OF_LINE);

      case '$':
        skipChar();
        return visitor.visitBoundary(Boundary.END_OF_LINE);

      case '\\':
        // NB: not `nextChar` since `\\ ` or `\\#` are escapes even in comment mode
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
    skipChar();

    // Flags to set back after the group is parsed
    int stashedFlags = this.regexFlags;

    // Detect `?:` non capture groups
    boolean capture = true;

    if (nextCharIf('?')) {

      // We don't error if we reach the end because we'll anyways error on an unclosed group below
      switch (peekChar()) {
        case -1:
          break;

        case '=':
        case '!':
        case '<':
        case '>':
          throw unsupported("Lookaround capture groups");

        default:
          capture = false;

          // Parse out flags
          int flags = parseRegexFlags();
          if (nextCharIf('-')) {
            flags &= ~parseRegexFlags();
          }

          final int afterFlags = peekChar();
          if (afterFlags == ':') {
            skipChar();
            this.regexFlags = flags;
          } else if (afterFlags == ')') {
            this.regexFlags = flags;
            stashedFlags = flags; // Don't reset flags back
          } else if (afterFlags != -1) {
            throw error(
              "Invalid non-capturing group (expected colon or close paren for group opened at " + openParenPosition + ")"
            );
          }
      }
    }

    // If this a capture group, increment the group count
    final var groupIdx = (capture) ? OptionalInt.of(groupCount++) : OptionalInt.empty();

    // Parse the group body and ensure that it is closed
    final A union = parseAlternation();
    if (!nextCharIf(')')) {
      throw error(
        "Unclosed group (expected close paren for group opened at " + openParenPosition + ")"
      );
    }

    // Reset flags for non-capturing groups
    if (!capture) {
      this.regexFlags = stashedFlags;
    }

    return visitor.visitGroup(union, groupIdx);
  }

  /**
   * Parse a literal sequence which can be terminated only with {@code \\E}.
   *
   * This intentionally ignores comments/space even in {@code COMMENTS} mode.
   */
  private A parseLiteralSequence() {
    // Make sure the literal flag is no longer set
    this.regexFlags = this.regexFlags & ~Pattern.LITERAL;

    return parseLiteralString()
      .codePoints()
      .<C>mapToObj(cp -> visitor.visitCharacter(cp, regexFlags))
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
      cls = visitor.visitCharacter(stashedCodePoint, regexFlags);
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
    switch (peekChar()) {
      case '.':
        skipChar();
        if (insideClass) {
          stashedCodePoint = '.';
          return null;
        } else {
          return visitor.visitBuiltinClass(BuiltinClass.DOT, regexFlags);
        }

      // Character class
      case '[':
        if (closingClassRange) {
          throw error("Cannot end class range with `[`");
        }

        // Track the open paren so we can use it in the error message
        final int openBracketPosition = position;
        skipChar();

        // Is the class negated?
        boolean negated = nextCharIf('^');
        if (peekChar() == -1) {
          throw error(
            "Unclosed character class (expected close to bracket opened at " + openBracketPosition + ")"
          );
        }

        // Class body
        final C union = parseClassIntersection();
        if (!nextCharIf(']')) {
          throw error(
            "Unclosed character class (expected close to bracket opened at " + openBracketPosition + ")"
          );
        }
        return negated ? visitor.visitNegated(union) : union;

      case '\\':
        skipChar();

        // NB: not `nextChar` since `\\ ` or `\\#` are escapes even in comment mode
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
            return visitor.visitBuiltinClass(BuiltinClass.CHARACTERS.get(c), regexFlags);

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
              throw error("Expected control character escape but got end of regex");
            }
            final char control = input.charAt(position);
            if (32 <= control && control <= 127) {
              position++;
              stashedCodePoint = control ^ 64;
              return null;
            } else {
              throw error("Expected control escape letter in printable ASCII range");
            }

          // Hexadecimal escape `\xhh` or `\x{h...h}`
          case 'x':
            position++;

            int hexChar = peekChar();
            if (hexChar == -1) {
              throw error("Expected hexadecimal escape but got end of regex");
            } else if (hexChar == '{') {
              skipChar();

              // Loop over hex characters
              codePoint = 0;
              while ((hexChar = peekChar()) != -1) {
                if (hexChar == '}') {
                  skipChar();
                  stashedCodePoint = codePoint;
                  return null;
                }

                codePoint = (codePoint << 4) | parseHexadecimalCharacter();
                if (codePoint < Character.MIN_CODE_POINT || codePoint > Character.MAX_CODE_POINT) {
                  throw error("Hexadecimal escape overflowed max code point");
                }
              }
              throw error("Expected `}` but got end of regex");
            } else {
              codePoint = parseHexadecimalCharacter();
              codePoint = (codePoint << 4) | parseHexadecimalCharacter();
              stashedCodePoint = codePoint;
              return null;
            }

          // Hexadecimal escape `\\uhhhh`
          case 'u':
            position++;
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

            if (!nextCharIf('{')) {
              throw error("Expected `{`");
            }

            // Loop over characters
            final int endName = input.indexOf("}", position);
            if (endName == -1) {
              throw error("`}` but got end of regex");
            }
            stashedCodePoint = Character.codePointOf(input.substring(position, endName));
            position = endName + 1;
            return null;

          // Properties
          case 'p':
          case 'P':
            position++;

            // Name of the property
            final String propertyName;
            final int propertyNamePosition;
            switch (peekChar()) {
              case -1:
                throw error("Expected property name but got end of regex");

              case '{':
                skipChar();
                propertyNamePosition = position;
                final int endProperty = input.indexOf("}", position);
                if (endProperty == -1) {
                  throw error("Expected `}` but got end of regex");
                }
                propertyName = input.substring(position, endProperty);
                position = endProperty + 1;
                break;

              default:
                propertyNamePosition = position;
                propertyName = Character.toString(nextChar());
            }

            return parsePropertyClass(propertyName, propertyNamePosition, c == 'P');

          case 'Q':
            position++;
            final var characters = parseLiteralString();
            final int codePointLength = characters.codePointCount(0, characters.length());

            // Literal escapes
            if (codePointLength == 1) {
              stashedCodePoint = characters.codePointAt(0);
              return null;
            } else if (codePointLength == 0) {
              throw unsupported("Empty literal escapes in character classes");
            } else {
              return characters
                .codePoints()
                .<C>mapToObj(cp -> visitor.visitCharacter(cp, regexFlags))
                .<C>reduce(visitor::visitUnion)
                .get();
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
            throw unsupported("Backreferences");

          // Invalid boundary
          case 'b':
          case 'B':
          case 'A':
          case 'Z':
          case 'z':
            if (insideClass) {
              throw error("Cannot use boundary escapes in character class");
            } else {
              throw new IllegalStateException("This should be unreachable");
            }

          default:
            if (Character.isLetter(c) && c < 128) {
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
   * Parse a {@code \\p} or {@code \\P} property class.
   *
   * @param propertyName name following the property class escape
   * @param propertyNamePosition position of the start of the property name
   * @param negated whether the class is negated
   */
  private C parsePropertyClass(
    String propertyName,
    int propertyNamePosition,
    boolean negated
  ) {
    final int equalsIndex = propertyName.indexOf('=');

    // `\p{key=value}`
    if (equalsIndex != -1) {
      final String key = propertyName.substring(0, equalsIndex);
      final String value = propertyName.substring(equalsIndex + 1);

      switch (key.toLowerCase()) {
        case "blk":
        case "block":
          try {
            final var block = Character.UnicodeBlock.forName(value);
            return visitor.visitUnicodeBlock(block, negated, regexFlags);
          } catch (IllegalArgumentException err) {
            throw error("Unknown unicode block: " + value, propertyNamePosition + key.length() + 1);
          }

        case "sc":
        case "script":
          try {
            final var script = Character.UnicodeScript.forName(value);
            return visitor.visitUnicodeScript(script, negated, regexFlags);
          } catch (IllegalArgumentException err) {
            throw error("Unknown unicode script: " + value, propertyNamePosition + key.length() + 1);
          }

        case "gc":
        case "general_category":
          throw error("Unimplemented general category", propertyNamePosition);

        default:
          throw error("Unknown property class key: " + key, propertyNamePosition);
      }
    }

    // Block
    if (propertyName.startsWith("In")) {
      final var blockName = propertyName.substring(2);
      final Character.UnicodeBlock block;
      try {
        block = Character.UnicodeBlock.forName(blockName);
      } catch (IllegalArgumentException err) {
        throw error("Unknown unicode block: " + blockName, propertyNamePosition);
      }
      return visitor.visitUnicodeBlock(block, negated, regexFlags);
    }

    // Script or property
    if (propertyName.startsWith("Is")) {
      final var scriptName = propertyName.substring(2);

      final PropertyClass propertyClass = PropertyClass.CLASSES.get(scriptName);
      if (propertyClass != null) {
        return visitor.visitPropertyClass(propertyClass, negated, regexFlags);
      }

      final Character.UnicodeScript script;
      try {
        script = Character.UnicodeScript.forName(scriptName);
      } catch (IllegalArgumentException err) {
        throw error("Unknown unicode script: " + scriptName, propertyNamePosition);
      }
      return visitor.visitUnicodeScript(script, negated, regexFlags);
    }

    // Property
    final PropertyClass propertyClass = PropertyClass.CLASSES.get(propertyName);
    if (propertyClass != null) {
      return visitor.visitPropertyClass(propertyClass, negated, regexFlags);
    }
    throw error("Unknown property " + propertyName, propertyNamePosition);
  }

  /**
   * Parse an intersection inside a bracket-delimited class.
   *
   * Called on non-empty input.
   */
  private C parseClassIntersection() throws PatternSyntaxException {
    C intersection = parseClassUnion();

    while (peekChar() == '&') {
      final int stashedPosition = position;
      skipChar();

      if (!nextCharIf('&')) {
        position = stashedPosition;
        break;
      }
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
    int peekedChar;
    union_loop: while ((peekedChar = peekChar()) != -1) {
      switch (peekedChar) {
        // Intersection
        case '&':
          final int stashedPosition = position;
          skipChar();
          final boolean isIntersection = peekChar() == '&';
          position = stashedPosition;

          if (isIntersection) {
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
        return visitor.visitRange(ch, stashedCodePoint, regexFlags);
      }
    }
    return visitor.visitCharacter(ch, regexFlags);
  }

  /**
   * Parse a literal up to a possible {@code \\E}.
   *
   * @return string inside the literal section
   */
  private String parseLiteralString() {
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

    return literal;
  }

  /**
   * Parse a hexadecimal character from the input.
   */
  private int parseHexadecimalCharacter() throws PatternSyntaxException {
    final int h = Character.digit(peekChar(), 16);
    if (h < 0) {
      throw error("Expected a hexadecimal character");
    } else {
      skipChar();
      return h;
    }
  }

  /**
   * Parse a (possibly empty) non-negative decimal integer from the input
   */
  private int parseDecimalInteger() throws PatternSyntaxException {
    long integer = 0;
    int peekedChar;

    while ((peekedChar = peekChar()) != -1) {
      // Try to read another decimal character
      final int d = Character.digit(peekedChar, 10);
      if (d < 0) {
        break;
      }

      // Increment the number
      integer = 10 * integer + d;
      if (integer > Integer.MAX_VALUE) {
        throw error("Decimal integer overflowed");
      }
      skipChar();
    }

    return (int) integer;
  }

  /**
   * Parse (regex) flags from the input.
   */
  private int parseRegexFlags() {
    int flags = 0;

    int peekedChar;
    flag_parsing:
    while ((peekedChar = peekChar()) != -1) {
      switch (peekedChar) {
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
      skipChar();
    }

    return flags;
  }
}
