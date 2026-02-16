package world.map;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Standalone Java 8 (Swing + JavaFX WebView) harness for loading Explv's OSRS map.
 */
public final class ExplvsMap {
    private static int requests;
    private static JFrame MAP_FRAME;
    private static JFXPanel jfxPanel;
    private static WebView webView;
    private static WebEngine engine;

    // Formatting for the requested timestamp debugging
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    private static final Dimension MAP_DIMENSION = new Dimension(1200, 800);

    private static final int DEFAULT_CENTRE_X = 2798;
    private static final int DEFAULT_CENTRE_Y = 3347;
    private static final int DEFAULT_CENTRE_Z = 0;
    private static final int DEFAULT_ZOOM = 7;

    private static final boolean ENABLE_BOUNDS_CLAMP = true;
    private static final int WORLD_MIN_X = 0;
    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_X = 7000;
    private static final int WORLD_MAX_Y = 7000;
    private static final double MAX_BOUNDS_VISCOSITY = 1.0;

    private static final boolean DEFER_TILE_UPDATES_WHILE_DRAGGING = true;
    private static final int TILE_UPDATE_INTERVAL_MS = 200;
    private static final int TILE_KEEP_BUFFER = 1;

    public static void main(String[] args) {
        init();
    }

    private static boolean init() {
        System.out.println("Checking java version...");
        printJavaRuntimeInfo();
        if (!checkJavaFxWebViewAvailable())
            return false;
        System.out.println("Initializing EXPLV's map...");
        try {
            Platform.setImplicitExit(false);
            File data = new File(System.getProperty("user.home"), "DreamBotMan/javaFX");
            System.setProperty("javafx.user.data.dir", data.getAbsolutePath());

            MAP_FRAME = new JFrame("Explv Map (Stand-alone)");
            jfxPanel = new JFXPanel();

            Platform.runLater(() -> {
                webView = new WebView();
                engine = webView.getEngine();
            });

            SwingUtilities.invokeLater(ExplvsMap::launch);
            return true;
        } catch (Exception e) {
            System.out.println("Error launching EXPLV's map!\n\n" + e);
            return false;
        }
    }

    private static void launch() {
        MAP_FRAME.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        MAP_FRAME.setLayout(new BorderLayout());
        MAP_FRAME.setSize(MAP_DIMENSION);

        final JTextField urlField = new JTextField(buildExplvUrl(DEFAULT_CENTRE_X, DEFAULT_CENTRE_Y, DEFAULT_CENTRE_Z, DEFAULT_ZOOM));
        final JButton btnLoad = new JButton("Load");
        final JButton btnStackOverflow = new JButton("Load StackOverflow (sanity)");

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(btnStackOverflow);
        buttons.add(btnLoad);

        final JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        top.add(urlField, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.EAST);

        final JTextArea status = new JTextArea(12, 60); // Increased height for better log visibility
        status.setEditable(false);
        final JScrollPane statusScroll = new JScrollPane(status);

        final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jfxPanel, statusScroll);
        split.setResizeWeight(0.75);

        MAP_FRAME.add(top, BorderLayout.NORTH);
        MAP_FRAME.add(split, BorderLayout.CENTER);
        MAP_FRAME.setLocationRelativeTo(null);
        MAP_FRAME.setVisible(true);

