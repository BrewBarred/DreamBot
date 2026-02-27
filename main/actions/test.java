package main.actions;

import main.menu.JActionSelector;

import javax.swing.*;
import java.awt.*;

public class test extends JFrame {
    private final JActionSelector actionHandler;
    private final JPanel settingsPanel;
    private final JButton addButton;
    private final DefaultListModel<String> actionListModel;

    public test() {
        setTitle("Action Builder");
        setLayout(new BorderLayout(10, 10));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(400, 300);

        // 1. Top Section: Selection
        actionHandler = new JActionSelector();

        // 2. Middle Section: Dynamic Settings Panel
        settingsPanel = new JPanel(new CardLayout());
        // In a real scenario, you'd loop your REGISTRY to add panels here
        // For now, we manually add the panels defined in your Action subclasses
        settingsPanel.add(new JPanel(), "Empty");

        // 3. Bottom Section: Controls
        addButton = new JButton("Add to Builder");
        actionListModel = new DefaultListModel<>();
        JList<String> activeActions = new JList<>(actionListModel);

        // Layout Assembly
        JPanel topPanel = new JPanel(new GridLayout(0, 1));
        topPanel.add(new JLabel("Select Action:"));
        topPanel.add(actionHandler);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(activeActions), BorderLayout.CENTER);
        add(addButton, BorderLayout.SOUTH);

        // Logic: Button Click
        addButton.addActionListener(e -> {
            Action action = actionHandler.build();

            if (action != null) {
                actionListModel.addElement(action.toString());
                System.out.println("Added: " + action.getClass().getSimpleName());
            }
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        // Run in the Event Dispatch Thread
        SwingUtilities.invokeLater(test::new);
    }
}
