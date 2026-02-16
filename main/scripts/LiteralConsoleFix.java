package main.scripts;

import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

import javax.swing.*;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;

@ScriptManifest(category = Category.UTILITY, name = "Literal Console Fix", author = "Gemini", version = 5.0)
public class LiteralConsoleFix extends AbstractScript {

    private JFrame frame;
    private JTextArea textArea;
    private PrintStream originalOut;

    @Override
    public void onStart() {
        // Build GUI on the Swing Thread to prevent the "Grey Box" issue
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("DreamBot Console Mirror");
            frame.setLayout(new BorderLayout());
            frame.setSize(600, 400);

            textArea = new JTextArea();
            textArea.setBackground(Color.BLACK);
            textArea.setForeground(Color.WHITE);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            textArea.setEditable(false);

            JScrollPane scroll = new JScrollPane(textArea);
            frame.add(scroll, BorderLayout.CENTER);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Redirect System.out to the JTextArea
            originalOut = System.out;
            System.setOut(new PrintStream(new CustomStream(textArea, originalOut)));

            log("Capture Started.");
        });
    }

    @Override
    public int onLoop() {
        return 1000;
    }

    @Override
    public void onExit() {
        if (originalOut != null) {
            System.setOut(originalOut);
        }
        if (frame != null) {
            frame.dispose();
        }
    }

    private static class CustomStream extends OutputStream {
        private final JTextArea display;
        private final PrintStream stdout;

        public CustomStream(JTextArea display, PrintStream stdout) {
            this.display = display;
            this.stdout = stdout;
        }

        @Override
        public void write(int b) {
            stdout.write(b);
            SwingUtilities.invokeLater(() -> {
                display.append(String.valueOf((char) b));
                display.setCaretPosition(display.getDocument().getLength());
            });
        }
    }
}