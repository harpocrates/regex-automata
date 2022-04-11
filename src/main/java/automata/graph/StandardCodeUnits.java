package automata.graph;

import automata.util.IntRange;
import automata.util.IntRangeSet;
import automata.parser.CodePoints;
import automata.graph.TrieSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum StandardCodeUnits {

  /**
   * Codepoints are turned into one or two 16-bit code units.
   *
   * Java strings internally use this.
   */
  UTF_16(IntRange.between(Character.MIN_VALUE, Character.MAX_VALUE)) {

    /**
     * Break down the input code point set into a mapping of low ranges to high
     * ranges.
     *
     * The values in the output should be disjoint and union out to a subset of
     * the high surrogate range. The values in the keys won't necessarily be
     * disjoint, but they should all be in the low surrogate range. Since the
     * high surrogate range is just {@code 0xD800–0xDBFF} (1024 values), the
     * total size of the output map is at most 1024 entries.
     *
     * @param codePointSet input code point set
     * @return mapping from low surrogate ranges to high surrogate ranges
     */
    private static Map<IntRangeSet, IntRangeSet> supplementaryCodeUnitRanges(
      IntRangeSet codePointSet
    ) {

      // TODO: this doesn't need to be a map since we scan high codepoints in order
      //       and only ever update the last one
      final var supplementaryCodeUnits = new HashMap<Integer, LinkedList<IntRange>>();

      for (IntRange range : codePointSet.difference(IntRangeSet.of(CodePoints.BMP_RANGE)).ranges()) {

        int rangeStartHi = Character.highSurrogate(range.lowerBound());
        int rangeStartLo = Character.lowSurrogate(range.lowerBound());

        int rangeEndHi = Character.highSurrogate(range.upperBound());
        int rangeEndLo = Character.lowSurrogate(range.upperBound());

        if (rangeStartHi == rangeEndHi) {
          // Add the _only_ range
          supplementaryCodeUnits
            .computeIfAbsent(rangeStartHi, k -> new LinkedList<>())
            .addLast(IntRange.between(rangeStartLo, rangeEndLo));
        } else {
          // Add the first range
          supplementaryCodeUnits
            .computeIfAbsent(rangeStartHi, k -> new LinkedList<>())
            .addLast(IntRange.between(rangeStartLo, Character.MAX_LOW_SURROGATE));

          // Add the last range
          supplementaryCodeUnits
            .computeIfAbsent(rangeEndHi, k -> new LinkedList<>())
            .addLast(IntRange.between(Character.MIN_LOW_SURROGATE, rangeEndLo));

          // Add everything in between
          for (int hi = rangeStartHi + 1; hi <= rangeEndHi - 1; hi++) {
            supplementaryCodeUnits
              .computeIfAbsent(hi, k -> new LinkedList<>())
              .addLast(CodePoints.LOW_SURROGATE_RANGE);
          }
        }
      }

      return supplementaryCodeUnits
        .entrySet()
        .stream()
        .collect(
          Collectors.groupingBy(
            e -> new IntRangeSet(e.getValue()),
            Collectors.mapping(
              e -> IntRangeSet.of(IntRange.single(e.getKey())),
              Collectors.collectingAndThen(Collectors.toList(), IntRangeSet::union)
            )
          )
        );
    }

    @Override
    public TrieSet<IntRangeSet> codeUnitRangeSuffixTrie(IntRangeSet codePointSet) {

      /* Code unit transitions corresponding to the basic multilingual plane.
       * By definition of the BMP, this means these are exactly one code unit.
       */
      final var basicCodeUnits = codePointSet.intersection(IntRangeSet.of(CodePoints.BMP_RANGE));

      /* Mapping from the first (high) 16-bit code unit to the range of second
       * (low) 16-bit code units. There are `0xDBFF - 0xD800 + 1 = 1024` high
       * code points, so this map will have between 0 and 1024 entries.
       */
      final var supplementaryCodeUnits = supplementaryCodeUnitRanges(codePointSet);

      final var output = new TrieSet<IntRangeSet>();
      output.add(List.of(basicCodeUnits));
      for (final var entry : supplementaryCodeUnits.entrySet()) {
        output.add(List.of(entry.getKey(), entry.getValue()));
      }
      return output;
    }
  },

  /**
   * Codepoints are turned into one to four 8-bit code units.
   */
  UTF_8(IntRange.between(0, 0xFF)) {

    // Ranges of code points taking 1, 2, 3, and 4 code unit
    private static final IntRange ONE_BYTE_RANGE = IntRange.between(0, 0x7F);
    private static final IntRange TWO_BYTE_RANGE = IntRange.between(0x80, 0x7FF);
    private static final IntRange THREE_BYTE_RANGE = IntRange.between(0x800, 0xFFFF);
    private static final IntRange FOUR_BYTE_RANGE = IntRange.between(0x10000, 0x10FFFF);

    @Override
    public TrieSet<IntRangeSet> codeUnitRangeSuffixTrie(IntRangeSet codePointSet) {
      throw new AssertionError("unimplemented");
    }
  },

  /**
   * Codepoints each map to exactly one 32-bit code unit.
   */
  UTF_32(IntRange.between(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)) {
    @Override
    public TrieSet<IntRangeSet> codeUnitRangeSuffixTrie(IntRangeSet codePointSet) {
      final var output = new TrieSet<IntRangeSet>();
      output.add(List.of(codePointSet));
      return output;
    }
  };

  /**
   * Full range of possible code units.
   */
  public final IntRange codeUnitRange;

  private StandardCodeUnits(IntRange codeUnitRange) {
    this.codeUnitRange = codeUnitRange;
  }

  /**
   * Converts a set of code points into a trie containing reversed code units.
   *
   * <p>The output trie must obey a handful of invariants:
   *
   * <ul>
   *   <li>
   *     The children of any node in the trie should have disjoint subsets of
   *     the valid code unit range.
   *
   *     As a consequence, any code point {@code c} with code unit
   *     representation {@code u0, u1, ... un} can traced to a node in the trie
   *     by walking the suffix tree from the root using the reversed code units
   *     {@code un, ... u1, u0}. A code point is in the trie set if this process
   *     ends at a node which has {@code inSet = true}.
   *
   *   <li>
   *     A code point is in the input set if and only if it is in the output set
   *     (using the definition of "in" from the previous invariant).
   * </ul>
   *
   * <p>This is the information needed to efficiently create a small DFA for the
   * range of code points.
   *
   * @param codePointRange range of code points to include in the trie
   * @return trie ranges of reversed code units
   */
  public abstract TrieSet<IntRangeSet> codeUnitRangeSuffixTrie(IntRangeSet codePointSet);

}
