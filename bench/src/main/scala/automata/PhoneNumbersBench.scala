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
class PhoneNumbersBench {

  val phoneRe = raw"(?:\+?(\d\d?\d?))?[-. (]*(\d\d\d)[-. )]*(\d\d\d)[-. ]*(\d\d\d\d)(?: *x(\d+))?"
  val phoneReNoCap = raw"(?:\+?(?:\d{1,3}))?[-. (?:]*(?:\d{3})[-. )]*(?:\d{3})[-. ]*(?:\d{4})(?: *x(?:\d+))?"
  val shouldMatch = List(
    "18005551234" -> List("1", "800", "555", "1234", null),
    "1 800 555 1234" -> List("1", "800", "555", "1234", null),
    "+1 800 555-1234" -> List("1", "800", "555", "1234", null),
    "+86 800 555 1234" -> List("86", "800", "555", "1234", null),
    "1-800-555-1234" -> List("1", "800", "555", "1234", null),
    "1 (800) 555-1234" -> List("1", "800", "555", "1234", null),
    "(800)555-1234" -> List(null, "800", "555", "1234", null),
    "(800) 555-1234" -> List(null, "800", "555", "1234", null),
    "(800)5551234" -> List(null, "800", "555", "1234", null),
    "800-555-1234" -> List(null, "800", "555", "1234", null),
    "800.555.1234" -> List(null, "800", "555", "1234", null),
    "800 555 1234x5678" -> List(null, "800", "555", "1234", "5678"),
    "8005551234 x5678" -> List(null, "800", "555", "1234", "5678"),
    "1    800    555-1234" -> List("1", "800", "555", "1234", null),
    "1----800----555-1234" -> List("1", "800", "555", "1234", null)
  )

  val testStrings: Array[String] = shouldMatch.map(_._1).toArray[String]
  val captureGroups: Array[Array[String]] = shouldMatch.map(_._2.toArray[String]).toArray[Array[String]]

  val compiledJava = java.util.regex.Pattern.compile("^" + phoneRe + "$")
  val compiledJavaNoGroups = java.util.regex.Pattern.compile("^" + phoneReNoCap + "$")
  val compiledAutomata = DfaPattern.compile(phoneRe)

  @Benchmark
  def javaRegexCheck(b: Blackhole): Unit = {
    var i = 0
    while (i < testStrings.length) {
      val m = compiledJavaNoGroups.matcher(testStrings(i))
      b.consume(m.matches())

      i += 1
    }
  }

  @Benchmark
  def automataRegexCheck(b: Blackhole): Unit = {
    var i = 0
    while (i < testStrings.length) {
      val m = compiledAutomata.checkMatch(testStrings(i))
      b.consume(m)

      i += 1
    }
  }

  @Benchmark
  def javaRegexMatch(b: Blackhole): Unit = {
    var i = 0
    while (i < testStrings.length) {
      val m = compiledJava.matcher(testStrings(i))
      m.matches()

      var j = 1
      val matchCount = m.groupCount()
      while (j < matchCount) {
        b.consume(m.group(j))
        j += 1
      }

      i += 1
    }
  }

  @Benchmark
  def automataRegexMatch(b: Blackhole): Unit = {
    var i = 0
    while (i < testStrings.length) {
      val m = compiledAutomata.captureMatch(testStrings(i))

      var j = 0
      val matchCount = m.groupCount()
      while (j < matchCount) {
        b.consume(m.group(j))
        j += 1
      }

      i += 1
    }
  }

}

