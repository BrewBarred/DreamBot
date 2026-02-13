//package main.world.map;
//
//import org.dreambot.api.script.AbstractScript;
//import org.dreambot.api.script.Category;
//import org.dreambot.api.script.ScriptManifest;
//
//import java.io.BufferedReader;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.util.Arrays;
//import java.util.List;
//
//@ScriptManifest(
//        name = "Process Launch Test",
//        author = "ETA",
//        version = 0.1,
//        description = "Tests whether DreamBot environment allows starting external processes.",
//        category = Category.MISC)
//public class TestLaunchProcess extends AbstractScript {
//
//    @Override
//    public void onStart() {
//        log("=== Process Launch Test starting ===");
//
//        log("os.name=" + System.getProperty("os.name"));
//        log("java.version=" + System.getProperty("java.version"));
//        log("java.home=" + System.getProperty("java.home"));
//
//        // If DreamBot uses a SecurityManager/sandbox, this often throws SecurityException.
//        try {
//            SecurityManager sm = System.getSecurityManager();
//            log("SecurityManager=" + (sm == null ? "null" : sm.getClass().getName()));
//        } catch (Throwable t) {
//            log("Could not read SecurityManager: " + t);
//        }
//
//        String os = System.getProperty("os.name", "").toLowerCase();
//        if (os.contains("win")) {
//            tryStart("Windows direct: notepad.exe", Arrays.asList("notepad.exe"), false);
//            tryStart("Windows shell start: cmd /c start notepad", Arrays.asList("cmd.exe", "/c", "start", "\"\"", "notepad.exe"), false);
//            tryStart("Windows echo: cmd /c echo hi", Arrays.asList("cmd.exe", "/c", "echo", "hi_from_dreambot"), true);
//        } else {
//            tryStart("Unix echo: sh -c echo hi", Arrays.asList("sh", "-c", "echo hi_from_dreambot"), true);
//        }
//
//        log("=== Process Launch Test finished onStart() ===");
//    }
//
//    /**
//     * Attempt to start a process and log:
//     * - whether ProcessBuilder.start() succeeded
//     * - exit code (if we wait)
//     * - stdout/stderr (for harmless commands)
//     */
//    private void tryStart(String label, List<String> cmd, boolean waitAndReadOutput) {
//        log("");
//        log("---- " + label + " ----");
//        log("CMD: " + cmd);
//
//        try {
//            ProcessBuilder pb = new ProcessBuilder(cmd);
//
//            // Don’t inherit IO inside DreamBot; read streams if we wait.
//            Process p = pb.start();
//            log("STARTED: pid=" + safePid(p) + " (ProcessBuilder.start() succeeded)");
//
//            if (waitAndReadOutput) {
//                String out = readAll(p.getInputStream());
//                String err = readAll(p.getErrorStream());
//
//                int code = p.waitFor();
//                log("EXIT CODE: " + code);
//
//                if (!out.trim().isEmpty()) log("STDOUT: " + out.trim());
//                if (!err.trim().isEmpty()) log("STDERR: " + err.trim());
//            } else {
//                log("Not waiting (interactive app or start command).");
//            }
//        } catch (SecurityException se) {
//            log("BLOCKED by SecurityException: " + se);
//            for (StackTraceElement ste : se.getStackTrace()) log("  at " + ste);
//        } catch (Throwable t) {
//            log("FAILED: " + t);
//            for (StackTraceElement ste : t.getStackTrace()) log("  at " + ste);
//        }
//    }
//
//    private static String readAll(InputStream is) {
//        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
//            StringBuilder sb = new StringBuilder();
//            for (String line; (line = br.readLine()) != null; ) {
//                sb.append(line).append('\n');
//            }
//            return sb.toString();
//        } catch (Throwable t) {
//            return "(failed reading stream: " + t + ")";
//        }
//    }
//
//    private static String safePid(Process p) {
//        try {
//            // Java 8 doesn't have Process.pid(); reflectively try (will usually fail).
//            return "unknown";
//        } catch (Throwable ignored) {
//            return "unknown";
//        }
//    }
//
//    @Override
//    public int onLoop() {
//        return 200;
//    }
//}