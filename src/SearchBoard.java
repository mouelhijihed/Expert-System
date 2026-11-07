import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class SearchBoard extends JPanel {

    public enum Tool { SELECT, ADD, CONNECT }

    private static class N {
        String label;
        int x, y;
        double h;
    }

    private static class E {
        final N from, to;
        double w;

        E(N from, N to, double w) {
            this.from = from;
            this.to = to;
            this.w = w;
        }
    }

    private static final int NODE_H = 38;
    private static final Color ORANGE = new Color(0xE65100), PURPLE = new Color(0x6A1B9A), DARK_GREEN = new Color(0x2E7D32);
    private static final Stroke DASH = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f);

    private final List<N> nodes = new ArrayList<>();
    private final List<E> edges = new ArrayList<>();
    private final javax.swing.Timer messageTimer;
    private Set<String> expanded = new HashSet<>(), frontier = new HashSet<>();
    private List<String> path = new ArrayList<>();
    private String current;
    private N start, goal, dragNode, connectFrom, pressedNode;
    private E pressedEdge;
    private Tool tool = Tool.SELECT;
    private Runnable onChange;
    private int nextId = 0, offX, offY, pressX, pressY;
    private boolean moved, showH;
    private Point mouse = new Point();
    private String message = "";

    public SearchBoard() {
        setBackground(Color.WHITE);
        setFont(new Font("SansSerif", Font.PLAIN, 14));
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
                    int hw = w(dragNode) / 2 + 4, hh = NODE_H / 2 + 14;
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

    public void setShowHeuristic(boolean on) {
        showH = on;
        repaint();
    }

    public void setTool(Tool t) {
        tool = t;
        connectFrom = null;
        repaint();
    }

    public void clearAll() {
        nodes.clear();
        edges.clear();
        start = null;
        goal = null;
        nextId = 0;
        changed();
    }

    public String startLabel() {
        return start == null ? null : start.label;
    }

    public String goalLabel() {
        return goal == null ? null : goal.label;
    }

    public SearchGraph buildGraph() {
        SearchGraph g = new SearchGraph();
        for (N n : nodes) g.addNode(n.label, n.h);
        for (E e : edges) g.addEdge(e.from.label, e.to.label, e.w);
        return g;
    }

    public void showStep(SearchAlgorithms.Step s) {
        if (s == null) {
            expanded = new HashSet<>();
            frontier = new HashSet<>();
            path = new ArrayList<>();
            current = null;
        } else {
            expanded = new HashSet<>(s.expanded);
            frontier = new HashSet<>(s.frontier);
            path = s.path;
            current = s.node;
        }
        repaint();
    }

    private void node(String label, int x, int y, double h) {
        N n = new N();
        n.label = label;
        n.x = x;
        n.y = y;
        n.h = h;
        nodes.add(n);
    }

    private void changed() {
        repaint();
        if (onChange != null) onChange.run();
    }

    private void flash(String m) {
        message = m;
        messageTimer.restart();
        repaint();
    }

    private int w(N n) {
        return Math.max(50, getFontMetrics(getFont()).stringWidth(n.label) + 30);
    }

    private boolean labelTaken(String l, N except) {
        for (N o : nodes) if (o != except && o.label.equals(l)) return true;
        return false;
    }

    private String freshLabel() {
        for (int i = nextId; ; i++) {
            String c = String.valueOf((char) ('A' + i % 26)) + (i >= 26 ? String.valueOf(i / 26) : "");
            if (!labelTaken(c, null)) {
                nextId = i + 1;
                return c;
            }
        }
    }

    private void addNode(int x, int y) {
        node(freshLabel(), x, y, 0);
        changed();
    }

    private Double askNumber(String prompt, double def) {
        String s = JOptionPane.showInputDialog(this, prompt, SearchAlgorithms.fmt(def));
        if (s == null) return null;
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException ex) {
            flash("Enter a non-negative number.");
            return null;
        }
    }

    private E edgeBetween(N a, N b) {
        for (E e : edges) if (e.from == a && e.to == b) return e;
        return null;
    }

    private void addEdge(N a, N b) {
        if (edgeBetween(a, b) != null) {
            flash("There is already an arrow " + a.label + " -> " + b.label + ".");
            return;
        }
        Double w = askNumber("Weight of " + a.label + " -> " + b.label + ":", 1);
        if (w == null) return;
        edges.add(new E(a, b, w));
        changed();
    }

    private void deleteNode(N n) {
        nodes.remove(n);
        edges.removeIf(e -> e.from == n || e.to == n);
        if (start == n) start = null;
        if (goal == n) goal = null;
        changed();
    }

    private void rename(N n) {
        String s = JOptionPane.showInputDialog(this, "Name:", n.label);
        if (s == null) return;
        String l = s.trim();
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
        JCheckBoxMenuItem st = new JCheckBoxMenuItem("Start node", start == n);
        JCheckBoxMenuItem gl = new JCheckBoxMenuItem("Goal node", goal == n);
        st.addActionListener(e -> {
            start = st.isSelected() ? n : null;
            changed();
        });
        gl.addActionListener(e -> {
            goal = gl.isSelected() ? n : null;
            changed();
        });
        JMenuItem h = new JMenuItem("Heuristic h(" + n.label + ") = " + SearchAlgorithms.fmt(n.h) + " ...");
        h.addActionListener(e -> {
            Double v = askNumber("Heuristic value h(" + n.label + "):", n.h);
            if (v != null) {
                n.h = v;
                changed();
            }
        });
        JMenuItem rn = new JMenuItem("Rename...");
        rn.addActionListener(e -> rename(n));
        JMenuItem del = new JMenuItem("Delete node");
        del.addActionListener(e -> deleteNode(n));
        m.add(st);
        m.add(gl);
        if (showH) {
            m.addSeparator();
            m.add(h);
        }
        if (!showH) m.addSeparator();
        m.add(rn);
        m.add(del);
        m.show(this, x, y);
    }

    private void showEdgeMenu(E edge, int x, int y) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem wt = new JMenuItem("Weight " + SearchAlgorithms.fmt(edge.w) + " ...");
        wt.addActionListener(e -> {
            Double v = askNumber("Weight of " + edge.from.label + " -> " + edge.to.label + ":", edge.w);
            if (v != null) {
                edge.w = v;
                changed();
            }
        });
        JMenuItem del = new JMenuItem("Delete arrow");
        del.addActionListener(e -> {
            edges.remove(edge);
            changed();
        });
        m.add(wt);
        m.add(del);
        m.show(this, x, y);
    }

    private N hitNode(int x, int y) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            N n = nodes.get(i);
            double dx = x - n.x, dy = y - n.y, a = w(n) / 2.0, b = NODE_H / 2.0;
            if ((dx / a) * (dx / a) + (dy / b) * (dy / b) <= 1) return n;
        }
        return null;
    }

    private E hitEdge(int x, int y) {
        for (E e : edges) {
            Line2D l = segment(e);
            if (l != null && l.ptSegDist(x, y) <= 7) return e;
        }
        return null;
    }

    private Point2D boundary(N c, double tx, double ty) {
        double dx = tx - c.x, dy = ty - c.y;
        if (dx == 0 && dy == 0) return new Point2D.Double(c.x, c.y);
        double a = w(c) / 2.0, b = NODE_H / 2.0;
        double t = 1.0 / Math.sqrt(dx * dx / (a * a) + dy * dy / (b * b));
        return new Point2D.Double(c.x + dx * t, c.y + dy * t);
    }

    private Line2D segment(E e) {
        if (e.from.x == e.to.x && e.from.y == e.to.y) return null;
        Point2D s = boundary(e.from, e.to.x, e.to.y), t = boundary(e.to, e.from.x, e.from.y);
        double dx = t.getX() - s.getX(), dy = t.getY() - s.getY(), len = Math.hypot(dx, dy);
        if (len == 0) return null;
        double off = edgeBetween(e.to, e.from) != null ? 9 : 0;
        double ox = -dy / len * off, oy = dx / len * off;
        return new Line2D.Double(s.getX() + ox, s.getY() + oy, t.getX() + ox, t.getY() + oy);
    }

    private boolean onPath(E e) {
        for (int i = 0; i + 1 < path.size(); i++) {
            if (path.get(i).equals(e.from.label) && path.get(i + 1).equals(e.to.label)) return true;
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();

        for (E e : edges) {
            Line2D l = segment(e);
            if (l == null) continue;
            boolean p = onPath(e);
            g2.setColor(p ? DARK_GREEN : new Color(0x888888));
            g2.setStroke(new BasicStroke(p ? 3.2f : 1.4f));
            arrow(g2, l);
        }

        for (E e : edges) {
            Line2D l = segment(e);
            if (l == null) continue;
            String t = SearchAlgorithms.fmt(e.w);
            int tw = fm.stringWidth(t) + 8, th = fm.getHeight() - 2;
            double mx = (l.getX1() + l.getX2()) / 2, my = (l.getY1() + l.getY2()) / 2;
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Double(mx - tw / 2.0, my - th / 2.0, tw, th, 8, 8));
            g2.setColor(onPath(e) ? DARK_GREEN : new Color(0x424242));
            g2.drawString(t, (float) (mx - fm.stringWidth(t) / 2.0), (float) (my + (fm.getAscent() - fm.getDescent()) / 2.0));
        }

        if (connectFrom != null) {
            g2.setColor(new Color(0x888888));
            g2.setStroke(DASH);
            g2.draw(new Line2D.Double(connectFrom.x, connectFrom.y, mouse.x, mouse.y));
        }

        Font small = getFont().deriveFont(Font.PLAIN, 11f);
        for (N n : nodes) {
            int w = w(n);
            Shape shape = new Ellipse2D.Double(n.x - w / 2.0, n.y - NODE_H / 2.0, w, NODE_H);
            g2.setColor(fill(n));
            g2.fill(shape);
            boolean cur = n.label.equals(current), onP = path.contains(n.label);
            g2.setColor(cur ? ORANGE : onP ? DARK_GREEN : Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(cur ? 3.5f : onP ? 3f : 1.4f));
            g2.draw(shape);
            if (n == goal) {
                g2.setColor(PURPLE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
                g2.draw(new Ellipse2D.Double(n.x - w / 2.0 - 4, n.y - NODE_H / 2.0 - 4, w + 8, NODE_H + 8));
            }
            g2.setColor(Color.BLACK);
            g2.setFont(getFont());
            g2.drawString(n.label, n.x - fm.stringWidth(n.label) / 2, n.y + (fm.getAscent() - fm.getDescent()) / 2);
            g2.setFont(small);
            FontMetrics sf = g2.getFontMetrics();
            if (showH) {
                String ht = "h=" + SearchAlgorithms.fmt(n.h);
                int hw = sf.stringWidth(ht) + 4, hy = n.y + NODE_H / 2 + 1;
                g2.setColor(Color.WHITE);
                g2.fillRect(n.x - hw / 2, hy, hw, sf.getHeight());
                g2.setColor(new Color(0x757575));
                g2.drawString(ht, n.x - sf.stringWidth(ht) / 2, hy + sf.getAscent());
            }
            String tag = n == start && n == goal ? "start / goal" : n == start ? "start" : n == goal ? "goal" : null;
            if (tag != null) {
                g2.setColor(n == goal ? PURPLE : new Color(0x1565C0));
                g2.drawString(tag, n.x - sf.stringWidth(tag) / 2, n.y - NODE_H / 2 - 7);
            }
            g2.setFont(getFont());
        }

        String hint = tool == Tool.SELECT
                ? "Drag to move  |  click a node: start / goal / rename  |  click an arrow: weight / delete"
                : tool == Tool.ADD
                ? "Click on the board to place a node"
                : "Drag from one node to another to draw a weighted arrow";
        g2.setColor(new Color(0x9E9E9E));
        g2.drawString(hint, 12, getHeight() - 12);
        if (!message.isEmpty()) {
            g2.setColor(ORANGE);
            g2.drawString(message, 12, getHeight() - 32);
        }
        if (nodes.isEmpty()) {
            String t = "Empty board: press \"+ Add node\" then click here, or load the example";
            g2.setColor(new Color(0xBDBDBD));
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, getHeight() / 2);
        }
        g2.dispose();
    }

    private Color fill(N n) {
        if (expanded.contains(n.label)) return new Color(0xA5D6A7);
        if (frontier.contains(n.label)) return new Color(0xFFE082);
        if (n == start) return new Color(0xBBDEFB);
        return new Color(0xEEEEEE);
    }

    private void arrow(Graphics2D g2, Line2D l) {
        g2.draw(l);
        double ang = Math.atan2(l.getY2() - l.getY1(), l.getX2() - l.getX1()), size = 10;
        Path2D head = new Path2D.Double();
        head.moveTo(l.getX2(), l.getY2());
        head.lineTo(l.getX2() - size * Math.cos(ang - 0.4), l.getY2() - size * Math.sin(ang - 0.4));
        head.lineTo(l.getX2() - size * Math.cos(ang + 0.4), l.getY2() - size * Math.sin(ang + 0.4));
        head.closePath();
        g2.fill(head);
    }
}
