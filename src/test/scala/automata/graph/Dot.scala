package automata.graph

import org.scalatest.funspec.AnyFunSpec

// When updating the "golden" outputs, make sure `dot` still accepts them
class Dot extends AnyFunSpec {

  val re = "((a)*|b)(ab|b)"

  describe(s"dot-graph for $re") {
    val tnfa = Tnfa.parse(re, StandardCodeUnits.UTF_16, 0, true, false)
    val tdfa = Tdfa.fromTnfa(tnfa, MatchMode.FULL, true)

    it("tnfa") {
      assert(
        tnfa.dotGraph("tnfa") ===
        """digraph "tnfa" {
          |  rankdir = LR;
          |  "0"[shape = doublecircle, label = <0>];
          |  "1"[shape = circle, label = <1>];
          |  "2"[shape = circle, label = <2>];
          |  "3"[shape = circle, label = <3>];
          |  "4"[shape = circle, label = <4>];
          |  "5"[shape = circle, label = <5>];
          |  "6"[shape = circle, label = <6>];
          |  "7"[shape = circle, label = <7>];
          |  "8"[shape = circle, label = <8>];
          |  "9"[shape = circle, label = <9>];
          |  "10"[shape = circle, label = <10>];
          |  "11"[shape = circle, label = <11>];
          |  "12"[shape = circle, label = <12>];
          |  "13"[shape = circle, label = <13>];
          |  "14"[shape = circle, label = <14>];
          |  "15"[shape = circle, label = <15>];
          |  "16"[shape = circle, label = <16>];
          |  "17"[shape = circle, label = <17>];
          |  "_gen1" -> "2" [label = <>];
          |  "1" -> "0" [label = <<i>E₀</i>>];
          |  "2" -> "10" [label = <<i>S₀</i>>];
          |  "3" -> "1" [label = <<i>E₃</i>>];
          |  "4" -> "8" [label = <<i>S₃</i>>];
          |  "5" -> "3" [label = <<font face="courier">b</font>>];
          |  "6" -> "5" [label = <<font face="courier">a</font>>];
          |  "7" -> "3" [label = <<font face="courier">b</font>>];
          |  "8" -> "7" [label = <&minus;>];
          |  "8" -> "6" [label = <+>];
          |  "9" -> "4" [label = <<i>E₁</i>>];
          |  "10" -> "17" [label = <<i>S₁</i>>];
          |  "11" -> "9" [label = <&minus;>];
          |  "11" -> "14" [label = <+>];
          |  "12" -> "9" [label = <&minus;>];
          |  "12" -> "14" [label = <+>];
          |  "13" -> "11" [label = <<i>E₂</i>>];
          |  "14" -> "15" [label = <<i>S₂</i>>];
          |  "15" -> "13" [label = <<font face="courier">a</font>>];
          |  "16" -> "9" [label = <<font face="courier">b</font>>];
          |  "17" -> "16" [label = <&minus;>];
          |  "17" -> "12" [label = <+>];
          |  "_gen1" [shape = none, label = <>];
          |}""".stripMargin
      )
    }

    it("tdfa") {
      assert(
        tdfa.dotGraph("tdfa") ===
        """digraph "tdfa" {
          |  rankdir = LR;
          |  "0"[shape = circle, label = <0>];
          |  "1"[shape = circle, label = <1>];
          |  "2"[shape = doublecircle, label = <2>];
          |  "4"[shape = doublecircle, label = <4>];
          |  "5"[shape = circle, label = <5>];
          |  "_gen1" -> "0" [label = <>];
          |  "2" -> "_gen2" [label = <>];
          |  "4" -> "_gen3" [label = <>];
          |  "0" -> "2" [label = <<font face="courier">b</font>&nbsp;/&nbsp;<i>S₃</i>←p>];
          |  "0" -> "1" [label = <<font face="courier">a</font>&nbsp;/&nbsp;>];
          |  "1" -> "4" [label = <<font face="courier">b</font>&nbsp;/&nbsp;<i>E₂</i>←p; <i>S₃</i>←p>];
          |  "1" -> "1" [label = <<font face="courier">a</font>&nbsp;/&nbsp;>];
          |  "2" -> "4" [label = <<font face="courier">b</font>&nbsp;/&nbsp;<i>S₃</i>←p>];
          |  "2" -> "5" [label = <<font face="courier">a</font>&nbsp;/&nbsp;<i>S₃</i>←p>];
          |  "5" -> "4" [label = <<font face="courier">b</font>&nbsp;/&nbsp;>];
          |  "_gen3" [shape = none, label = <>];
          |  "_gen2" [shape = none, label = <>];
          |  "_gen1" [shape = none, label = <>];
          |}""".stripMargin
      )
    }
  }
}
