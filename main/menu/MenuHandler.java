package main.menu;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionListener;

public class MenuHandler {
    static final Color COLOR_BTN_BACKGROUND = new Color(40,40,40);
    static final Color COLOR_BTN_FOREGROUND = Color.WHITE;

    static final Color COLOR_SUBTITLE = Theme.TEXT_DIM;

    public static final Color COLOR_BORDER_DIM = Theme.BORDER;

    static final Color PANEL_SURFACE = Theme.SURFACE_1;
    public static final Color TEXT_MAIN = Theme.TEXT;

    public static final Color COLOR_BLOOD = Theme.ACCENT;

    static final Color COLOR_BTN_ADD = Theme.ACCENT;

    static final Color COLOR_NPC  = new Color(60,  120, 200);
    static final Color COLOR_OBJ  = new Color(40,  160,  70);
    static final Color COLOR_ITEM = new Color(200, 130,  40);

    static JButton createButton(String btnText) {
        return createButton(btnText, null, null);
    }

    static JButton createIconButton(String symbol, String tooltip, ActionListener action) {
        JButton btn = new JButton(symbol);
            btn.setPreferredSize(new Dimension(40, 40));
            btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18));
            btn.setBackground(new Color(30, 0, 0));
            btn.setForeground(COLOR_BLOOD);
            btn.addActionListener(action);
            btn.setToolTipText(tooltip); // TODO force tooltip onto normal buttons and go through and apply one

        return btn;
    }

    static JPanel createPanel() {
        return styleComp(new JPanel(new BorderLayout()));
    }

    static JPanel createPanelBorderLayout(int hgap, int vgap) {
        return styleComp(new JPanel(new BorderLayout(hgap, vgap)));
    }

    static JPanel createPanelGridBagLayout() {
        return styleComp(new JPanel(new GridBagLayout()));
    }

    static JLabel createLabel(String text) {
        return styleComp(new JLabel(text));
    }

    static JButton createButton(String btnText, Color backgroundColor, Color foregroundColor) {
        // validate button text
        if (btnText.isEmpty())
            throw new IllegalArgumentException("Error creating button! Button text cannot be empty");

        // create button object
        JButton btn = new JButton(btnText);
        btn.setFocusPainted(false);
        // Semantic colour (e.g. red Remove, green Add) paints as a FILLED button via the flat UI;
        // a null colour leaves it as a quiet ghost button. (Border/paint handled by Theme.FlatButtonUI.)
        if (backgroundColor != null)
            btn.putClientProperty("fillColor", backgroundColor);
        if (foregroundColor != null)
            btn.setForeground(foregroundColor);

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

    static JPanel createParamTitle(String rawTitle) {
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

    static JLabel createSubtitle(String subtitle) {
        JLabel title = new JLabel(subtitle, SwingConstants.CENTER);
        title.setForeground(COLOR_BLOOD);
        title.setFont(new Font("Consolas", Font.BOLD, 22));
        title.setBorder(BorderFactory.createMatteBorder(0, 5, 0, 0, COLOR_BLOOD));

        return title;
    }

    static JTextArea createParamDescription(String rawDescription) {
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

    static JTextArea createParamExample(String rawExample) {
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
        dynamicComponent.setOpaque(false);

        if (dynamicComponent instanceof JTextField)
            ((JTextField) dynamicComponent).setCaretColor(COLOR_BLOOD);

        for (Component child : dynamicComponent.getComponents()) {
            if (child instanceof JComponent)
                styleComp((JComponent) child);
        }
        
        return dynamicComponent;
    }










    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_SURFACE);
        header.setPreferredSize(new Dimension(0, 85));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER_DIM));
        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.setOpaque(false);
        rightContainer.setBorder(new EmptyBorder(0, 0, 0, 20));
        return header;
    }
}
