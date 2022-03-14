package automata;

import java.util.regex.MatchResult;

/**
 * Test case in a test file.
 *
 * @param pattern regular expression pattern
 * @param input input string to feed to the regular expression
 * @param output expected output
 */
public class TestCase {

  /**
   * Regular expression pattern.
   */
  public final String pattern;

  /**
   * Input string to feed to the regular expression.
   */
  public final String input;

  /**
   * Expected output.
   */
  public final String output;

  /**
   * Source file from which the test originated.
   */
  public final String filePath;

  /**
   * Line in the source file from which the test originated.
   */
  public final int lineNumber;

  public TestCase(
    String pattern,
    String input,
    String output,
    String filePath,
    int lineNumber
  ) {
    this.pattern = pattern;
    this.input = input;
    this.output = output;
    this.filePath = filePath;
    this.lineNumber = lineNumber;
  }

  /**
   * Construct an output string from a match result
   *
   * @param foundMatch was the match successful?
   * @param result match information (only used if a match was found)
   * @return output string
   */
  public static String createOutput(boolean foundMatch, MatchResult result) {
    final var outputBuilder = new StringBuilder();
    final int groupCount = result.groupCount();
    outputBuilder.append(foundMatch ? "true " : "false ");

    if (foundMatch) {
      outputBuilder.append(result.group() + " " + groupCount);

      for (int i = 1; i <= groupCount; i++) {
        final var group = result.group(i);
        if (group != null) {
          outputBuilder.append(" " + group);
        }
      }
    } else {
      outputBuilder.append(groupCount);
    }

    return outputBuilder.toString();
  }

  /**
   * Render the test and its source location in a human readable fashion.
   */
  public String getSummary() {
    return "/" + pattern + "/ (at " + filePath + ":" + lineNumber + ")";
  }
}