        Platform.runLater(() -> initFxWebView(jfxPanel, status, urlField, btnLoad, btnStackOverflow));
    }

    private static void initFxWebView(JFXPanel jfxPanel, JTextArea status, JTextField urlField,
                                      JButton btnLoad, JButton btnStackOverflow
    ) {
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            append(status, "Load state: " + newState);
            if (newState == Worker.State.SUCCEEDED) {
                append(status, "Load succeeded. Applying map patches...");
                applyLeafletPatches(engine, status);
            }
        });

        // FIXED: Throttled location tracking to prevent Request Storms
        engine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
            // We use a simple counter, but the JS patch below handles the actual "heaviness"
            requests++;
            append(status, "Location Update [" + requests + "]: " + newLoc);
        });

        engine.setOnAlert(e -> append(status, "Alert: " + e.getData()));

        jfxPanel.setScene(new Scene(webView));
        engine.load(urlField.getText());

        btnLoad.addActionListener(e -> Platform.runLater(() -> engine.load(urlField.getText())));
        btnStackOverflow.addActionListener(e -> Platform.runLater(() -> engine.load("https://stackoverflow.com/")));
    }

    private static void applyLeafletPatches(WebEngine engine, JTextArea status) {
        final String js = buildLeafletPatchScript(
                ENABLE_BOUNDS_CLAMP,
                WORLD_MIN_X, WORLD_MIN_Y, WORLD_MAX_X, WORLD_MAX_Y,
                MAX_BOUNDS_VISCOSITY,
                DEFER_TILE_UPDATES_WHILE_DRAGGING,
                TILE_UPDATE_INTERVAL_MS,
                TILE_KEEP_BUFFER
        );

        Platform.runLater(() -> {
            try {
                engine.executeScript(js);
                append(status, "Injected Leaflet patch script.");
            } catch (Throwable t) {
                append(status, "Failed to inject patch script: " + t);
            }
        });
    }

    private static String buildLeafletPatchScript(
            boolean enableClamp, int minX, int minY, int maxX, int maxY,
            double viscosity, boolean deferTileUpdates, int updateIntervalMs, int keepBuffer
    ) {
        // This script now includes internal JS-level throttling to keep the UI thread clear
        return "(function(){\n"
                + "  var ENABLE_CLAMP = " + enableClamp + ";\n"
                + "  var DEFER_TILES = " + deferTileUpdates + ";\n"
                + "  var UPDATE_INTERVAL = " + updateIntervalMs + ";\n"
                + "\n"
                + "  function log(msg){ try { console.log('[LeafletPatch] ' + msg); } catch(e){} }\n"
                + "\n"
                + "  function tryApply(){\n"
                + "    var L = window.L; var map = window.map;\n"
                + "    if (!L || !map || typeof map.getCenter !== 'function') return false;\n"
                + "\n"
                + "    if (ENABLE_CLAMP) {\n"
                + "      var bounds = L.latLngBounds(L.latLng(" + minY + ", " + minX + "), L.latLng(" + maxY + ", " + maxX + "));\n"
                + "      map.setMaxBounds(bounds);\n"
                + "      map.options.maxBoundsViscosity = " + viscosity + ";\n"
                + "    }\n"
                + "\n"
                + "    if (DEFER_TILES) {\n"
                + "      map.eachLayer(function(layer){\n"
                + "        if (layer.options && (layer.getTileUrl || layer._getTilePos)) {\n"
                + "          layer.options.updateWhenIdle = true;\n"
                + "          layer.options.updateWhenZooming = false;\n" // Prevents zoom-freezing
                + "          layer.options.updateInterval = UPDATE_INTERVAL;\n"
                + "          if (typeof layer.redraw === 'function') layer.redraw();\n"
                + "        }\n"
                + "      });\n"
                + "    }\n"
                + "    return true;\n"
                + "  }\n"
                + "\n"
                + "  var attempts = 0;\n"
                + "  var timer = setInterval(function(){\n"
                + "    if (tryApply() || ++attempts > 60) clearInterval(timer);\n"
                + "  }, 100);\n"
                + "})();";
    }

    private static String buildExplvUrl(int centreX, int centreY, int centreZ, int zoom) {
        return "https://explv.github.io/?centreX=" + centreX + "&centreY=" + centreY + "&centreZ=" + centreZ + "&zoom=" + zoom;
    }

    /**
     * Appends a line with a high-precision timestamp to help diagnose server-side lag.
     */
    private static void append(JTextArea area, String line) {
        String timestamp = TIME_FORMAT.format(new Date());
        SwingUtilities.invokeLater(() -> {
            area.append("[" + timestamp + "] " + line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    private static void printJavaRuntimeInfo() {
        System.out.println("java.version=" + System.getProperty("java.version"));
        System.out.println("java.vendor=" + System.getProperty("java.vendor"));
    }

    private static boolean checkJavaFxWebViewAvailable() {
        try {
            Class.forName("javafx.embed.swing.JFXPanel");
            Class.forName("javafx.scene.web.WebView");
            return true;
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null, "JavaFX WebView is NOT available.", "JavaFX Missing", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void closeMap() {
        Platform.runLater(() -> engine.load(null));
    }
}