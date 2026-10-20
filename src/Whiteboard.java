import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class Whiteboard extends JPanel {

    public enum Tool { SELECT, ADD, CONNECT }

    private enum S { NORMAL, INITIAL, PROVEN, TRYING, FAILED }

    private static class N {
        int id;
        String label;
        int x, y;
        boolean fact, goal, rule;
    }

    private static class E {
        final N from, to;

        E(N from, N to) {
            this.from = from;
            this.to = to;
        }
    }

    private static final int NODE_H = 36, RULE_H = 30;
    private static final Color ORANGE = new Color(0xE65100), PURPLE = new Color(0x6A1B9A);
    private static final Stroke DASH = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f);
    private static final Stroke RING = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f);

    private final List<N> nodes = new ArrayList<>();
    private final List<E> edges = new ArrayList<>();
    private final Map<N, S> state = new HashMap<>();
    private final Set<N> recent = new HashSet<>();
    private final javax.swing.Timer messageTimer;
    private List<N> snapRules = new ArrayList<>();
    private ExpertSystem snapSystem;
    private Tool tool = Tool.SELECT;
    private Runnable onChange;
    private int nextId = 1;
    private N dragNode, connectFrom, pressedNode;
    private E pressedEdge;
    private int offX, offY, pressX, pressY;
    private boolean moved;
    private Point mouse = new Point();
    private String message = "";

    public Whiteboard() {
        setBackground(Color.WHITE);
        setFont(new Font("SansSerif", Font.PLAIN, 14));
        setToolTipText("");
        messageTimer = new javax.swing.Timer(3500, ev -> {
            message = "";
            repaint();
        });
        messageTimer.setRepeats(false);

        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressX = e.getX();
                pressY = e.getY();
                moved = false;
                mouse = e.getPoint();
                dragNode = null;
                connectFrom = null;
                pressedNode = hitNode(e.getX(), e.getY());
                pressedEdge = pressedNode == null ? hitEdge(e.getX(), e.getY()) : null;
                if (pressedNode != null) {
                    if (tool == Tool.CONNECT) {
                        connectFrom = pressedNode;
                    } else {
                        dragNode = pressedNode;
                        offX = e.getX() - pressedNode.x;
                        offY = e.getY() - pressedNode.y;
                    }
                } else if (tool == Tool.ADD && pressedEdge == null) {
                    addNode(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouse = e.getPoint();
                if (Math.abs(e.getX() - pressX) > 3 || Math.abs(e.getY() - pressY) > 3) moved = true;
                if (dragNode != null && moved) {
                    int hw = w(dragNode) / 2 + 4, hh = h(dragNode) / 2 + 4;
                    dragNode.x = Math.max(hw, Math.min(getWidth() - hw, e.getX() - offX));
                    dragNode.y = Math.max(hh, Math.min(getHeight() - hh, e.getY() - offY));
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (connectFrom != null) {
                    N to = hitNode(e.getX(), e.getY());
                    if (to != null && to != connectFrom) addEdge(connectFrom, to);
                    connectFrom = null;
                    repaint();
                } else if (!moved && pressedNode != null) {
                    showNodeMenu(pressedNode, e.getX(), e.getY());
                } else if (!moved && pressedEdge != null && tool != Tool.CONNECT) {
                    showEdgeMenu(pressedEdge, e.getX(), e.getY());
                }
                dragNode = null;
                pressedNode = null;
                pressedEdge = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean over = hitNode(e.getX(), e.getY()) != null;
                int c = over ? Cursor.HAND_CURSOR : tool == Tool.ADD ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR;
                setCursor(Cursor.getPredefinedCursor(c));
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setOnChange(Runnable r) {
        onChange = r;
    }

    public void setTool(Tool t) {
        tool = t;
        connectFrom = null;
        repaint();
    }

    public void clearAll() {
        nodes.clear();
        edges.clear();
        nextId = 1;
        changed();
    }

    public String goalLabel() {
        for (N n : nodes) if (n.goal && !n.rule) return n.label;
        return null;
    }

    public boolean goalIsFact() {
        for (N n : nodes) if (n.goal && n.fact && !n.rule) return true;
        return false;
    }

    public ExpertSystem buildSystem() {
        ExpertSystem sys = new ExpertSystem();
        sys.getFacts().clear();
        sys.getRules().clear();
        snapRules = new ArrayList<>();
        for (N n : nodes) if (!n.rule && n.fact) sys.addFact(n.label);
        for (N n : nodes) {
            if (!n.rule) continue;
            Rule r = ruleOf(n);
            if (r != null && sys.addRule(r.getConditions(), r.getConclusion())) snapRules.add(n);
        }
        snapSystem = sys;
        return sys;
    }

    public void showSteps(List<String> steps, int upTo) {
        state.clear();
        recent.clear();
        for (N n : nodes) state.put(n, !n.rule && n.fact ? S.INITIAL : S.NORMAL);
        for (int k = 0; k < upTo && k < steps.size(); k++) apply(steps.get(k));
        repaint();
    }

    public void save(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        for (N n : nodes) {
            String flags = "-" + (n.fact ? "F" : "") + (n.goal ? "G" : "") + (n.rule ? "R" : "");
            lines.add(String.join("|", "NODE", String.valueOf(n.id), flags, String.valueOf(n.x), String.valueOf(n.y), n.label));
        }
        for (E e : edges) lines.add("EDGE|" + e.from.id + "|" + e.to.id);
        Files.write(path, lines);
    }

    public void load(Path path) throws IOException {
        List<N> ns = new ArrayList<>();
        Map<Integer, N> byId = new HashMap<>();
        List<E> es = new ArrayList<>();
        int max = 0;
        for (String line : Files.readAllLines(path)) {
            String[] p = line.split("\\|", 6);
            if (p[0].equals("NODE") && p.length == 6) {
                N n = new N();
                n.id = Integer.parseInt(p[1]);
                n.fact = p[2].contains("F");
                n.goal = p[2].contains("G");
                n.rule = p[2].contains("R");
                n.x = Integer.parseInt(p[3]);
                n.y = Integer.parseInt(p[4]);
                n.label = p[5].trim();
                ns.add(n);
                byId.put(n.id, n);
                max = Math.max(max, n.id);
            } else if (p[0].equals("EDGE") && p.length >= 3) {
                N a = byId.get(Integer.parseInt(p[1]));
                N b = byId.get(Integer.parseInt(p[2]));
                if (a != null && b != null) es.add(new E(a, b));
            }
        }
        nodes.clear();
        nodes.addAll(ns);
        edges.clear();
        edges.addAll(es);
        nextId = max + 1;
        changed();
    }

    private void changed() {
        edges.removeIf(e -> e.from.rule == e.to.rule);
        repaint();
        if (onChange != null) onChange.run();
    }

    private void flash(String m) {
        message = m;
        messageTimer.restart();
        repaint();
    }

    private int w(N n) {
        FontMetrics fm = getFontMetrics(getFont());
        return n.rule ? Math.max(44, fm.stringWidth(text(n)) + 20) : Math.max(60, fm.stringWidth(n.label) + 32);
    }

    private int h(N n) {
        return n.rule ? RULE_H : NODE_H;
    }

    private int ruleNumber(N n) {
        int k = 0;
        for (N o : nodes) {
            if (o.rule) k++;
            if (o == n) break;
        }
        return k;
    }

    private String text(N n) {
        return n.rule ? "R" + ruleNumber(n) : n.label;
    }

    private boolean labelTaken(String l, N except) {
        for (N o : nodes) if (o != except && !o.rule && o.label.equals(l)) return true;
        return false;
    }

    private String freshLabel() {
        for (int i = 0; ; i++) {
            String c = String.valueOf((char) ('A' + i % 26)) + (i >= 26 ? String.valueOf(i / 26) : "");
            if (!labelTaken(c, null)) return c;
        }
    }

    private Rule ruleOf(N r) {
        List<String> cond = new ArrayList<>();
        String concl = null;
        int outs = 0;
        for (E e : edges) {
            if (e.to == r) cond.add(e.from.label);
            if (e.from == r) {
                concl = e.to.label;
                outs++;
            }
        }
        return cond.isEmpty() || outs != 1 ? null : new Rule(cond, concl);
    }

    private void addNode(int x, int y) {
        N n = new N();
        n.id = nextId++;
        n.x = x;
        n.y = y;
        n.label = freshLabel();
        nodes.add(n);
        changed();
    }

    private void addEdge(N a, N b) {
        if (a.rule == b.rule) {
            flash("Arrows go: condition -> Rule -> conclusion. Link a node to a Rule node, or a Rule node to its conclusion.");
            return;
        }
        for (E e : edges) {
            if (e.from == a && e.to == b) {
                flash("These two nodes are already linked.");
                return;
            }
        }
        if (a.rule) {
            for (E e : edges) {
                if (e.from == a) {
                    flash("A rule has only one conclusion.");
                    return;
                }
            }
        }
        edges.add(new E(a, b));
        changed();
    }

    private void deleteNode(N n) {
        nodes.remove(n);
        edges.removeIf(e -> e.from == n || e.to == n);
        changed();
    }

    private void toggleFact(N n, boolean on) {
        n.fact = on;
        if (on) n.rule = false;
        afterRole(n);
    }

    private void toggleGoal(N n, boolean on) {
        for (N o : nodes) o.goal = false;
        n.goal = on;
        if (on) n.rule = false;
        afterRole(n);
    }

    private void toggleRule(N n, boolean on) {
        n.rule = on;
        if (on) {
            n.fact = false;
            n.goal = false;
        }
        afterRole(n);
    }

    private void afterRole(N n) {
        if (!n.rule && labelTaken(n.label, n)) n.label = freshLabel();
        changed();
    }

    private void rename(N n) {
        String s = JOptionPane.showInputDialog(this, "Name:", n.label);
        if (s == null) return;
        String l = s.trim().replace('|', '/');
        if (l.isEmpty()) return;
        if (labelTaken(l, n)) {
            flash("A node named \"" + l + "\" already exists.");
            return;
        }
        n.label = l;
        changed();
    }

    private void showNodeMenu(N n, int x, int y) {
        JPopupMenu m = new JPopupMenu();
        JCheckBoxMenuItem fact = new JCheckBoxMenuItem("Fact (known true)", n.fact);
        JCheckBoxMenuItem goalItem = new JCheckBoxMenuItem("Goal (to prove)", n.goal);
        JCheckBoxMenuItem rule = new JCheckBoxMenuItem("Rule (IF ... THEN ...)", n.rule);
        fact.addActionListener(e -> toggleFact(n, fact.isSelected()));
        goalItem.addActionListener(e -> toggleGoal(n, goalItem.isSelected()));
        rule.addActionListener(e -> toggleRule(n, rule.isSelected()));
        m.add(fact);
        m.add(goalItem);
        m.add(rule);
        m.addSeparator();
        if (!n.rule) {
            JMenuItem rename = new JMenuItem("Rename...");
            rename.addActionListener(e -> rename(n));
            m.add(rename);
        }
        JMenuItem del = new JMenuItem("Delete node");
        del.addActionListener(e -> deleteNode(n));
        m.add(del);
        m.show(this, x, y);
    }

    private void showEdgeMenu(E edge, int x, int y) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem del = new JMenuItem("Delete arrow");
        del.addActionListener(e -> {
            edges.remove(edge);
            changed();
        });
        m.add(del);
        m.show(this, x, y);
    }

    private N hitNode(int x, int y) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            N n = nodes.get(i);
            double dx = x - n.x, dy = y - n.y;
            double a = w(n) / 2.0, b = h(n) / 2.0;
            if (n.rule) {
                if (Math.abs(dx) <= a && Math.abs(dy) <= b) return n;
            } else if ((dx / a) * (dx / a) + (dy / b) * (dy / b) <= 1) {
                return n;
            }
        }
        return null;
    }

    private E hitEdge(int x, int y) {
        for (E e : edges) {
            if (e.from.x == e.to.x && e.from.y == e.to.y) continue;
            Point2D s = boundary(e.from, e.to.x, e.to.y);
            Point2D t = boundary(e.to, e.from.x, e.from.y);
            if (Line2D.ptSegDist(s.getX(), s.getY(), t.getX(), t.getY(), x, y) <= 6) return e;
        }
        return null;
    }

    private Point2D boundary(N c, double tx, double ty) {
        double dx = tx - c.x, dy = ty - c.y;
        if (dx == 0 && dy == 0) return new Point2D.Double(c.x, c.y);
        double a = w(c) / 2.0, b = h(c) / 2.0, t;
        if (c.rule) {
            double sx = dx != 0 ? a / Math.abs(dx) : Double.MAX_VALUE;
            double sy = dy != 0 ? b / Math.abs(dy) : Double.MAX_VALUE;
            t = Math.min(sx, sy);
        } else {
            t = 1.0 / Math.sqrt(dx * dx / (a * a) + dy * dy / (b * b));
        }
        return new Point2D.Double(c.x + dx * t, c.y + dy * t);
    }

    private static String after(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : null;
    }

    private S stateOf(N n) {
        return state.getOrDefault(n, !n.rule && n.fact ? S.INITIAL : S.NORMAL);
    }

    private N propNode(String label) {
        for (N n : nodes) if (!n.rule && n.label.equals(label)) return n;
        return null;
    }

    private N ruleNode(String text) {
        List<Rule> rules = snapSystem.getRules();
        for (int i = 0; i < rules.size(); i++) if (rules.get(i).toString().equals(text)) return snapRules.get(i);
        return null;
    }

    private void mark(String label, S s) {
        N n = propNode(label);
        if (n != null) {
            state.put(n, s);
            recent.add(n);
        }
    }

    private void closeRules(String fact, S result) {
        List<Rule> rules = snapSystem.getRules();
        for (int i = 0; i < rules.size(); i++) {
            N r = snapRules.get(i);
            if (state.get(r) == S.TRYING && rules.get(i).getConclusion().equals(fact)) {
                state.put(r, result);
                recent.add(r);
            }
        }
    }

    private void apply(String line) {
        if (snapSystem == null) return;
        String s = line.trim();
        String v;
        recent.clear();
        List<Rule> rules = snapSystem.getRules();

        if ((v = after(s, "Rule applied: ")) != null) {
            N r = ruleNode(v);
            if (r != null) {
                state.put(r, S.TRYING);
                recent.add(r);
            }
        } else if ((v = after(s, "New fact deduced: ")) != null) {
            mark(v, S.PROVEN);
            closeRules(v, S.PROVEN);
        } else if ((v = after(s, "Fact found: ")) != null) {
            mark(v, S.PROVEN);
        } else if ((v = after(s, "Trying to prove: ")) != null) {
            mark(v, S.TRYING);
        } else if ((v = after(s, "Checking rule: ")) != null) {
            N r = ruleNode(v);
            if (r != null) {
                int i = snapRules.indexOf(r);
                String c = rules.get(i).getConclusion();
                for (int j = 0; j < rules.size(); j++) {
                    N o = snapRules.get(j);
                    if (j != i && state.get(o) == S.TRYING && rules.get(j).getConclusion().equals(c)) state.put(o, S.FAILED);
                }
                state.put(r, S.TRYING);
                recent.add(r);
            }
        } else if ((v = after(s, "Rule proves: ")) != null) {
            mark(v, S.PROVEN);
            closeRules(v, S.PROVEN);
        } else if ((v = after(s, "Failed to prove: ")) != null) {
            mark(v, S.FAILED);
            closeRules(v, S.FAILED);
        } else if ((v = after(s, "Cycle detected: ")) != null) {
            N n = propNode(v);
            if (n != null) recent.add(n);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());

        for (E e : edges) {
            if (e.from.x == e.to.x && e.from.y == e.to.y) continue;
            S st = stateOf(e.from.rule ? e.from : e.to);
            g2.setColor(edgeColor(st));
            g2.setStroke(new BasicStroke(st == S.NORMAL ? 1.4f : 2.4f));
            arrow(g2, boundary(e.from, e.to.x, e.to.y), boundary(e.to, e.from.x, e.from.y));
        }

        if (connectFrom != null) {
            g2.setColor(new Color(0x888888));
            g2.setStroke(DASH);
            g2.draw(new Line2D.Double(connectFrom.x, connectFrom.y, mouse.x, mouse.y));
        }

        FontMetrics fm = g2.getFontMetrics();
        for (N n : nodes) {
            int w = w(n), h = h(n);
            Shape shape = n.rule
                    ? new RoundRectangle2D.Double(n.x - w / 2.0, n.y - h / 2.0, w, h, 10, 10)
                    : new Ellipse2D.Double(n.x - w / 2.0, n.y - h / 2.0, w, h);
            g2.setColor(fill(stateOf(n)));
            g2.fill(shape);
            boolean rec = recent.contains(n);
            boolean incomplete = n.rule && ruleOf(n) == null;
            g2.setColor(rec ? ORANGE : Color.DARK_GRAY);
            g2.setStroke(rec ? new BasicStroke(3.5f) : incomplete ? DASH : new BasicStroke(1.4f));
            g2.draw(shape);
            if (n.goal) {
                g2.setColor(PURPLE);
                g2.setStroke(RING);
                g2.draw(new Ellipse2D.Double(n.x - w / 2.0 - 4, n.y - h / 2.0 - 4, w + 8, h + 8));
            }
            String t = text(n);
            g2.setColor(Color.BLACK);
            g2.drawString(t, n.x - fm.stringWidth(t) / 2, n.y + (fm.getAscent() - fm.getDescent()) / 2);
        }

        String hint = tool == Tool.SELECT
                ? "Drag to move  |  click a node to set it as Fact / Goal / Rule  |  click an arrow to delete it"
                : tool == Tool.ADD
                ? "Click on the board to place a node"
                : "Drag from one node to another to draw an arrow: condition -> Rule -> conclusion";
        g2.setColor(new Color(0x9E9E9E));
        g2.drawString(hint, 12, getHeight() - 12);
        if (!message.isEmpty()) {
            g2.setColor(ORANGE);
            g2.drawString(message, 12, getHeight() - 32);
        }
        if (nodes.isEmpty()) {
            String t = "Empty board: press \"+ Add node\", then click here";
            g2.setColor(new Color(0xBDBDBD));
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, getHeight() / 2);
        }
        g2.dispose();
    }

    private void arrow(Graphics2D g2, Point2D s, Point2D t) {
        g2.draw(new Line2D.Double(s, t));
        double ang = Math.atan2(t.getY() - s.getY(), t.getX() - s.getX());
        double size = 10;
        Path2D head = new Path2D.Double();
        head.moveTo(t.getX(), t.getY());
        head.lineTo(t.getX() - size * Math.cos(ang - 0.4), t.getY() - size * Math.sin(ang - 0.4));
        head.lineTo(t.getX() - size * Math.cos(ang + 0.4), t.getY() - size * Math.sin(ang + 0.4));
        head.closePath();
        g2.fill(head);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        N n = hitNode(e.getX(), e.getY());
        if (n == null || !n.rule) return null;
        Rule r = ruleOf(n);
        return r == null
                ? "Incomplete rule: needs at least one incoming arrow and exactly one outgoing arrow"
                : "R" + ruleNumber(n) + ": " + r;
    }

    private static Color fill(S s) {
        switch (s) {
            case INITIAL: return new Color(0xBBDEFB);
            case PROVEN: return new Color(0xA5D6A7);
            case TRYING: return new Color(0xFFE082);
            case FAILED: return new Color(0xEF9A9A);
            default: return new Color(0xEEEEEE);
        }
    }

    private static Color edgeColor(S s) {
        switch (s) {
            case PROVEN: return new Color(0x2E7D32);
            case TRYING: return new Color(0xF9A825);
            case FAILED: return new Color(0xC62828);
            default: return new Color(0x888888);
        }
    }
}