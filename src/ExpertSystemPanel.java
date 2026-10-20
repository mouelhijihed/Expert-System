import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ExpertSystemPanel extends JPanel {

    private final Whiteboard board = new Whiteboard();
    private final JRadioButton forward = new JRadioButton("Forward chaining", true);
    private final JRadioButton backward = new JRadioButton("Backward chaining");
    private final JButton prev = new JButton("◀ Prev");
    private final JButton next = new JButton("Next ▶");
    private final JButton play = new JButton("Play");
    private final JLabel goalStatus = new JLabel(" ");
    private final javax.swing.Timer timer = new javax.swing.Timer(800, null);
    private List<String> steps = new ArrayList<>();
    private int stepIndex = 0;

    public ExpertSystemPanel(Runnable onBack) {
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
        select.addActionListener(e -> board.setTool(Whiteboard.Tool.SELECT));
        add.addActionListener(e -> board.setTool(Whiteboard.Tool.ADD));
        connect.addActionListener(e -> board.setTool(Whiteboard.Tool.CONNECT));
        clearBoard.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Clear the whole board?", "Clear board", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                board.clearAll();
            }
        });

        ButtonGroup method = new ButtonGroup();
        method.add(forward);
        method.add(backward);

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
        reset.addActionListener(e -> resetReasoning());
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
        row2.add(forward);
        row2.add(backward);
        row2.add(start);
        row2.add(prev);
        row2.add(next);
        row2.add(play);
        row2.add(reset);
        row2.add(Box.createHorizontalStrut(14));
        row2.add(goalStatus);

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(row1);
        top.add(row2);

        board.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        board.setOnChange(() -> {
            resetReasoning();
            updateGoalStatus();
        });

        add(top, BorderLayout.NORTH);
        add(board, BorderLayout.CENTER);

        resetReasoning();
        updateGoalStatus();
    }

    private void updateGoalStatus() {
        String g = board.goalLabel();
        if (g == null) goalStatus.setText("No goal: click a node and choose Goal");
        else goalStatus.setText("Goal: " + g + (board.goalIsFact() ? "   ✔ already in the fact base" : "   ✘ not in the fact base"));
    }

    private void start() {
        String goal = board.goalLabel();
        if (goal == null) {
            JOptionPane.showMessageDialog(this, "Set a node as Goal first: click a node and choose Goal.");
            return;
        }
        stopPlay();
        ExpertSystem sys = board.buildSystem();
        steps = new ArrayList<>();
        if (forward.isSelected()) ForwardChaining.prove(sys, goal, steps::add);
        else BackwardChaining.prove(sys, goal, steps::add);
        goTo(1);
    }

    private void resetReasoning() {
        stopPlay();
        steps = new ArrayList<>();
        goTo(0);
    }

    private void goTo(int n) {
        stepIndex = Math.max(0, Math.min(n, steps.size()));
        board.showSteps(steps, stepIndex);
        boolean more = stepIndex < steps.size();
        prev.setEnabled(stepIndex > 0);
        next.setEnabled(more);
        play.setEnabled(more);
    }

    private void togglePlay() {
        if (timer.isRunning()) {
            stopPlay();
        } else if (stepIndex < steps.size()) {
            timer.start();
            play.setText("Pause");
        }
    }

    private void tick() {
        goTo(stepIndex + 1);
        if (stepIndex >= steps.size()) stopPlay();
    }

    private void stopPlay() {
        timer.stop();
        play.setText("Play");
    }
}