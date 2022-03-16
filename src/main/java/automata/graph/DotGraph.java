package automata.graph;

import java.util.stream.Stream;

/**
 * Graphs which can be rendered using the DOT language.
 *
 * @author Alec Theriault
 * @param <V> vertex in the graph
 * @param <E> edge in the graph
 */
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
   * @param from vertex where the edges starts (or no vertex if {@code null})
   * @param to vertex where the edges ends (or no vertex if {@code null})
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
   * Render the graph into its DOT source.
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
    int gen = 0;
    final Iterable<Edge<V, E>> es = () -> edges().iterator();
    for (Edge<V, E> edge : es) {
      final V fromV = edge.from();
      final V toV = edge.to();

      final var from = escapeId(fromV == null ? ("_gen" + ++gen) : fromV.toString());
      final var to = escapeId(toV == null ? ("_gen" + ++gen) : toV.toString());
      final var label = renderEdgeLabel(edge);
      builder.append("  " + from + " -> " + to + " [label = <" + label + ">];\n");
    }

    // Generated (and blank) vertices
    while (gen > 0) {
      final var genId = escapeId("_gen" + gen--);
      builder.append("  " + genId + " [shape = none, label = <>];\n");
    }

    builder.append("}");
    return builder.toString();
  }

  /**
   * Turn a string into a Dot ID.
   *
   * <p>As per the docs, an ID can be "any double-quoted string ("...") possibly
   * containing escaped quotes (\")".
   *
   * @param str string to escape into an ID
   */
  private static String escapeId(String str) {
    return "\"" + str.replace("\"", "\\\"") + "\"";
  }
}
