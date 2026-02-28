package main.menu;

import main.menu.components.JLibraryList;

import javax.swing.*;
import java.awt.*;

public class DevelopersConsole extends JPanel {
    private final JTabbedPane devTabs = new JTabbedPane();

    public DevelopersConsole() {
        devTabs.addTab("Main", new JPanel());
        devTabs.addTab("Library List", new JLibraryList());
        devTabs.addTab("Library List 2", new JLibraryList2());

        this.add(devTabs, BorderLayout.CENTER);
    }
}

