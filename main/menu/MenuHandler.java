package main.menu;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

public class MenuHandler {
    static final Color COLOR_BTN_BACKGROUND = new Color(40,40,40);
    static final Color COLOR_BTN_FOREGROUND = Color.WHITE;

    public static final Color COLOR_SUBTITLE = new Color(193, 93, 13);

    static final Color COLOR_BORDER_DIM = new Color(45, 45, 45);

    static final Color PANEL_SURFACE = new Color(24, 24, 24);
    static final Color TEXT_MAIN = new Color(210, 210, 210);

    static final Color COLOR_BLOOD = new Color(150, 0, 0);

    static final Color COLOR_BTN_ADD = new Color(220, 80, 0);

    static JButton createButton(@NotNull String btnText) {
        return createButton(btnText, null, null);
    }

    public static JPanel createPanel() {
        return styleComp(new JPanel(new BorderLayout()));
    }

    public static JLabel createLabel(@NotNull String text) {
        return styleComp(new JLabel(text));
    }

    public static JButton createButton(@NotNull String btnText, Color backgroundColor, Color foregroundColor) {
        // validate button text
        if (btnText.isEmpty())
            throw new IllegalArgumentException("Error creating button! Button text cannot be empty");

        // create button object
        JButton btn = new JButton(btnText);
        // set button properties
        btn.setBackground(backgroundColor == null ? COLOR_BTN_BACKGROUND : backgroundColor);
        btn.setForeground(foregroundColor == null ? COLOR_BTN_FOREGROUND : foregroundColor);
        btn.setFocusPainted(false);
        btn.setBorder(new LineBorder(COLOR_BORDER_DIM));

        return btn;
    }

    /**
     * Creates and returns a panel containing all the necessary features for an informative parameter control.
     * @return
     */
    public static JPanel createParameterPanel(String title, String description, JComponent input, String example) {
        JPanel targetTitle = createParamTitle(title);
        JTextArea targetDescription = createParamDescription(description);
        JTextArea targetExample = createParamExample(example);

        JPanel parameterPanel = new JPanel();
            parameterPanel.setLayout(new BoxLayout(parameterPanel, BoxLayout.Y_AXIS));
            parameterPanel.setOpaque(false);
            parameterPanel.add(targetTitle, BorderLayout.WEST);
            parameterPanel.add(targetDescription, BorderLayout.WEST);
            parameterPanel.add(Box.createVerticalStrut(10));
            parameterPanel.add(input, BorderLayout.WEST);
            parameterPanel.add(Box.createVerticalStrut(4));
            parameterPanel.add(targetExample, BorderLayout.WEST);
            parameterPanel.add(Box.createVerticalStrut(10));

        styleComp(parameterPanel);

        return parameterPanel;
    }

    public static JPanel createParamTitle(String rawTitle) {
        ///  Create the subtitle label (text)
        JLabel paramLabel = new JLabel(rawTitle);
            paramLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            paramLabel.setForeground(COLOR_SUBTITLE);

        JPanel paramPanel = new JPanel(new GridLayout(1, 1, 0, 0));
            paramPanel.setOpaque(false);
            paramPanel.add(paramLabel, BorderLayout.WEST);
            paramPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        return paramPanel;
    }

    public static JLabel createSubtitle(String subtitle) {
        JLabel title = new JLabel(subtitle, SwingConstants.CENTER);
        title.setForeground(COLOR_BLOOD);
        title.setFont(new Font("Consolas", Font.BOLD, 22));
        title.setBorder(BorderFactory.createMatteBorder(0, 5, 0, 0, COLOR_BLOOD));

        return title;
    }

    public static JTextArea createParamDescription(@NotNull String rawDescription) {
        JTextArea description = new JTextArea(rawDescription);
            description.setWrapStyleWord(true);
            description.setLineWrap(true);
            description.setEditable(false);
            description.setFocusable(false);
            description.setOpaque(false);
            description.setBackground(new Color(0,0,0,0)); // Matches parent background
            description.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
            description.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 2));

        return description;
    }

    public static JTextArea createParamExample(@NotNull String rawExample) {
        JTextArea example = new JTextArea(rawExample);
            example.setWrapStyleWord(true);
            example.setLineWrap(true);
            example.setEditable(false);
            example.setFocusable(false);
            example.setOpaque(false);
            example.setBackground(new Color(0,0,0,0)); // Matches parent background
            example.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 9));
            example.setBorder(BorderFactory.createEmptyBorder(2, 10, 0, 2));
            example.setAlignmentX(Component.CENTER_ALIGNMENT);

        return example;
    }

    public static <T extends JComponent> T styleComp(T dynamicComponent) {
        dynamicComponent.setBackground(PANEL_SURFACE);
        dynamicComponent.setForeground(TEXT_MAIN);

        if (dynamicComponent instanceof JTextField)
            ((JTextField) dynamicComponent).setCaretColor(COLOR_BLOOD);

        for (Component child : dynamicComponent.getComponents()) {
            if (child instanceof JComponent)
                styleComp((JComponent) child);
        }
        
        return dynamicComponent;
    }
}
