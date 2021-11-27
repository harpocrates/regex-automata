package automata

class CompiledRe(
  val regexString: String,
  val re: Re,
  val m1: M1,
  val m2: M2,
  val m3: M3,
  val m4: M4
) {

  def this(m3: M3, m4: M4) =
    this(null, null, null, null, m3, m4)

  /** Match an input string against the compiled regex
    *
    * @param input input test string
    * @return capture group results (if anything matched)
    */
  def matchComplete(input: String): Option[ArrayMatchResult] =
    m3.simulate(input).flatMap(m4.simulate(input, _))
}

object CompiledRe {

  /** Compile a regular expression
    *
    * @param regexString source-code of the regex
    * @param keepDebug whether to store all the extra intermediate automata
    */
  def apply(regexString: String, keepDebug: Boolean = false): CompiledRe = {
    val re = Re.parse(regexString)
    val m1 = M1.fromRe(re)
    val m2 = M2.fromM1(m1)
    val m3 = M3.fromM2(m2)
    val m4 = M4.fromM1M2M3(m1, m2, m3)

    if (keepDebug) new CompiledRe(regexString, re, m1, m2, m3, m4) else new CompiledRe(m3, m4)
  }
}
