package automata.graph;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Transition in a TDFA graph.
 *
 * @param codeUnit allowable code units (absent in final transitions)
 * @param commands tag commands to run as part of the transition
 */
public record TdfaTransition(
  Optional<CodeUnitTransition> codeUnit,
  List<TagCommand> commands
) {

  /**
   * Label for a DOT graph transition.
   */
  public String dotLabel() {
    final var builder = new StringBuilder();

    if (codeUnit.isPresent()) {
      builder.append(codeUnit.get().dotLabel());
    }

    if (codeUnit.isPresent() || !commands.isEmpty()) {
      builder.append("&nbsp;/&nbsp;");
    }

    if (!commands.isEmpty()) {
      builder.append(commands.stream().map(TagCommand::dotLabel).collect(Collectors.joining("; ")));
    }

    return builder.toString();
  }
}
