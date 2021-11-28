package automata

class CompiledRe(
  val pattern: String,
  val re: Option[Re],
  val m1: Option[M1],
  val m2: Option[M2],
  val m3: M3,
  val m4: M4
) extends DfaPattern {

  def checkMatch(input: CharSequence): Boolean =
    m3.checkSimulate(input)

  def captureMatch(input: CharSequence): ArrayMatchResult =
    captureMatchOpt(input).orNull

  /** Match an input string against the compiled regex
    *
    * @param input input test string
    * @return capture group results (if anything matched) or else `null`
    */
  def captureMatchOpt(input: CharSequence): Option[ArrayMatchResult] =
    m3.captureSimulate(input).flatMap(m4.simulate(input.toString, _))
}

object CompiledRe {

  /** Compile a regular expression
    *
    * @param regex source-code of the regex
    * @param keepDebug whether to store all the extra intermediate automata
    */
  def apply(regex: String, keepDebug: Boolean = false): CompiledRe = {
    val re = Re.parse(regex)
    val m1 = M1.fromRe(re)
    val m2 = M2.fromM1(m1)
    val m3 = M3.fromM2(m2)
    val m4 = M4.fromM1M2M3(m1, m2, m3)

    if (keepDebug) new CompiledRe(regex, Some(re), Some(m1), Some(m2), m3, m4)
    else new CompiledRe(regex, None, None, None, m3, m4)
  }
}
