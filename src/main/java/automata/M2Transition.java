package automata;

import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public record M2Transition(
  CodeUnitTransition codeUnit,
  PathMarkers path
) {

 /**
   * Label for a DOT graph transition.
   */
  public String dotLabel() {
    var parts = new LinkedList<String>();
    if (codeUnit != null) {
      parts.add(codeUnit.dotLabel());
    }

    if (!path.isEmpty()) {
      final String label = StreamSupport
        .stream(path.spliterator(), false)
        .map(PathMarker::dotLabel)
        .collect(Collectors.joining());
      parts.add(label);
    }

    return String.join("&nbsp;/&nbsp;", parts);
  }
}
