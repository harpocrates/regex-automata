# `regex-automata`

Compiles regular expressions into DFAs and generate bytecode on the fly (using
[JEP 371][1]) for encoding those DFAs inside control flow. Supported features
include:

  * capture groups
  * alternation
  * quantifiers (greedy and lazy, but not possesive)
  * some character classes

## Caveats

This approach has a lot of caveats. The main ones are:

 - the powerset construction for NFA to DFA is exponential (this turns out to
   usually not be the case for most real world regex).

 - encoding DFAs using control flow means the max method body size of 64k
   instructions will likely be hit (we can work around this a bit by pulling
   subgraphs of the DFA out into other methods).

## Building

You'll need JDK 16 or newer (this relies on [JEP 371][1] and uses newer Java
features) installed as well as [SBT][0]. Then,

    sbt compile     # compile the project
    sbt test        # compile and run the tests

## References

These are great papers that I've been reading along with this:

 * <https://www.labs.hpe.com/techreports/2012/HPL-2012-41R1.pdf>
 * <https://re2c.org/2017_trofimovich_tagged_deterministic_finite_automata_with_lookahead.pdf>

[0]: https://www.scala-sbt.org
[1]: https://openjdk.java.net/jeps/371
