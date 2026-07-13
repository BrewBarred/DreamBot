package main.menu;

import main.data.library.JLibrary;
import main.data.library.LibraryPanel;
import main.menu.components.JLibraryList;

import javax.swing.*;
import java.awt.*;

public class DevelopersConsole extends JPanel {
    private final JTabbedPane devTabs = new JTabbedPane();

    public DevelopersConsole(LibraryPanel library) {
        setLayout( new BorderLayout());
        devTabs.addTab("Main", new JPanel());
        devTabs.addTab("Library List", new JLibraryList());
        devTabs.addTab("Library Panel (Beta)", library);

        this.add(devTabs, BorderLayout.CENTER);
    }
}

