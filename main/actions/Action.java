package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.map.Tile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.awt.*;
import java.util.Map;

import static main.menu.MenuHandler.*;

public abstract class Action {
    public JParamTextField paramTarget;

    ///
    ///     Every subclass must implement these:
    ///
    public abstract boolean execute();
    public abstract String getParamTarget();
    public abstract Action copy();
    public abstract Map<String, String> serialize();
    public abstract void deserialize(Map<String, String> data);

    ///
    ///     Constructor
    ///
    public Action() {
        super();
    }

    ///
    ///     Getters/setters
    ///
    public String getName() {
        return this.getClass().getSimpleName();
    }

    ///  Getters/setters
    public Class<? extends Action> getType() {
        return this.getClass();
    }

    /**
     * Returns a JPanel containing both the compulsory AND dynamic controls for the selected {@link Action} in the
     * {@link main.menu.components.JActionSelector}.
     */
    public JPanel getParamPanel() {
        JPanel paramPanel = new JPanel(new BorderLayout());
        JPanel dynamicPanel = createParamPanel();

        paramPanel.add(dynamicPanel, BorderLayout.CENTER);

        styleComp(paramPanel);

        return paramPanel;
    }

    public abstract JPanel createParamPanel();

    ///  Helper functions
    public static Tile parseStringIntoTile(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        // Remove any characters that aren't digits, commas, or spaces
        // This allows for formats like "(3222, 3218)" or "3222 3218 0"
        String cleaned = input.replaceAll("[^0-9, ]", "").trim();

        // Split by comma or space
        String[] parts = cleaned.split("[, ]+");

        try {
            if (parts.length == 2) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                if (isValid(x, y, 0)) {
                    return new Tile(x, y, 0);
                }

            } else if (parts.length == 3) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);

                if (isValid(x, y, z)) {
                    return new Tile(x, y, z);
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return null;
    }

    private static boolean isValid(int x, int y, int z) {
        return x >= 0 && x <= 16383 &&
                y >= 0 && y <= 16383 &&
                z >= 0 && z <= 3;
    }

    public String toBuildString() {
        return this + " → " + getParamTarget();
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName();
    }
}