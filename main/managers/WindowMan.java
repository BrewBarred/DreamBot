//package main.managers;
//
//import main.BotMan;
//import main.task.Task;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.WindowAdapter;
//import java.awt.event.WindowEvent;
//import java.io.File;
//
///**
// * The Window-Manager provides functions used to detect, create, activate, select, use and manipulate various windows
// * applications and processes.
// * <p>
// * This class is mainly used to spawn windows on alternate screens and to attach listeners to the client/menu for
// * real-time updates. It is also used to launch cmd.exe processes required for advanced features' external jars such as
// * JFX and web-viewer, which is used to load Explvs map inside the BotMenu.
// *
// * TODO load this class into settings so it displays all detected screens and the player can click which screen
// * (i.e., Monitor 1, Monitor 2 or Monitor 3) to spawn the BotMenu on then use that instead.
// */
//public class WindowMan {
//    //TODO investigate security liabilities associate with having a public static function that can start any process
//    // on someones pc
//    public static void startProcess(File path, boolean createFile, boolean exit, String... processArgs) {
//        try {
//            // ensure the path exists, or is created if create bool is true
//            if (!path.exists() && !(createFile && path.createNewFile()))
//                throw new RuntimeException("Unable to find file: " + path);
//
//            // TODO setup launch external jar/batch file logic
//            // (Standard Java ProcessBuilder used here)
//
//        } catch (Exception e) {
//            BotMan.Log("Error starting process: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Attaches a listener to the passed list which automatically triggers a BotMenu refresh when the list model
//     * is updated.
//     */
//    public final void attachMenuListListeners(JList<Task> list) {
//        list.addListSelectionListener(e -> {
//            if (!e.getValueIsAdjusting())
//                //BotMenu.refresh();
//                //TODO fix!!
//                System.out.println("Failed refresh!");
//        });
//    }
//
//    /**
//     * Attaches a window listener to the passed frame which executes the passed task when the window is closed.
//     */
//    public final void attachOnCloseEvent(JFrame frame, Runnable task) {
//        frame.addWindowListener(new WindowAdapter() {
//            @Override
//            public void windowClosing(WindowEvent e) {
//                task.run();
//            }
//        });
//    }
//
//    /**
//     * Attempts to find the DreamBot client window and the BotMenu window, then moves the BotMenu to a different
//     * monitor if one is available to prevent the menu overlapping the game screen.
//     */
//    public static void moveToAlternateMonitor(JFrame menuFrame) {
//        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
//        if (screens.length < 2) return;
//
//        Frame botClient = findDreambotFrame(); // Renamed from findOsbotFrame
//        int clientMonitor = (botClient != null) ? screenIndexFor(botClient) : 0;
//        int menuMonitor = (screens.length == 1 || clientMonitor > 0) ? 0 : 1;
//
//        Rectangle menuMonitorBounds = screens[menuMonitor].getDefaultConfiguration().getBounds();
//        Dimension menuMonitorSize = menuFrame.getSize();
//        int x = menuMonitorBounds.x + Math.max(0, (menuMonitorBounds.width - menuMonitorSize.width) / 2);
//        int y = menuMonitorBounds.y + Math.max(0, (menuMonitorBounds.height - menuMonitorSize.height) / 2);
//
//        menuFrame.setLocation(x, y);
//    }
//
//    private static Frame findDreambotFrame() { // Updated for DreamBot
//        for (Frame f : Frame.getFrames()) {
//            if (f == null || !f.isVisible())
//                continue;
//
//            String t = f.getTitle();
//            if (t == null)
//                continue;
//
//            if (t.toLowerCase().contains("dreambot")) // Changed search string
//                return f;
//        }
//        return null;
//    }
//
//    private static int screenIndexFor(Window window) {
//        Rectangle r = window.getBounds();
//        Point c = new Point(r.x + r.width / 2, r.y + r.height / 2);
//
//        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
//        for (int i = 0; i < screens.length; i++) {
//            if (screens[i].getDefaultConfiguration().getBounds().contains(c))
//                return i;
//        }
//        return 0;
//    }
//}