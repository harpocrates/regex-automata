package automata;

import java.io.IOException;
import java.io.FileNotFoundException;
import automata.parser.UnsupportedPatternSyntaxException;

/** Processes `.txt` files that encode test cases.
  *
  * This is compatible with test cases in OpenJDK (eg. `test/jdk/java/util/regex/TestCases.txt`)
  */
class FileTesterMain {

  static int successes = 0;
  static int failures = 0;
  static int skipped = 0;

  public static void main(String[] testFiles) throws IOException {

    // Console reporter - writes its output straight to console
    final var consoleReporter = new TestReporter() {
      @Override
      public void onPatternError(TestCase testCase, Exception error) {
        if (error instanceof UnsupportedPatternSyntaxException) {
          FileTesterMain.skipped++;
        } else {
          System.err.println("Unexpected error compiling " + testCase.getSummary() + ": " + error.getMessage());
          FileTesterMain.failures++;
        }
      }

      @Override
      public void onUnexpectedOutput(TestCase testCase, String foundOutput) {
        System.err.println("Unexpected output matching " + testCase.getSummary() + ": expected '" + testCase.output + "' but got '" + foundOutput + "'");
        FileTesterMain.failures++;
      }

      @Override
      public void onSuccess(TestCase testCase, boolean expectedFailure) {
        FileTesterMain.successes++;
      }
    };

    final var runner = new TestRunner(consoleReporter);
    for (String testFile : testFiles) {
      processFileOfTests(runner, testFile);
    }

    System.err.println();
    System.err.println("PASSED: " + successes + ", FAILED: " + failures + ", SKIPPED: " + skipped);
  }

  /**
   * Process all of the tests inside a test file.
   *
   * @param runner test runner
   * @param testFile filepath to the tests
   */
  public static void processFileOfTests(TestRunner runner, String testFile) throws IOException {
    final TestFileReader reader;
    try {
      reader = new TestFileReader(testFile);
    } catch (FileNotFoundException err) {
      System.err.println("Failed to open file " + testFile + ": " + err.getMessage());
      return;
    }

    reader.forEachTestCase(runner);
  }
}
