package automata;

import java.util.List;
import java.util.LinkedList;
import java.util.stream.Collectors;

public record M4Transition(
  IntSet m3State,
  List<GroupMarker> groups
) {

 /**
   * Label for a DOT graph transition.
   */
  public String dotLabel() {
    var parts = new LinkedList<String>();
    if (m3State != null) {
      parts.add(m3State.toString());
    }

    if (!groups.isEmpty()) {
      final String label = groups
        .stream()
        .map(PathMarker::dotLabel)
        .collect(Collectors.joining());
      parts.add(label);
    }

    return String.join("&nbsp;/&nbsp;", parts);
  }
}
