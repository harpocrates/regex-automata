package automata;

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(10)
class BadBacktracking {

  val re = "(x+x+)+y"

  // Pathological cases for backtracking engines
  val shouldntMatch = Array.tabulate(100)("x".repeat(_))

  @Param(Array("4", "8", "12", "16", "20", "24"))
  var n: Int = _

  val compiledJava = java.util.regex.Pattern.compile("^" + re + "$")
  val compiledAutomata = DfaPattern.compile(re)

  @Benchmark
  def javaRegex(b: Blackhole): Unit = {
    var i = 0
    while (i < n) {
      val m = compiledJava.matcher(shouldntMatch(i))
      b.consume(m.matches())
      i += 1
    }
  }

  @Benchmark
  def automataRegex(b: Blackhole): Unit = {
    var i = 0
    while (i < n) {
      val m = compiledAutomata.captureMatch(shouldntMatch(i))
      b.consume(m)
      i += 1
    }
  }
}
