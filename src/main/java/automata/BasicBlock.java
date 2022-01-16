package automata;

import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.Collections;

/**
 * Basic block inside a control-flow graph.
 *
 * This gets used for liveness and reachability analysis (which are in turn
 * used for dead store elimination and pruning of states which cannot reach
 * accepting states).
 *
 * @param <V> variables in basic blocks
 */
public class BasicBlock<V> {
  public final Set<V> gen;
  public final Set<V> kill;

  public final Set<BasicBlock<V>> successors;
  public final Set<BasicBlock<V>> predecessors;

  public final Set<V> liveIn;
  public final Set<V> liveOut;


  // TODO
  // public boolean canReachAcceptingBlock;

  // Empty block
  public BasicBlock() {
    this(Collections.emptySet(), Collections.emptySet());
  }

  public BasicBlock(Set<V> gen, Set<V> kill) {
    // These won't change
    this.gen = Collections.unmodifiableSet(gen);
    this.kill = Collections.unmodifiableSet(kill);

    // These will grow
    this.liveIn = new HashSet<V>(gen);
    this.liveOut = new HashSet<V>();

    // These will be set later
    this.successors = new HashSet<BasicBlock<V>>();
    this.predecessors = new HashSet<BasicBlock<V>>();
  }

  /**
   * Mark one block as having an immediate control flow to another block.
   *
   * This updates the {@code successors} of the first block as well as the
   * {@code predecessors} of the second block.
   *
   * @param first first block
   * @param second second block
   */
  public static <V> void link(BasicBlock<V> first, BasicBlock<V> second) {
    first.successors.add(second);
    second.predecessors.add(first);
  }

  /**
   * Perform liveness analysis on a set of blocks.
   *
   * This solves the backwards data-flow equations updating {@code liveIn} and
   * {@code liveOut} on all reachable blocks.
   *
   * @param blocks all of the basic blocks to initially scan
   */
  public static <V> void livenessAnalysis(Collection<BasicBlock<V>> blocks) {

    final var toVisit = new HashSet<BasicBlock<V>>(blocks);

    while (!toVisit.isEmpty()) {
      final var block = toVisit.iterator().next();
      toVisit.remove(block);

      // Update `liveOut`
      for (final var successor : block.successors) {
        block.liveOut.addAll(successor.liveIn);
      }

      // Update `liveIn`
      boolean liveInChanged = false;
      for (final var variable : block.liveOut) {
        if (!block.kill.contains(variable)) {
          boolean added = block.liveIn.add(variable);
          liveInChanged = liveInChanged || added;
        }
      }

      // Update the worklist
      if (liveInChanged) {
        toVisit.addAll(block.predecessors);
      }
    }
  }
}
