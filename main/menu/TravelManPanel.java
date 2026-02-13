//package main.menu;
//
//import javafx.application.Platform;
//import javafx.concurrent.Worker;
//import javafx.embed.swing.JFXPanel;
//import javafx.scene.Scene;
//import javafx.scene.web.WebEngine;
//import javafx.scene.web.WebView;
//import main.BotMan;
//import main.BotMenu;
//
//import javax.swing.*;
//import java.awt.*;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//
//public final class TravelManPanel extends JPanel {
//    /**
//     * The default webview (map) size
//     */
//    private final Dimension MAP_DIMENSION = new Dimension(1200, 800);
//    /**
//     * The 'x' coordinate used in cases where no other value is provided.
//     */
//    private final int DEFAULT_X = 2798;
//    /**
//     * The 'y' coordinate used in cases where no other value is provided.
//     */
//    private final int DEFAULT_Y = 3347;
//    /**
//     * The 'z' coordinate used in cases where no other value is provided.
//     */
//    private final int DEFAULT_Z = 0;
//    /**
//     * The zoom value used in cases where no other value is provided
//     */
//    private final int DEFAULT_ZOOM = 7;
//    /**
//     * Set default timestamp format to HH:mm:ss.SSS
//     */
//    private final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
//    /**
//     * The {@link BotMan} instance associated with this {@link BotMenu}'s Travel Manager.
//     */
//    private final BotMan bot;
//    /**
//     * The current zoom level of the map as adjusted by the player.
//     */
//    private int zoom;
//    /**
//     * create a status area for debugging information
//     */
//    //TODO delete this once TravelMan is completed
//    final JTextArea status = new JTextArea(12, 60);
//    /**
//     * Java FX panel used to hold a web-view to display web-pages such as Explv's map.
//     */
//    private final JFXPanel jfxPanel = new JFXPanel();
//    //TODO delete url bar before release
//    private JTextField urlBar;
//    private JButton btnLoad;
//    private JButton btnLoadOther;
//    /**
//     * Web-view object used to display web-pages such as Explv's map.
//     */
//    private WebView webView;
//    /**
//     * The engine used to load web-pages inside a web-view.
//     */
//    private WebEngine engine;
//
//    /**
//     * Constructs a Travel Manager panel for the {@link BotMenu}. The Travel Manager can be used to easily travel
//     * between locations; add, remove or edit existing locations in the location library or to track the players current
//     * locations in x, y, z coordinate format.
//     *
//     * @param bot
//     */
//    public TravelManPanel(BotMan bot) {
//        super(new BorderLayout(12, 12));
//        this.bot = bot;
//
//        bot.setBotStatus("Initializing Travel Manager...");
//
//        // ensures the FULL java 8 package is installed by checking for java FX and webview components
//        if (!checkJavaFxWebViewAvailable())
//            throw new RuntimeException("Unable to find TravelMan's necessary java components... The FULL Java 8 JRE package is required for this feature!");
//
//        // construct the travel man (before initializing fx components, so they have somewhere to go)
//        if (!buildSwingUi())
//            throw new RuntimeException("Unable to build TravelMan swing components...");
//
//        // initialize the java fx components required to load a map
//        if (!initFx())
//            throw new RuntimeException("Unable to initialize TravelMan FX components...");
//    }
//
//    private boolean checkJavaFxWebViewAvailable() {
//        try {
//            printJavaRuntimeInfo();
//            bot.log("Checking java components...");
//            // check for java FX (JFX)
//            Class.forName("javafx.embed.swing.JFXPanel");
//            bot.log("Found JFX!");
//            // check for JFX web view
//            Class.forName("javafx.scene.web.WebView");
//            bot.log("Found WebView!");
//            // only return true if both are found
//            return true;
//
//        } catch (ClassNotFoundException e) {
//            JOptionPane.showMessageDialog(null, "JavaFX WebView is NOT available.", "JavaFX Missing", JOptionPane.ERROR_MESSAGE);
//            return false;
//        }
//    }
//
//    private boolean buildSwingUi() {
//        try {
//            bot.log("Building TravelMan tab UI...");
//            // set a recommended size for the jfx panel (map panel)
//            jfxPanel.setPreferredSize(MAP_DIMENSION);
//            // prevent user input
//            status.setEditable(false);
//
//            // create a scrollable status panel for debug output
//            //TODO delete this later?
//            JScrollPane statusScroll = new JScrollPane(status);
//
//            // add the map and status scroll panel to a split panel
//            //TODO delete this later?
//            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jfxPanel, statusScroll);
//            // force resize to make the map take up 3/4 of the space by default
//            split.setResizeWeight(0.75);
//
//            add(buildTopSection(), BorderLayout.NORTH);
//            add(split, BorderLayout.CENTER);
//
//            //        revalidate();
//            //        repaint();
//            return true;
//        } catch (Exception e) {
//            bot.log(e);
//            return false;
//        }
//    }
//
//    //TODO remove the whole top section later
//    private JPanel buildTopSection() {
//        urlBar = new JTextField(buildUrl(getMapX(), getMapY(), getMapZ(), getMapZoom()));
//
//        // create buttons to load the url bar or chat gpt
//        //TODO remove later
//        btnLoad = new JButton("Load");
//        btnLoad.addActionListener(e -> Platform.runLater(() -> {
//            if (engine != null)
//                engine.load(urlBar.getText());
//        }));
//
//        //TODO remove later
//        btnLoadOther = new JButton("Load AI");
//        btnLoadOther.addActionListener(e -> Platform.runLater(() -> {
//            if (engine != null)
//                engine.load("https://chatgpt.com/");
//        }));
//
//        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
//        buttons.add(btnLoad);
//        buttons.add(btnLoadOther);
//
//        JPanel top = new JPanel(new BorderLayout(8, 8));
//        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
//        top.add(urlBar, BorderLayout.CENTER);
//        top.add(buttons, BorderLayout.EAST);
//
//        return top;
//    }
//
//    private boolean initFx() {
//        try {
//            // create webview, engine and scene
//            Platform.runLater(() -> {
//                webView = new WebView();
//                engine = webView.getEngine();
//
//                engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
//                    if (newState == Worker.State.SUCCEEDED)
//                        append("Loaded: " + engine.getLocation());
//                    else if (newState == Worker.State.RUNNING)
//                        append("Loading...");
//                    else if (newState == Worker.State.FAILED)
//                        append("Load FAILED");
//                });
//
//                jfxPanel.setScene(new Scene(webView));
//                // get engine to load url bar text by default
//                //TODO adjust this once necessary removals made
//                engine.load(urlBar.getText());
//            });
//
//            return true;
//
//        } catch (Throwable t) {
//            bot.log(t);
//            return false;
//        }
//    }
//
//    private void printJavaRuntimeInfo() {
//        bot.log("Checking java version...");
//        bot.log("java.version=" + System.getProperty("java.version"));
//        bot.log("java.vendor=" + System.getProperty("java.vendor"));
//    }
//
//    private void append(String debugMsg) {
//        String timestamp = TIME_FORMAT.format(new Date());
//        SwingUtilities.invokeLater(() -> {
//            // append this debug message to the status section
//            status.append("[" + timestamp + "] " + debugMsg + "\n");
//            // move mouse caret to the new last line
//            status.setCaretPosition(status.getDocument().getLength());
//        });
//    }
//
//    /**
//     * Ensure proper disposal of the JFX components on this panel.
//     */
//    public void dispose() {
//        javafx.application.Platform.runLater(() -> {
//            try {
//                // stops page / frees doc
//                if (engine != null) engine.load(null);
//                // detach scene from Swing host
//                jfxPanel.setScene(null);
//
//            } catch (Throwable ignored) {}
//
//            webView = null;
//            engine = null;
//        });
//    }
//
//
//    private String buildUrl(int x, int y, int z, int zoom) {
//        return "https://explv.github.io/?centreX=" + x + "&centreY=" + y + "&centreZ=" + z + "&zoom=" + zoom;
//    }
//
//    private int getMapX() {
//        return bot == null ? DEFAULT_X : bot.getX();
//    }
//
//    private int getMapY() {
//        return bot == null ? DEFAULT_Y : bot.getY();
//    }
//
//    private int getMapZ() {
//        return bot == null ? DEFAULT_Z : bot.getZ();
//    }
//
//    private int getMapZoom() {
//        return zoom < 1 ? DEFAULT_ZOOM : zoom;
//    }
//
//}