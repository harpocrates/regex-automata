package automata;

import java.util.function.Consumer;

/**
 * Charged with running test cases.
 */
public class TestRunner implements Consumer<TestCase> {

  /**
   * How are test outcomes reported?
   */
  final TestReporter reporter;

  public TestRunner(TestReporter reporter) {
    this.reporter = reporter;
  }

  /**
   * Accept a new test case.
   *
   * @param testCase test to run
   */
  public void accept(TestCase testCase) {

    // Compile the pattern
    final DfaMatcher matcher;
    try {
      matcher = DfaPattern.compile(testCase.pattern).matcher(testCase.input);
    } catch (Exception error) {
      if (testCase.output.startsWith("error")) {
        reporter.onSuccess(testCase, true);
      } else {
        reporter.onPatternError(testCase, error);
      }
      return;
    }

    // Try to match
    final boolean found = matcher.find();

    // Compare the outputs
    final String foundOutput = TestCase.createOutput(found, matcher);
    if (testCase.output.equals(foundOutput)) {
      reporter.onSuccess(testCase, false);
    } else {
      reporter.onUnexpectedOutput(testCase, foundOutput);
    }
  }
}
