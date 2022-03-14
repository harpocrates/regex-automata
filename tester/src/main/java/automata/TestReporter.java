package automata;

interface TestReporter {

  /**
   * Handler for when a pattern (unexpectedly) fails to compile.
   *
   * @param testCase test which failed
   * @param error exception that was thrown
   */
  public void onPatternError(TestCase testCase, Exception error);

  /**
   * Handler for when the pattern output does not match the expected output.
   *
   * @param testCase test which failed
   * @param foundOutput output which was found
   */
  public void onUnexpectedOutput(TestCase testCase, String foundOutput);

  /**
   * Handler for a test passing.
   *
   * @param testCase test which passed
   * @param expectedFailure the successful behaviour was an error
   */
  public void onSuccess(TestCase testCase, boolean expectedFailure);
}
