import java.util.*;

public class SearchGraph {

    public static class Edge {
        public final String from, to;
        public final double weight;

        public Edge(String from, String to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
    }

    private final Map<String, Double> heuristics = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    public void addNode(String name, double h) {
        heuristics.put(name, h);
    }

    public boolean addEdge(String from, String to, double weight) {
        if (!heuristics.containsKey(from) || !heuristics.containsKey(to)) return false;
        if (weight < 0 || Double.isNaN(weight) || Double.isInfinite(weight)) return false;
        edges.add(new Edge(from, to, weight));
        return true;
    }

    public boolean hasNode(String name) {
        return heuristics.containsKey(name);
    }

    public Set<String> nodes() {
        return heuristics.keySet();
    }

    public double heuristic(String name) {
        return heuristics.getOrDefault(name, 0.0);
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<Edge> edgesFrom(String name) {
        List<Edge> out = new ArrayList<>();
        for (Edge e : edges) if (e.from.equals(name)) out.add(e);
        return out;
    }
}
