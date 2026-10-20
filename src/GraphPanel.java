import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class GraphPanel extends JPanel {

    private enum S { NORMAL, INITIAL, PROVEN, TRYING, FAILED }

    private static final int NODE_H = 36, RULE_H = 30, PAD = 40, ROW_H = 64, MIN_W = 60, MIN_RULE_W = 44;
    private static final Color ORANGE = new Color(0xE65100), PURPLE = new Color(0x6A1B9A);

    private ExpertSystem system;
    private String goal = "";
    private final List<String> factNames = new ArrayList<>();
    private final Map<String, Point> factPos = new HashMap<>();
    private final Map<String, Integer> factW = new HashMap<>();
    private final List<Point> rulePos = new ArrayList<>();
    private final List<Integer> ruleW = new ArrayList<>();
    private final Map<String, S> factState = new HashMap<>();
    private final List<S> ruleState = new ArrayList<>();
    private final Set<String> recentFacts = new HashSet<>();
    private final Set<Integer> recentRules = new HashSet<>();

    private String dragFact;
    private int dragRule = -1;
    private int offX, offY;

    public GraphPanel() {
        setBackground(Color.WHITE);
        setFont(new Font("SansSerif", Font.PLAIN, 13));
        setToolTipText("");

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragFact = null;
                dragRule = hitRule(e.getX(), e.getY());
                Point p = null;
                if (dragRule >= 0) {
                    p = rulePos.get(dragRule);
                } else {
                    dragFact = hitFact(e.getX(), e.getY());
                    if (dragFact != null) p = factPos.get(dragFact);
                }
                if (p != null) {
                    offX = e.getX() - p.x;
                    offY = e.getY() - p.y;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point p = null;
                int w = 0, h = 0;
                if (dragRule >= 0) {
                    p = rulePos.get(dragRule);
                    w = ruleW.get(dragRule);
                    h = RULE_H;
                } else if (dragFact != null) {
                    p = factPos.get(dragFact);
                    w = factW.get(dragFact);
                    h = NODE_H;
                }
                if (p == null) return;
                p.x = Math.max(w / 2 + 6, e.getX() - offX);
                p.y = Math.max(h / 2 + 6, e.getY() - offY);
                updateSize();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragFact = null;
                dragRule = -1;
                updateSize();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean over = hitRule(e.getX(), e.getY()) >= 0 || hitFact(e.getX(), e.getY()) != null;
                setCursor(Cursor.getPredefinedCursor(over ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setKnowledgeBase(ExpertSystem system) {
        this.system = system;
        computeLayout();
        showSteps(new ArrayList<>(), 0);
    }

    public void setGoal(String goal) {
        this.goal = goal == null ? "" : goal;
        repaint();
    }

    public void showSteps(List<String> steps, int upTo) {
        factState.clear();
        ruleState.clear();
        recentFacts.clear();
        recentRules.clear();
        for (String n : factNames) factState.put(n, system.getFacts().contains(n) ? S.INITIAL : S.NORMAL);
        for (int i = 0; i < system.getRules().size(); i++) ruleState.add(S.NORMAL);
        for (int k = 0; k < upTo && k < steps.size(); k++) apply(steps.get(k));
        repaint();
    }

    private static String after(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : null;
    }

    private int ruleIndex(String text) {
        List<Rule> rules = system.getRules();
        for (int i = 0; i < rules.size(); i++) if (rules.get(i).toString().equals(text)) return i;
        return -1;
    }

    private void closeRules(String fact, S result) {
        List<Rule> rules = system.getRules();
        for (int i = 0; i < rules.size(); i++) {
            if (ruleState.get(i) == S.TRYING && rules.get(i).getConclusion().equals(fact)) {
                ruleState.set(i, result);
                recentRules.add(i);
            }
        }
    }

    private void apply(String line) {
        String s = line.trim();
        String v;
        recentFacts.clear();
        recentRules.clear();
        List<Rule> rules = system.getRules();

        if ((v = after(s, "Rule applied: ")) != null) {
            int i = ruleIndex(v);
            if (i >= 0) {
                ruleState.set(i, S.TRYING);
                recentRules.add(i);
            }
        } else if ((v = after(s, "New fact deduced: ")) != null) {
            factState.put(v, S.PROVEN);
            recentFacts.add(v);
            closeRules(v, S.PROVEN);
        } else if ((v = after(s, "Fact found: ")) != null) {
            factState.put(v, S.PROVEN);
            recentFacts.add(v);
        } else if ((v = after(s, "Trying to prove: ")) != null) {
            factState.put(v, S.TRYING);
            recentFacts.add(v);
        } else if ((v = after(s, "Checking rule: ")) != null) {
            int i = ruleIndex(v);
            if (i >= 0) {
                String c = rules.get(i).getConclusion();
                for (int j = 0; j < rules.size(); j++) {
                    if (j != i && ruleState.get(j) == S.TRYING && rules.get(j).getConclusion().equals(c)) ruleState.set(j, S.FAILED);
                }
                ruleState.set(i, S.TRYING);
                recentRules.add(i);
            }
        } else if ((v = after(s, "Rule proves: ")) != null) {
            factState.put(v, S.PROVEN);
            recentFacts.add(v);
            closeRules(v, S.PROVEN);
        } else if ((v = after(s, "Failed to prove: ")) != null) {
            factState.put(v, S.FAILED);
            recentFacts.add(v);
            closeRules(v, S.FAILED);
        } else if ((v = after(s, "Cycle detected: ")) != null) {
            recentFacts.add(v);
        }
    }

    private void computeLayout() {
        List<Rule> rules = system.getRules();
        Set<String> names = new LinkedHashSet<>(system.getFacts());
        for (Rule r : rules) {
            names.addAll(r.getConditions());
            names.add(r.getConclusion());
        }
        factNames.clear();
        factNames.addAll(names);

        FontMetrics fm = getFontMetrics(getFont());
        factW.clear();
        ruleW.clear();
        int maxW = MIN_W;
        for (String n : names) {
            int w = Math.max(MIN_W, fm.stringWidth(n) + 32);
            factW.put(n, w);
            maxW = Math.max(maxW, w);
        }
        for (int i = 0; i < rules.size(); i++) ruleW.add(Math.max(MIN_RULE_W, fm.stringWidth("R" + (i + 1)) + 20));

        Set<String> concluded = new HashSet<>();
        for (Rule r : rules) concluded.add(r.getConclusion());

        Map<String, Integer> level = new HashMap<>();
        for (String n : names) if (system.getFacts().contains(n) || !concluded.contains(n)) level.put(n, 0);

        int[] ruleCol = new int[rules.size()];
        boolean[] placed = new boolean[rules.size()];
        while (true) {
            boolean progress = false;
            for (int i = 0; i < rules.size(); i++) {
                if (placed[i]) continue;
                Rule r = rules.get(i);
                int max = 0;
                boolean ready = true;
                for (String c : r.getConditions()) {
                    Integer l = level.get(c);
                    if (l == null) {
                        ready = false;
                        break;
                    }
                    max = Math.max(max, l);
                }
                if (!ready) continue;
                placed[i] = true;
                progress = true;
                ruleCol[i] = 2 * max + 1;
                level.putIfAbsent(r.getConclusion(), max + 1);
            }
            if (progress) continue;
            int top = 0;
            for (int l : level.values()) top = Math.max(top, l);
            boolean assigned = false;
            for (String n : names) {
                if (!level.containsKey(n)) {
                    level.put(n, top + 1);
                    assigned = true;
                }
            }
            if (!assigned) break;
        }

        Map<Integer, List<Object>> cols = new TreeMap<>();
        for (String n : names) cols.computeIfAbsent(2 * level.get(n), k -> new ArrayList<>()).add(n);
        for (int i = 0; i < rules.size(); i++) cols.computeIfAbsent(ruleCol[i], k -> new ArrayList<>()).add(i);

        int maxCount = 1;
        for (List<Object> items : cols.values()) maxCount = Math.max(maxCount, items.size());

        int step = Math.max(80, maxW / 2 + 60);

        factPos.clear();
        rulePos.clear();
        for (int i = 0; i < rules.size(); i++) rulePos.add(null);

        for (Map.Entry<Integer, List<Object>> e : cols.entrySet()) {
            int x = PAD + maxW / 2 + e.getKey() * step;
            List<Object> items = e.getValue();
            int offset = (maxCount - items.size()) * ROW_H / 2;
            for (int k = 0; k < items.size(); k++) {
                Point p = new Point(x, PAD + NODE_H / 2 + offset + k * ROW_H);
                Object o = items.get(k);
                if (o instanceof String) factPos.put((String) o, p);
                else rulePos.set((Integer) o, p);
            }
        }
        updateSize();
    }

    private void updateSize() {
        int maxX = 0, maxY = 0;
        for (String n : factNames) {
            Point p = factPos.get(n);
            if (p == null) continue;
            maxX = Math.max(maxX, p.x + factW.get(n) / 2);
            maxY = Math.max(maxY, p.y + NODE_H / 2);
        }
        for (int i = 0; i < rulePos.size(); i++) {
            Point p = rulePos.get(i);
            if (p == null) continue;
            maxX = Math.max(maxX, p.x + ruleW.get(i) / 2);
            maxY = Math.max(maxY, p.y + RULE_H / 2);
        }
        setPreferredSize(new Dimension(maxX + PAD, maxY + PAD));
        revalidate();
    }

    private int hitRule(int x, int y) {
        for (int i = rulePos.size() - 1; i >= 0; i--) {
            Point p = rulePos.get(i);
            if (p != null && Math.abs(x - p.x) <= ruleW.get(i) / 2 && Math.abs(y - p.y) <= RULE_H / 2) return i;
        }
        return -1;
    }

    private String hitFact(int x, int y) {
        for (int i = factNames.size() - 1; i >= 0; i--) {
            String n = factNames.get(i);
            Point p = factPos.get(n);
            if (p == null) continue;
            double a = factW.get(n) / 2.0, b = NODE_H / 2.0;
            double dx = (x - p.x) / a, dy = (y - p.y) / b;
            if (dx * dx + dy * dy <= 1) return n;
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (system == null) return;
        List<Rule> rules = system.getRules();
        if (rulePos.size() != rules.size() || ruleState.size() != rules.size()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());

        for (int i = 0; i < rules.size(); i++) {
            S st = ruleState.get(i);
            g2.setColor(edgeColor(st));
            g2.setStroke(new BasicStroke(st == S.NORMAL ? 1.2f : 2.4f));
            Point rp = rulePos.get(i);
            int rw = ruleW.get(i);
            for (String c : rules.get(i).getConditions()) {
                Point fp = factPos.get(c);
                if (fp != null) edge(g2, fp, factW.get(c), NODE_H, true, rp, rw, RULE_H, false);
            }
            String concl = rules.get(i).getConclusion();
            Point cp = factPos.get(concl);
            if (cp != null) edge(g2, rp, rw, RULE_H, false, cp, factW.get(concl), NODE_H, true);
        }

        for (String n : factNames) {
            Point p = factPos.get(n);
            int w = factW.get(n);
            Shape shape = new Ellipse2D.Double(p.x - w / 2.0, p.y - NODE_H / 2.0, w, NODE_H);
            g2.setColor(fill(factState.getOrDefault(n, S.NORMAL)));
            g2.fill(shape);
            boolean recent = recentFacts.contains(n);
            g2.setColor(recent ? ORANGE : Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(recent ? 3.5f : 1.2f));
            g2.draw(shape);
            if (n.equals(goal)) {
                g2.setColor(PURPLE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
                g2.draw(new Ellipse2D.Double(p.x - w / 2.0 - 4, p.y - NODE_H / 2.0 - 4, w + 8, NODE_H + 8));
            }
            label(g2, n, p);
        }

        for (int i = 0; i < rules.size(); i++) {
            Point p = rulePos.get(i);
            int w = ruleW.get(i);
            Shape shape = new RoundRectangle2D.Double(p.x - w / 2.0, p.y - RULE_H / 2.0, w, RULE_H, 10, 10);
            g2.setColor(fill(ruleState.get(i)));
            g2.fill(shape);
            boolean recent = recentRules.contains(i);
            g2.setColor(recent ? ORANGE : Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(recent ? 3.5f : 1.2f));
            g2.draw(shape);
            label(g2, "R" + (i + 1), p);
        }
        g2.dispose();
    }

    private static Point2D boundary(Point c, int w, int h, boolean ellipse, Point toward) {
        double dx = toward.x - c.x, dy = toward.y - c.y;
        if (dx == 0 && dy == 0) return new Point2D.Double(c.x, c.y);
        double t;
        if (ellipse) {
            double a = w / 2.0, b = h / 2.0;
            t = 1.0 / Math.sqrt(dx * dx / (a * a) + dy * dy / (b * b));
        } else {
            double tx = dx != 0 ? (w / 2.0) / Math.abs(dx) : Double.MAX_VALUE;
            double ty = dy != 0 ? (h / 2.0) / Math.abs(dy) : Double.MAX_VALUE;
            t = Math.min(tx, ty);
        }
        return new Point2D.Double(c.x + dx * t, c.y + dy * t);
    }

    private void edge(Graphics2D g2, Point a, int aw, int ah, boolean aEll, Point b, int bw, int bh, boolean bEll) {
        if (a.x == b.x && a.y == b.y) return;
        Point2D s = boundary(a, aw, ah, aEll, b);
        Point2D e = boundary(b, bw, bh, bEll, a);
        g2.draw(new Line2D.Double(s, e));
        double ang = Math.atan2(e.getY() - s.getY(), e.getX() - s.getX());
        double size = 10;
        Path2D head = new Path2D.Double();
        head.moveTo(e.getX(), e.getY());
        head.lineTo(e.getX() - size * Math.cos(ang - 0.4), e.getY() - size * Math.sin(ang - 0.4));
        head.lineTo(e.getX() - size * Math.cos(ang + 0.4), e.getY() - size * Math.sin(ang + 0.4));
        head.closePath();
        g2.fill(head);
    }

    private void label(Graphics2D g2, String text, Point p) {
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.BLACK);
        g2.drawString(text, p.x - fm.stringWidth(text) / 2, p.y + (fm.getAscent() - fm.getDescent()) / 2);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (system == null) return null;
        int r = hitRule(e.getX(), e.getY());
        if (r >= 0) return "R" + (r + 1) + ": " + system.getRules().get(r);
        return hitFact(e.getX(), e.getY());
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