package automata;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

public class InterferenceGraph<V> {

  /**
   * Mapping of canonical variables to canonical variables with which they
   * interfere.
   *
   * This mapping should be two way - if the key {@code v1} has a set containing
   * {@code v2}, then the set under the key {@code v2} should exist and contain
   * {@code v1}.
   */
  public Map<V, Set<V>> interferences;

  /**
   * Mapping of variables to variables into which they were merged.
   *
   * As variables get merged, one variable is kept in {@code interferences} and
   * the other is a key in this map. The value is the variable into which it
   * was merged. Since multiple merges may get chained, the value may sometimes
   * end up also being in {@code merged} instead of {@code interferences}.
   */
  public Map<V, V> merged;

  public InterferenceGraph() {
    this.interferences = new HashMap<>();
    this.merged = new HashMap<>();
  }

  public Map<V, V> canonicalMerged() {
    final var toReturn = new HashMap<V, V>();
    for (var e : merged.keySet()) {
      toReturn.put(e, canonical(e));
    }
    return toReturn;
  }

  /**
   * Get the canonical version of a variable.
   *
   * As an optimization, this also updates the {@code merged} map along the way
   */
  public V canonical(V variable) {
    V canonical = merged.getOrDefault(variable, variable);

    // Optimization: update the chain to point straight to the last canonical
    if (merged.containsKey(canonical)) {

      // Chase the chain of merged variables all the way down
      while (merged.containsKey(canonical)) {
        canonical = merged.get(canonical);
      }

      // Second pass through the chain, this time to update the mapping
      var next = merged.get(variable);
      while (next != null) {
        merged.put(variable, canonical);
        variable = next;
        next = merged.get(next);
      }
    }

    return canonical;
  }

  /**
   * Register mutual interference between several variables.
   *
   * @param betweenVariables variables that interfere with each other pairwise
   */
  public void addInterference(Collection<V> betweenVariables) {
    for (var v1 : betweenVariables) {
      v1 = canonical(v1);
      final var interfere = interferences.computeIfAbsent(v1, k -> new HashSet<>());

      for (var v2 : betweenVariables) {
        v2 = canonical(v2);
        interfere.add(v2);
      }

      interfere.remove(v1);
    }
  }

  /**
   * Try to coalesce two variables.
   *
   * If the variables are interfering, they cannot be coalesced.
   *
   * @param variable1 first variable
   * @param variable2 second variable (this is the "canonical" one)
   * @return whether the variables could be coalesced
   */
  public boolean coalesce(V variable1, V variable2) {
    final V v1 = canonical(variable1);
    final V v2 = canonical(variable2);

    // Already the same variable, so this is a no-op
    if (v1.equals(v2)) {
      return true;
    }

    // Variables interfere
    final var inters1 = interferences.getOrDefault(v1, Collections.emptySet());
    if (inters1.contains(v2)) {
     return false;
    }

    // Coalesce!
    final var inters2 = interferences.computeIfAbsent(v2, k -> new HashSet<>());
    inters2.addAll(inters1);
    for (final var v : inters1) {
      final var intersOther = interferences.get(v);
      intersOther.remove(v1);
      intersOther.add(v2);
    }
    merged.put(v1, v2);

    return true;
  }

}
