import javax.swing.*;
import java.awt.*;

public class MainGUI extends JFrame {

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    public MainGUI() {
        super("IA & ML - Assignment 1");
        root.add(menuPanel(), "menu");
        root.add(new ExpertSystemPanel(() -> cards.show(root, "menu")), "expert");
        add(root);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
    }

    private JPanel menuPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        JPanel box = new JPanel(new GridLayout(0, 1, 0, 12));
        box.setPreferredSize(new Dimension(300, 220));

        JLabel title = new JLabel("Main Menu", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));

        JButton expert = new JButton("Expert Systems");
        JButton search = new JButton("Search Algorithms");
        JButton quit = new JButton("Quit");

        expert.addActionListener(e -> cards.show(root, "expert"));
        search.addActionListener(e -> JOptionPane.showMessageDialog(this, "Search Algorithms module coming next."));
        quit.addActionListener(e -> System.exit(0));

        box.add(title);
        box.add(expert);
        box.add(search);
        box.add(quit);
        p.add(box);
        return p;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainGUI().setVisible(true));
    }
}