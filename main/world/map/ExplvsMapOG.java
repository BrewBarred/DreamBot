package main.world.map;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Standalone Java 8 (Swing + JavaFX WebView) harness for loading Explv's OSRS map.
 * <p>
 * This class focuses on two performance/UX fixes that address the symptoms you described:
 * <ol>
 *   <li><b>Clamp bounds</b> so the user cannot drag infinitely into black space.</li>
 *   <li><b>Defer tile updates while dragging</b> so panning stays responsive when entering unloaded areas.</li>
 * </ol>
 * <p>
 * Because the lag originates inside the page (Leaflet + tile loading + render), these fixes are applied via
 * <b>JavaScript injection</b> after the map finishes loading.
 */
public final class ExplvsMapOG {
    private static int requests;
    // frame used to load explvs map externally (press button in menu to open in it's own window)
    private static JFrame MAP_FRAME;
    // java fx panel can holds web-view
    private static JFXPanel jfxPanel;

    private static WebView webView;
    private static WebEngine engine;
    /// ----------------------------
    ///  UI / Window sizing
    /// ----------------------------

    private static final Dimension MAP_DIMENSION = new Dimension(1200, 800);

    /// ----------------------------
    ///  Explv URL defaults
    /// ----------------------------

    private static final int DEFAULT_CENTRE_X = 2798;
    private static final int DEFAULT_CENTRE_Y = 3347;
    private static final int DEFAULT_CENTRE_Z = 0;
    private static final int DEFAULT_ZOOM = 7;

    /// ----------------------------
    ///  Bounds clamping configuration (Step 1)
    /// ----------------------------
    /**
     * Enable/disable bounds clamping injection.
     * <p>
     * If you leave this false, the map can be dragged into infinite black space (depending on Leaflet config).
     */
    private static final boolean ENABLE_BOUNDS_CLAMP = true;

    /**
     * OSRS world bounds in the coordinate system used by the map.
     * <p>
     * NOTE: I cannot infer these exact values reliably from your code alone.
     * You must set these to the correct extents for the tile set you are using.
     *
     * How to get the correct values:
     * - Fork/run the map in a modern browser
     * - Pan to the extreme edges of the actual map content
     * - Determine min/max X/Y supported by the tiles/data
     */
    private static final int WORLD_MIN_X = 0;      // TODO: set correctly for your tile set
    private static final int WORLD_MIN_Y = 0;      // TODO: set correctly for your tile set
    private static final int WORLD_MAX_X = 7000;   // TODO: set correctly for your tile set
    private static final int WORLD_MAX_Y = 7000;   // TODO: set correctly for your tile set

    /**
     * How “sticky” the bounds are when dragging against the edge:
     * - 0.0  = can drag outside freely (but springs back)
     * - 1.0  = cannot drag outside at all
     */
    private static final double MAX_BOUNDS_VISCOSITY = 1.0;

    /// ----------------------------
    ///  Drag-loading policy (Step 2)
    /// ----------------------------

    /**
     * If true, attempt to force Leaflet tile layers to update only when the user stops dragging.
     * <p>
     * This reduces the “freeze while dragging into unloaded space” symptom by preventing expensive tile work
     * from running continuously during drag.
     */
    private static final boolean DEFER_TILE_UPDATES_WHILE_DRAGGING = true;

    /**
     * Tile update interval in ms (Leaflet uses this for throttling tile updates in some modes).
     * Larger values may reduce thrash, smaller values may feel more responsive.
     */
    private static final int TILE_UPDATE_INTERVAL_MS = 200;

    /**
     * Keep-buffer controls how many tiles beyond the viewport Leaflet keeps loaded.
     * Smaller = fewer extra loads; larger = smoother at the cost of more loading.
     */
    private static final int TILE_KEEP_BUFFER = 1;

    /// ----------------------------
    ///  App entry point
    /// ----------------------------

    public static void main(String[] args) {
        // ensure proper initialization
        init();
    }

