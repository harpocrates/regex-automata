package automata.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.Optional;

class TrieSet<A> {

  public boolean inSet = false;

  public final Map<A, TrieSet<A>> children = new HashMap<A, TrieSet<A>>();

  boolean hasChildren() {
    return !children.isEmpty();
  }

  /**
   * Register a path in the trie.
   *
   * @param path path which should be created in the trie starting from the root
   */
  public void add(Iterable<A> path) {
    final var iterator = path.iterator();
    var nextSet = this;
    while (iterator.hasNext()) {
      nextSet = nextSet.children.computeIfAbsent(iterator.next(), k -> new TrieSet<A>());
    }
    nextSet.inSet = true;
  }

  /**
   * Check if a path is in the trie.
   *
   * @param path path to check
   * @return whether the path is in the trie
   */
  public boolean contains(Iterable<A> path) {
    final var iterator = path.iterator();
    var nextSet = this;
    while (iterator.hasNext()) {
      nextSet = nextSet.children.get(iterator.next());
      if (nextSet == null) {
        return false;
      }
    }
    return nextSet.inSet;
  }

  /**
   * If every entry in the set is at the same depth, return that depth.
   */
  public Optional<Integer> inSetDepth() {
    int depth = 0;

    var thisLevel = new Stack<TrieSet<A>>();
    var nextLevel = new Stack<TrieSet<A>>();
    thisLevel.push(this);
    boolean encounteredInSet = false;

    while (!encounteredInSet && !thisLevel.isEmpty()) {

      // Visit the next level
      while (!thisLevel.isEmpty()) {
        var entry = thisLevel.pop();
        encounteredInSet = encounteredInSet || entry.inSet;
        nextLevel.addAll(entry.children.values());
      }

      // Swap the stacks
      var temp = thisLevel;
      thisLevel = nextLevel;
      nextLevel = temp;

      depth++;
    }

    return (encounteredInSet && thisLevel.isEmpty())
      ? Optional.of(depth - 1)
      : Optional.empty();
  }
}
