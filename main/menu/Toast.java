package main.menu;

import javax.swing.*;
import java.awt.*;

public class Toast extends JPanel {
    private float opacity = 1.0f;
    private final Color TOAST_RED = new Color(150, 0, 0);
    private final Color TOAST_BG = new Color(12, 12, 12, 220);

    public Toast(String message, int x, int y) {
        setLayout(new BorderLayout());
        setOpaque(false); // Critical for transparency

        JLabel lbl = new JLabel(message);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        add(lbl);

        setSize(getPreferredSize());
        // Spawn slightly lower and float up into position
        setLocation(x - (getWidth() / 2), y + 10);

        Timer timer = new Timer(25, null);
        timer.addActionListener(e -> {
            opacity -= 0.03f;
            setLocation(getX(), getY() - 1); // Drift upwards

            if (opacity <= 0f) {
                timer.stop();
                if (getParent() != null) {
                    getParent().remove(this);
                    getParent().repaint();
                }
            } else {
                repaint();
            }
        });
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Apply the fade
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

        // Draw the "Pill" background
        g2d.setColor(TOAST_BG);
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

        // Draw the blood-red border
        g2d.setColor(TOAST_RED);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);

        g2d.dispose();
    }
}