    /**
     * This initialization function ensures the proper initialization of this class. Since this class has a stand-alone
     * menu and javaFX/webview components, changing the order of initialization may cause some features to break.
     */
    private static boolean init() {
        System.out.println("Checking java version...");
        /// --- Basic runtime sanity checks (helps when users install the wrong Java 8) ---
        printJavaRuntimeInfo();
        if (!checkJavaFxWebViewAvailable())
            return false;
        System.out.println("Initializing EXPLV's map...");
        try {
            /// --- Keep FX runtime alive (useful when embedding inside larger apps/menus) ---
            Platform.setImplicitExit(true);

            ///  set data properties and file locations

            // create a data file to store webview credentials for users to prevent manual logins each launch.
            System.out.println("Creating data file...");
            File data = new File(System.getProperty("user.home"), "DreamBotMan/javaFX");

            System.out.println("Setting up data file...");
            System.setProperty("javafx.user.data.dir", data.getAbsolutePath());

            ///  init java fx tools, components and tool-kit (must be done after setting properties)

            System.out.println("Initializing menu components...");
            // frame used to load explvs map externally (press button in menu to open in it's own window)
            MAP_FRAME = new JFrame("Explv Map (Stand-alone)");
            // initialize jfx toolkit (must be done before using java fx components!)
            jfxPanel = new JFXPanel();

            ///  ensure webview is initialized on fx EDT

            Platform.runLater(() -> {
                System.out.println("Initializing web view...");
                // web-view allows http requests to display images etc. and create interactive broswers inside your app
                webView = new WebView();
                // create a web view engine to load or add listeners and properties to the webview
                engine = webView.getEngine();
            });

            /// ensure swing components are built on Swing UI EDT
            SwingUtilities.invokeLater(ExplvsMapOG::launch);

            // return true to signal successful initialization
            return true;

        } catch (Exception e) {
            System.out.println("Error launching EXPLV's map!\n\n" + e);
            // return false to cancel class initialization
            return false;
        }
    }

    /// ----------------------------
    ///  Swing UI
    /// ----------------------------

    /**
     * Creates the Swing window, embeds a {@link JFXPanel}, and initializes the JavaFX {@link WebView} on the FX thread.
     */
    private static void launch() {
        System.out.println("Loading ExplvsMap...");
        MAP_FRAME.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        MAP_FRAME.setLayout(new BorderLayout());
        MAP_FRAME.setSize(MAP_DIMENSION);

        /// --- Top bar: URL field + buttons ---
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

        /// --- Status/log area (Swing) ---
        final JTextArea status = new JTextArea(8, 60);
        status.setEditable(false);
        final JScrollPane statusScroll = new JScrollPane(status);

        /// --- Layout: WebView on top, status below ---
        final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jfxPanel, statusScroll);
        split.setResizeWeight(0.85);

        MAP_FRAME.add(top, BorderLayout.NORTH);
        MAP_FRAME.add(split, BorderLayout.CENTER);

        MAP_FRAME.setLocationRelativeTo(null);
        MAP_FRAME.setVisible(true);

