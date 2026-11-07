import java.util.*;

public class SearchAlgorithms {

    public enum Method {
        BFS("Breadth-first (BFS)"),
        DFS("Depth-first (DFS)"),
        UCS("Uniform cost (Dijkstra)"),
        GREEDY("Greedy best-first"),
        ASTAR("A*");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class Step {
        public final String node, text;
        public final List<String> frontier, frontierInfo, expanded, path;

        Step(String node, String text, List<String> frontier, List<String> frontierInfo,
             List<String> expanded, List<String> path) {
            this.node = node;
            this.text = text;
            this.frontier = frontier;
            this.frontierInfo = frontierInfo;
            this.expanded = expanded;
            this.path = path;
        }
    }

    public static class Result {
        public final boolean found;
        public final List<String> path;
        public final double cost;
        public final int expandedCount;
        public final List<Step> steps;

        Result(boolean found, List<String> path, double cost, int expandedCount, List<Step> steps) {
            this.found = found;
            this.path = path;
            this.cost = cost;
            this.expandedCount = expandedCount;
            this.steps = steps;
        }
    }

    private static class Item {
        final String state;
        final Item parent;
        final double g, h;
        final long seq;

        Item(String state, Item parent, double g, double h, long seq) {
            this.state = state;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.seq = seq;
        }
    }

    public static Result run(Method m, SearchGraph graph, String start, String goal) {
        List<Step> steps = new ArrayList<>();
        List<Item> open = new ArrayList<>();
        Set<String> expanded = new LinkedHashSet<>();
        Map<String, Double> best = new HashMap<>();
        boolean reopen = m == Method.UCS || m == Method.ASTAR;
        long seq = 0;
        int count = 0;

        open.add(new Item(start, null, 0, graph.heuristic(start), seq++));
        best.put(start, 0.0);

        while (!open.isEmpty()) {
            Item it = pick(m, open);
            open.remove(it);
            if (reopen ? it.g > best.getOrDefault(it.state, Double.MAX_VALUE) : expanded.contains(it.state)) continue;

            expanded.add(it.state);
            count++;

            if (it.state.equals(goal)) {
                List<String> path = pathOf(it);
                steps.add(snapshot(m, it.state, "Goal reached: " + String.join(" -> ", path)
                        + "   cost = " + fmt(it.g), open, expanded, best, reopen, path));
                return new Result(true, path, it.g, count, steps);
            }

            List<SearchGraph.Edge> out = graph.edgesFrom(it.state);
            if (m == Method.DFS) Collections.reverse(out);
            for (SearchGraph.Edge e : out) {
                double ng = it.g + e.weight;
                if (reopen) {
                    if (ng >= best.getOrDefault(e.to, Double.MAX_VALUE)) continue;
                    best.put(e.to, ng);
                } else if (expanded.contains(e.to)) {
                    continue;
                }
                open.add(new Item(e.to, it, ng, graph.heuristic(e.to), seq++));
            }

            steps.add(snapshot(m, it.state, "Expand " + it.state + "  (" + info(m, it) + ")",
                    open, expanded, best, reopen, Collections.emptyList()));
        }

        steps.add(snapshot(m, null, "Frontier empty: no path from " + start + " to " + goal,
                open, expanded, best, reopen, Collections.emptyList()));
        return new Result(false, Collections.emptyList(), 0, count, steps);
    }

    private static Item pick(Method m, List<Item> open) {
        if (m == Method.BFS) return open.get(0);
        if (m == Method.DFS) return open.get(open.size() - 1);
        return Collections.min(open, order(m));
    }

    private static Comparator<Item> order(Method m) {
        return Comparator.<Item>comparingDouble(i -> key(m, i)).thenComparingLong(i -> i.seq);
    }

    private static double key(Method m, Item i) {
        switch (m) {
            case UCS: return i.g;
            case GREEDY: return i.h;
            case ASTAR: return i.g + i.h;
            default: return 0;
        }
    }

    private static String info(Method m, Item i) {
        switch (m) {
            case GREEDY: return "h=" + fmt(i.h);
            case ASTAR: return "g=" + fmt(i.g) + ", h=" + fmt(i.h) + ", f=" + fmt(i.g + i.h);
            default: return "g=" + fmt(i.g);
        }
    }

    private static Step snapshot(Method m, String node, String text, List<Item> open, Set<String> expanded,
                                 Map<String, Double> best, boolean reopen, List<String> path) {
        List<Item> live = new ArrayList<>();
        for (Item i : open) {
            boolean stale = reopen ? i.g > best.getOrDefault(i.state, Double.MAX_VALUE) : expanded.contains(i.state);
            if (!stale) live.add(i);
        }
        if (m == Method.DFS) Collections.reverse(live);
        else if (m != Method.BFS) live.sort(order(m));
        List<String> states = new ArrayList<>(), infos = new ArrayList<>();
        for (Item i : live) {
            if (!reopen && states.contains(i.state)) continue;
            states.add(i.state);
            infos.add(i.state + " (" + info(m, i) + ")");
        }
        return new Step(node, text, states, infos, new ArrayList<>(expanded), path);
    }

    private static List<String> pathOf(Item it) {
        LinkedList<String> p = new LinkedList<>();
        for (Item i = it; i != null; i = i.parent) p.addFirst(i.state);
        return new ArrayList<>(p);
    }

    public static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 1000) / 1000.0);
    }
}
