package automata.graph;

import java.util.stream.Collectors;

/**
 * Abstract memory location that can be read/written and contains an integer.
 *
 * These can be thought of as variables.
 *
 * @author Alec Theriault
 */
public interface Register {

  public String dotLabel();

  /**
   * Returns a compact string representation of the register.
   *
   * @return compact string representation
   */
  public String compactString();

  /**
   * Temporary register with no meaningful name.
   *
   * @param register unique index
   */
  public record Temporary(int register) implements Register {

    @Override
    public String dotLabel() {
      // Note: uses unicode subscripts because they render more nicely
      return String
        .format("<i>r%d</i>", register)
        .chars()
        .mapToObj((int c) -> Character.toString((Character.isDigit(c) ? c - '0' + '₀' : c)))
        .collect(Collectors.joining(""));
    }

    @Override
    public String compactString() {
      return "t" + register;
    }
  }
}