        /// --- Initialize JavaFX WebView on FX thread ---
        Platform.runLater(() -> initFxWebView(jfxPanel, status, urlField, btnLoad, btnStackOverflow));
    }

    /// ----------------------------
    ///  JavaFX WebView init
    /// ----------------------------

    /**
     * Initializes the JavaFX {@link WebView}/{@link WebEngine}, installs diagnostics, and wires Swing buttons.
     * <p>
     * Also applies the two requested performance steps by injecting JavaScript after load succeeds:
     * <ul>
     *   <li>Clamp bounds</li>
     *   <li>Defer tile updates while dragging</li>
     * </ul>
     */
    private static void initFxWebView(JFXPanel jfxPanel, JTextArea status, JTextField urlField,
                                      JButton btnLoad, JButton btnStackOverflow
    ) {
        /// --- Load progress diagnostics ---
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            append(status, "Load state: " + newState);

            /// --- When the page finishes loading successfully, inject our Leaflet patches ---
            if (newState == Worker.State.SUCCEEDED) {
                append(status, "Load succeeded. Applying map patches (bounds + drag-loading)...");
                applyLeafletPatches(engine, status);
            }
        });

        /// --- Track URL changes ---
        engine.locationProperty().addListener((obs, oldLoc, newLoc) -> append(status, "Location: " + newLoc + ", request. " + requests++));

        /// --- Capture JS alerts (useful for debugging your injected scripts too) ---
        engine.setOnAlert(e -> append(status, "Alert: " + e.getData()));

        /// --- Capture load exceptions ---
        engine.getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
            if (newEx != null) {
                append(status, "Load exception: " + newEx);
                for (StackTraceElement ste : newEx.getStackTrace())
                    append(status, "  at " + ste);
            }
        });

        /// --- Attach WebView to Swing host panel ---
        jfxPanel.setScene(new Scene(webView));

        /// --- Initial load ---
        engine.load(urlField.getText());

        /// --- Wire Swing buttons -> FX thread load calls ---
        btnLoad.addActionListener(e -> Platform.runLater(() -> engine.load(urlField.getText())));
        btnStackOverflow.addActionListener(e -> Platform.runLater(() -> engine.load("https://stackoverflow.com/")));
    }

    /// ----------------------------
    ///  Step 1 + Step 2: JS injection
    /// ----------------------------

    /**
     * Applies bounds clamping and "no heavy loading during drag" behavior by injecting JavaScript.
     * <p>
     * This is intentionally defensive:
     * - If Leaflet/map objects are not in the expected shape, it fails safely and logs a warning.
     * - It retries briefly because some apps build the Leaflet map after initial DOM load.
     */
    private static void applyLeafletPatches(WebEngine engine, JTextArea status) {
        /// --- Build the JS patch with your configured options ---
        final String js = buildLeafletPatchScript(
                ENABLE_BOUNDS_CLAMP,
                WORLD_MIN_X, WORLD_MIN_Y, WORLD_MAX_X, WORLD_MAX_Y,
                MAX_BOUNDS_VISCOSITY,
                DEFER_TILE_UPDATES_WHILE_DRAGGING,
                TILE_UPDATE_INTERVAL_MS,
                TILE_KEEP_BUFFER
        );

        /// --- Execute on FX thread (WebEngine requires FX thread) ---
        Platform.runLater(() -> {
            try {
                engine.executeScript(js);
                append(status, "Injected Leaflet patch script.");
            } catch (Throwable t) {
                append(status, "Failed to inject patch script: " + t);
            }
        });
    }

    /**
     * Builds a JavaScript snippet that:
     * <ol>
     *   <li>Finds the Leaflet map instance (commonly on {@code window.map}).</li>
     *   <li>Applies max bounds clamping.</li>
     *   <li>Forces tile layers to update when idle (not continuously during drag).</li>
     * </ol>
     *
     * <p>
     * Notes:
     * - Leaflet is typically exposed as {@code window.L}.
     * - Many Leaflet apps expose the map as {@code window.map}; if not, you may need to adjust the detection.
     */
    private static String buildLeafletPatchScript(
            boolean enableClamp,
            int minX, int minY, int maxX, int maxY,
            double viscosity,
            boolean deferTileUpdates,
            int updateIntervalMs,
            int keepBuffer
    ) {
        /// We build it as a single string so it can be injected via WebEngine.executeScript(...)
        return "(function(){\n"
                + "  // -----------------------------\n"
                + "  // Leaflet patch injector\n"
                + "  // - clamps map bounds (prevents infinite black space)\n"
                + "  // - defers tile updates while dragging (keeps drag responsive)\n"
                + "  // -----------------------------\n"
                + "  var ENABLE_CLAMP = " + enableClamp + ";\n"
                + "  var DEFER_TILES = " + deferTileUpdates + ";\n"
                + "  var MIN_X = " + minX + ", MIN_Y = " + minY + ", MAX_X = " + maxX + ", MAX_Y = " + maxY + ";\n"
                + "  var VISCOSITY = " + viscosity + ";\n"
                + "  var UPDATE_INTERVAL = " + updateIntervalMs + ";\n"
                + "  var KEEP_BUFFER = " + keepBuffer + ";\n"
                + "\n"
                + "  function log(msg){ try { console.log('[LeafletPatch] ' + msg); } catch(e){} }\n"
                + "\n"
                + "  function tryApply(){\n"
                + "    // Leaflet global\n"
                + "    var L = window.L;\n"
                + "    if (!L) { return false; }\n"
                + "\n"
                + "    // Many Leaflet apps store the map in window.map.\n"
                + "    // If Explv uses a different name, you can search window for Leaflet map instances.\n"
                + "    var map = window.map;\n"
                + "    if (!map || typeof map.getCenter !== 'function') {\n"
                + "      return false;\n"
                + "    }\n"
                + "\n"
                + "    // -----------------------------\n"
                + "    // Step 1: Clamp bounds\n"
                + "    // -----------------------------\n"
                + "    if (ENABLE_CLAMP && typeof map.setMaxBounds === 'function') {\n"
                + "      // In Leaflet, bounds are LatLngBounds. For CRS.Simple-style maps,\n"
                + "      // the \"lat\" and \"lng\" are still used as numeric coordinates.\n"
                + "      var southWest = L.latLng(MIN_Y, MIN_X);\n"
                + "      var northEast = L.latLng(MAX_Y, MAX_X);\n"
                + "      var bounds = L.latLngBounds(southWest, northEast);\n"
                + "\n"
                + "      try {\n"
                + "        map.setMaxBounds(bounds);\n"
                + "        map.options.maxBoundsViscosity = VISCOSITY;\n"
                + "        log('Applied max bounds: ('+MIN_X+','+MIN_Y+') -> ('+MAX_X+','+MAX_Y+'), viscosity=' + VISCOSITY);\n"
                + "      } catch (e) {\n"
                + "        log('Failed to apply bounds: ' + e);\n"
                + "      }\n"
                + "    } else {\n"
                + "      log('Bounds clamp disabled or unsupported.');\n"
                + "    }\n"
                + "\n"
                + "    // -----------------------------\n"
                + "    // Step 2: Defer tile updates while dragging\n"
                + "    // -----------------------------\n"
                + "    if (DEFER_TILES && map.eachLayer) {\n"
                + "      var patched = 0;\n"
                + "      map.eachLayer(function(layer){\n"
                + "        // Detect tile layers by presence of getTileUrl/options.\n"
                + "        var isTile = layer && layer.options && (layer.getTileUrl || layer._getTilePos);\n"
                + "        if (!isTile) return;\n"
                + "\n"
                + "        try {\n"
                + "          // Leaflet supports updating tiles when idle rather than during drag.\n"
                + "          layer.options.updateWhenIdle = true;\n"
                + "          layer.options.updateInterval = UPDATE_INTERVAL;\n"
                + "          layer.options.keepBuffer = KEEP_BUFFER;\n"
                + "\n"
                + "          // Abort any in-flight tile loading during aggressive drags if supported.\n"
                + "          // (Method names can vary by Leaflet version; guard checks keep this safe.)\n"
                + "          if (typeof layer._abortLoading === 'function') {\n"
                + "            map.on('dragstart', function(){ try { layer._abortLoading(); } catch(e){} });\n"
                + "          }\n"
                + "\n"
                + "          // Force redraw with new options.\n"
                + "          if (typeof layer.redraw === 'function') layer.redraw();\n"
                + "          patched++;\n"
                + "        } catch (e) {\n"
                + "          log('Tile layer patch failed: ' + e);\n"
                + "        }\n"
                + "      });\n"
                + "\n"
                + "      log('Patched tile layers: ' + patched);\n"
                + "    } else {\n"
                + "      log('Tile deferral disabled or map.eachLayer unavailable.');\n"
                + "    }\n"
                + "\n"
                + "    return true;\n"
                + "  }\n"
                + "\n"
                + "  // Retry briefly because some apps initialize Leaflet after initial load.\n"
                + "  var attempts = 0;\n"
                + "  var maxAttempts = 60; // ~6 seconds at 100ms interval\n"
                + "  var timer = setInterval(function(){\n"
                + "    attempts++;\n"
                + "    if (tryApply()) { clearInterval(timer); }\n"
                + "    else if (attempts >= maxAttempts) { clearInterval(timer); log('Could not find Leaflet map instance (window.map).'); }\n"
                + "  }, 100);\n"
                + "})();\n";
    }

    /// ----------------------------
    ///  URL builder
    /// ----------------------------

    /**
     * Builds the Explv map URL for a given center coordinate and zoom.
     */
    private static String buildExplvUrl(int centreX, int centreY, int centreZ, int zoom) {
        return "https://explv.github.io/?centreX=" + centreX
                + "&centreY=" + centreY
                + "&centreZ=" + centreZ
                + "&zoom=" + zoom;
    }

    /// ----------------------------
    ///  Logging helpers
    /// ----------------------------

    /**
     * Appends a line to the Swing status area on the Swing EDT.
     */
    private static void append(JTextArea area, String line) {
        SwingUtilities.invokeLater(() -> {
            area.append(line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    /// ----------------------------
    ///  Runtime checks
    /// ----------------------------

    /**
     * Prints Java runtime info to help diagnose “wrong JRE” installs on end user machines.
     */
    private static void printJavaRuntimeInfo() {
        System.out.println("java.version=" + System.getProperty("java.version"));
        System.out.println("java.vendor=" + System.getProperty("java.vendor"));
        System.out.println("java.home=" + System.getProperty("java.home"));
    }

    /**
     * Ensures JavaFX WebView is available (some Java 8 runtimes exclude it).
     *
     * @return true if JavaFX WebView classes exist, otherwise false (and shows a dialog).
     */
    private static boolean checkJavaFxWebViewAvailable() {
        try {
            Class.forName("javafx.embed.swing.JFXPanel");
            Class.forName("javafx.scene.web.WebView");
            return true;
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "JavaFX WebView is NOT available.\n\nMissing: " + e.getMessage()
                            + "\n\nYou need a Java 8 runtime that includes JavaFX WebView (JFXPanel + WebView).",
                    "JavaFX Missing",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }
    }

    private void closeMap() {

        // unload
        Platform.runLater(() -> engine.load(null));
    }

}
