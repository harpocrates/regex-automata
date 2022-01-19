package automata.graph;

import java.util.HashMap;
import java.util.Optional;

public interface TagCommand {

  public String dotLabel();

  /**
   * Variable used by the command.
   *
   * This is used in liveness analysis (referred to as "gen" set there).
   */
  public Optional<Register> usedVariable();

  public void interpret(
    HashMap<Register, Integer> context,
    int currentPos,
    boolean printDebugInfo
  );

  /**
   * Variable overwritten by the command.
   *
   * This is used in liveness analysis (referred to as "kill" set there).
   */
  public Optional<Register> definedVariable();

  record Copy(Register assignTo, Register copyFrom) implements TagCommand {

    @Override
    public String dotLabel() {
      return String.format("%s←%s", assignTo.dotLabel(), copyFrom.dotLabel());
    }

    @Override
    public Optional<Register> usedVariable() {
      return Optional.of(copyFrom);
    }

    @Override
    public Optional<Register> definedVariable() {
      return Optional.of(assignTo);
    }

    @Override
    public void interpret(HashMap<Register, Integer> context, int currentPos, boolean printDebugInfo) {
      final int value = context.getOrDefault(copyFrom, -1);
      if (printDebugInfo) {
        System.err.println("[TagCommand] " + assignTo + " <- " + copyFrom + " (currently " + value + ")");
      }
      context.put(assignTo, value);
    }
  }

  record CurrentPosition(Register assignTo) implements TagCommand {

    @Override
    public String dotLabel() {
      return String.format("%s←p", assignTo.dotLabel());
    }

    @Override
    public Optional<Register> usedVariable() {
      return Optional.empty();
    }

    @Override
    public Optional<Register> definedVariable() {
      return Optional.of(assignTo);
    }

    @Override
    public void interpret(HashMap<Register, Integer> context, int currentPos, boolean printDebugInfo) {
      if (printDebugInfo) {
        System.err.println("[TagCommand] " + assignTo + " <- pos (currently " + currentPos + ")");
      }
      context.put(assignTo, currentPos);
    }
  }
}
