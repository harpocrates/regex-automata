package automata;

import java.util.stream.Collectors;

interface Register {

  public String dotLabel();

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
  }

}
