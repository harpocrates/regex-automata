package automata;

import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * Immutable set of integers.
 */
public final class IntSet {

  // Sorted and distinct elements
  private final int[] elements;

  public static IntSet of(int... elems) {
    return new IntSet(Arrays.stream(elems).boxed().collect(Collectors.toList()));
  }

  public IntSet(Collection<Integer> elems) {
    this.elements = new TreeSet<Integer>(elems).stream().mapToInt((Integer i) -> i.intValue()).toArray();
  }

  public IntStream stream() {
    return Arrays.stream(elements);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(elements);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (!(obj instanceof IntSet)) {
      return false;
    } else {
      return Arrays.equals(elements, ((IntSet) obj).elements);
    }
  }

  @Override
  public String toString() {
    return Arrays
      .stream(elements)
      .mapToObj(Integer::toString)
      .collect(Collectors.joining(",", "{", "}"));
  }
}
