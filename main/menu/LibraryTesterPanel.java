package main.menu;

import main.data.Library;
import main.data.Library.TargetType;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class LibraryTesterPanel extends JPanel {

    private final JTextField searchField;
    private final DefaultListModel<String> listModel;
    private final JList<String> resultList;
    private final JCheckBox npcBox, objBox, groundBox;

    public LibraryTesterPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(45, 45, 45)); // Dark theme

        // --- Top: Search and Filters ---
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setOpaque(false);

        searchField = new JTextField();
        searchField.setToolTipText("Search library entries...");

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setOpaque(false);

        npcBox = new JCheckBox("NPCs", true);
        objBox = new JCheckBox("Objects", true);
        groundBox = new JCheckBox("Ground Spawns", true);

        // Style checkboxes for dark theme
        for (JCheckBox cb : new JCheckBox[]{npcBox, objBox, groundBox}) {
            cb.setForeground(Color.WHITE);
            cb.setOpaque(false);
            cb.addActionListener(e -> updateSearch());
        }

        searchField.addActionListener(e -> updateSearch());

        filterPanel.add(npcBox);
        filterPanel.add(objBox);
        filterPanel.add(groundBox);

        topPanel.add(searchField);
        topPanel.add(filterPanel);

        // --- Center: Results ---
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setBackground(new Color(30, 30, 30));
        resultList.setForeground(Color.CYAN);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(resultList), BorderLayout.CENTER);

        // --- Bottom: Actions ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setOpaque(false);

        JButton walkBtn = new JButton("Walk to Selected");
        JButton distBtn = new JButton("Check Distance");

        walkBtn.addActionListener(e -> {
            String selected = resultList.getSelectedValue();
            if (selected != null) {
                String cleanName = selected.split(": ")[1];
                Tile target = Library.resolveToTile(cleanName);
                if (target != null) {
                    Walking.walk(target);
                    Logger.log("Walking to: " + cleanName);
                }
            }
        });

        distBtn.addActionListener(e -> {
            String selected = resultList.getSelectedValue();
            if (selected != null) {
                String cleanName = selected.split(": ")[1];
                Tile target = Library.resolveToTile(cleanName);
                if (target != null) {
                    int dist = Library.getDistanceToTarget(target.getX() + ", " + target.getY() + ", " + target.getZ());
                    Logger.log("Distance to " + cleanName + ": " + dist + " tiles");
                }
            }
        });

        bottomPanel.add(distBtn);
        bottomPanel.add(walkBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void updateSearch() {
        listModel.clear();
        String query = searchField.getText();

        List<TargetType> types = new ArrayList<>();
        if (npcBox.isSelected()) types.add(TargetType.NPC);
        if (objBox.isSelected()) types.add(TargetType.GAME_OBJECT);
        if (groundBox.isSelected()) types.add(TargetType.GROUND_ITEM);

        List<String> results = Library.searchLibrary(query, types.toArray(new TargetType[0]));
        for (String res : results) {
            listModel.addElement(res);
        }
    }
}