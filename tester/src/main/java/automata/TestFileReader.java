package automata;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.MatchResult;

/**
 * Skips over comment lines, processes escape sequences.
 */
public class TestFileReader {

  private static Pattern UNICODE_ESCAPES = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

  private final BufferedReader reader;

  private final String filePath;

  private int lineNumber = 0;

  public TestFileReader(String filePath) throws FileNotFoundException {
    this.reader = new BufferedReader(new FileReader(filePath));
    this.filePath = filePath;
  }

  /**
   * Read the next processed line from the input.
   */
  public String readLine() throws IOException {
    String line;

    while (true) {
      line = reader.readLine();
      lineNumber++;
      if (line == null) {
        return line; // EOF
      } else if (line.startsWith("//") || line.isEmpty()) {
        continue; // Not a valid line
      }

      line = processLineEscapes(line);
      break;
    }

    return line;
  }

  /**
   * Read the next test case from the input.
   */
  public TestCase readTestCase() throws IOException {

    // Test data
    final String pattern = readLine();
    if (pattern == null) {
      return null;
    }
    final int lineNumber = this.lineNumber;
    final String input = readLine();
    final String outputData = readLine();

    return new TestCase(pattern, input, outputData, filePath, lineNumber);
  }

  /**
   * Run an action for every remaining test case in the file.
   *
   * @param action action to run on each test case
   */
  public void forEachTestCase(Consumer<? super TestCase> action) throws IOException {
    TestCase testCase;
    while ((testCase = readTestCase()) != null) {
      action.accept(testCase);
    }
  }

  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * Process a line to replace some escape sequences with the actual characters
   *
   * @param line line to escape
   * @return escaped line
   */
  private static String processLineEscapes(String line) {

    // process newline escapes
    line = line.replaceAll("\\\\n", "\n");

    // process unicode escapes
    line = UNICODE_ESCAPES.matcher(line).replaceAll(result ->
      Character.toString((char) Integer.parseInt(result.group(1), 16))
    );

    return line;
  }
}
