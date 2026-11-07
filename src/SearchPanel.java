import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class SearchPanel extends JPanel {

    private final SearchBoard board = new SearchBoard();
    private final JComboBox<SearchAlgorithms.Method> algo = new JComboBox<>(SearchAlgorithms.Method.values());
    private final JButton prev = new JButton("◀ Prev");
    private final JButton next = new JButton("Next ▶");
    private final JButton play = new JButton("Play");
    private final JLabel status = new JLabel(" ");
    private final javax.swing.Timer timer = new javax.swing.Timer(900, null);
    private SearchAlgorithms.Result result;
    private int stepIndex = 0;

    public SearchPanel(Runnable onBack) {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JButton back = new JButton("← Main menu");
        JToggleButton select = new JToggleButton("Select / Move", true);
        JToggleButton add = new JToggleButton("+ Add node");
        JToggleButton connect = new JToggleButton("Connect");
        ButtonGroup tools = new ButtonGroup();
        tools.add(select);
        tools.add(add);
        tools.add(connect);
        JButton clearBoard = new JButton("Clear board");

        back.addActionListener(e -> {
            stopPlay();
            onBack.run();
        });
        select.addActionListener(e -> board.setTool(SearchBoard.Tool.SELECT));
        add.addActionListener(e -> board.setTool(SearchBoard.Tool.ADD));
        connect.addActionListener(e -> board.setTool(SearchBoard.Tool.CONNECT));
        clearBoard.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Clear the whole board?", "Clear board", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                board.clearAll();
            }
        });

        JButton start = new JButton("Start");
        JButton reset = new JButton("Reset");
        start.addActionListener(e -> start());
        prev.addActionListener(e -> {
            stopPlay();
            goTo(stepIndex - 1);
        });
        next.addActionListener(e -> {
            stopPlay();
            goTo(stepIndex + 1);
        });
        play.addActionListener(e -> togglePlay());
        reset.addActionListener(e -> resetSearch());
        algo.addActionListener(e -> {
            board.setShowHeuristic(usesHeuristic());
            resetSearch();
        });
        timer.addActionListener(e -> tick());

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row1.add(back);
        row1.add(Box.createHorizontalStrut(14));
        row1.add(select);
        row1.add(add);
        row1.add(connect);
        row1.add(Box.createHorizontalStrut(14));
        row1.add(clearBoard);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row2.add(new JLabel("Algorithm:"));
        row2.add(algo);
        row2.add(start);
        row2.add(prev);
        row2.add(next);
        row2.add(play);
        row2.add(reset);
        row2.add(Box.createHorizontalStrut(14));
        row2.add(status);

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(row1);
        top.add(row2);

        board.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        board.setOnChange(this::resetSearch);

        add(top, BorderLayout.NORTH);
        add(board, BorderLayout.CENTER);

        board.setShowHeuristic(usesHeuristic());
        resetSearch();
    }

    private boolean usesHeuristic() {
        Object m = algo.getSelectedItem();
        return m == SearchAlgorithms.Method.GREEDY || m == SearchAlgorithms.Method.ASTAR;
    }

    private void start() {
        String s = board.startLabel(), g = board.goalLabel();
        if (s == null || g == null) {
            JOptionPane.showMessageDialog(this, "Set a Start node and a Goal node first: click a node and choose Start or Goal.");
            return;
        }
        stopPlay();
        result = SearchAlgorithms.run((SearchAlgorithms.Method) algo.getSelectedItem(), board.buildGraph(), s, g);
        goTo(1);
    }

    private void resetSearch() {
        stopPlay();
        result = null;
        goTo(0);
    }

    private void goTo(int n) {
        List<SearchAlgorithms.Step> steps = result == null ? Collections.emptyList() : result.steps;
        stepIndex = Math.max(0, Math.min(n, steps.size()));
        board.showStep(stepIndex == 0 ? null : steps.get(stepIndex - 1));

        boolean more = stepIndex < steps.size();
        prev.setEnabled(stepIndex > 0);
        next.setEnabled(more);
        play.setEnabled(more);

        if (result == null) {
            status.setText("Press Start to run the selected algorithm");
        } else if (more) {
            status.setText("Step " + stepIndex + " / " + steps.size());
        } else if (result.found) {
            status.setText("Path: " + String.join(" → ", result.path) + "   |   cost " + SearchAlgorithms.fmt(result.cost)
                    + "   |   " + result.expandedCount + " nodes expanded");
        } else {
            status.setText("No path found   |   " + result.expandedCount + " nodes expanded");
        }
    }

    private void togglePlay() {
        if (timer.isRunning()) {
            stopPlay();
        } else if (result != null && stepIndex < result.steps.size()) {
            timer.start();
            play.setText("Pause");
        }
    }

    private void tick() {
        goTo(stepIndex + 1);
        if (result == null || stepIndex >= result.steps.size()) stopPlay();
    }

    private void stopPlay() {
        timer.stop();
        play.setText("Play");
    }
}
