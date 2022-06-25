# `regex-automata`

Compiles regular expressions into DFAs and generate bytecode on the fly (using
[JEP 371][1]) for encoding those DFAs inside control flow. The goal is to
support as much of the JDK `Pattern` API as possible (notable exceptions being
possesive quantifiers and backreferences, since those can't be encoded).
Supported features include:

  * capture groups
  * alternation
  * quantifiers (greedy and lazy, but not possesive)
  * character classes

## Caveats

This approach has several caveats. The main ones are:

 - the powerset construction for NFA to DFA is exponential (this turns out to
   usually not be the case for most real world regex).

 - encoding DFAs using control flow means the max method body size of 64k
   instructions will likely be hit (we can work around this a bit by pulling
   subgraphs of the DFA out into other methods).

## Building

You'll need JDK 16 or newer (this relies on [JEP 371][1] and uses newer Java
features) installed as well as [SBT][0]. Then,

    sbt compile        # compile the project
    sbt test           # compile and run the tests
    sbt bench/Jmh/run  # compile and run all JMH benchmarks

There's also a test driver that consumes `.txt` test cases like those used in
the OpenJDK tests for `java.util.regex` (in `test/jdk/java/util/regex/*.txt`):

    sbt 'tester/run TestCases.txt'

## References

This would not have been possible without the following papers and blogs:

 * [This paper][4] (Tagged Deterministic Finite Automata with Lookahead)
   describes how capture groups can be extracted in a single pass tagged DFA

 * [This paper][2] (Efficient Submatch Extraction for Practical Regular
   Expressions) describes how capture groups can be extracted even using
   simple DFAs

 * [This blog series][3] about the design space and tradeoffs in regular
   expression search

[0]: https://www.scala-sbt.org
[1]: https://openjdk.java.net/jeps/371
[2]: https://www.labs.hpe.com/techreports/2012/HPL-2012-41R1.pdf
[3]: https://swtch.com/~rsc/regexp/
[4]: https://re2c.org/2017_trofimovich_tagged_deterministic_finite_automata_with_lookahead.pdf
