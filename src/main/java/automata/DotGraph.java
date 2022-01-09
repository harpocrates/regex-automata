package automata;

import java.util.stream.Stream;

public interface DotGraph<V, E> {

  /**
   * Vertex in the dot graph.
   *
   * @param id unique identifier for the vertex
   * @param accepting is this an accepting state?
   */
  record Vertex<V>(V id, boolean accepting) { }

  /**
   * Edge in the dot graph.
   *
   * @param from vertex where the edges starts (or no vertex if `null`)
   * @param to vertex where the edges ends (or no vertex if `null`)
   * @param label label on the vertex
   */
  record Edge<V, E>(V from, V to, E label) { }

  /**
   * List out the vertices in the graph.
   *
   * @return all vertices
   */
  public Stream<Vertex<V>> vertices();

  /**
   * List out the edges in the graph.
   *
   * @return all edges
   */
  public Stream<Edge<V, E>> edges();

  /**
   * Render an edge label.
   *
   * @param edge edge associated with the label
   * @return HTML label string
   */
  default String renderEdgeLabel(Edge<V, E> edge) {
    final E label = edge.label();
    return label == null ? "" : label.toString();
  }

  /**
   * Render a vertex label.
   *
   * @param vertex vertex associated with the label
   * @return HTML label string
   */
  default String renderVertexLabel(Vertex<V> vertex) {
    return vertex.id().toString();
  }

  /**
   * Render a full Dot graph.
   *
   * @param name title given to the graph
   * @return source code for the graph
   */
  default String dotGraph(String name) {
    final var builder = new StringBuilder();
    builder.append("digraph " + escapeId(name) + " {\n");
    builder.append("  rankdir = LR;\n");

    // Vertices
    final Iterable<Vertex<V>> vs = () -> vertices().iterator();
    for (Vertex<V> vertex : vs) {
      final var id = escapeId(vertex.id().toString());
      final var shape = vertex.accepting() ? "doublecircle" : "circle";
      final var label = renderVertexLabel(vertex);
      builder.append("  " + id + "[shape = " + shape + ", label = <" + label + ">];\n");
    }

    // Edges
    int generated = 0;
    final Iterable<Edge<V, E>> es = () -> edges().iterator();
    for (Edge<V, E> edge : es) {
      final var from = escapeId(edge.from() == null ? ("_gen" + ++generated) : edge.from().toString());
      final var to = escapeId(edge.to() == null ? ("_gen" + ++generated) : edge.to().toString());
      final var label = renderEdgeLabel(edge);
      builder.append("  " + from + " -> " + to + " [label = <" + label + ">];\n");
    }

    // Generated (and blank) vertices
    while (generated > 0) {
      final var genId = escapeId("_gen" + generated--);
      builder.append("  " + genId + " [shape = none, label = <>];\n");
    }

    builder.append("}");
    return builder.toString();
  }

  /**
   * Turn a string into a Dot ID.
   *
   * As per the docs, an ID can be "any double-quoted string ("...") possibly
   * containing escaped quotes (\")".
   *
   * @param str string to escape into an ID
   */
  private static String escapeId(String str) {
    return "\"" + str.replace("\"", "\\\"") + "\"";
  }
}
