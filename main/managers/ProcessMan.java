package main.managers;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Launcher class for ETAbot.
 * Handles the hand-off between Java and the Windows Batch environment.
 */
public class ProcessMan {
    public final static String BUILD_DIR = "build\\";
    public final static String URL_EXPLVS_MAP = "https://explv.github.io/?centreX=2798&centreY=3347&centreZ=0&zoom=7";

    /**
     * Temp main func bypasses potential client sandbox restrictions while I get this class working.
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("Total arguments received: " + args.length);

        try {
            String directory = BUILD_DIR;
            String filename = "home.bat";

            // 1. Locate the batch file
            File file = checkFile(new File(directory + filename));
            String filepath = file.getAbsolutePath();

            // 2. Determine which arguments to pass.
            String[] finalArgs;
            if (args.length > 0) {
                finalArgs = args;
            } else {
                finalArgs = new String[]{URL_EXPLVS_MAP};
            }

            // 3. Launch
            runBatch(filepath, true, finalArgs);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void runBatch(String batchFilePath, boolean wait, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add("start");
        command.add("\"ETAbot External Process\"");
        command.add(batchFilePath);

        for (String arg : args) {
            command.add(sanitizeArg(arg));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.inheritIO().start();

        if (wait) {
            p.waitFor();
        }
    }

    /** Cmd.exe-safe argument sanitizer. Use ONLY when building a cmd.exe command string. */
    public static String sanitizeArg(String s) {
        if (s == null)
            return "";

        s = s.trim().replace("\"", "");

        s = s.replace("^", "^^")
                .replace("&", "^&")
                .replace("|", "^|")
                .replace("<", "^<")
                .replace(">", "^>")
                .replace("(", "^(")
                .replace(")", "^)");

        s = s.replace("!", "^^!");

        return "\"" + s + "\"";
    }

    private static File checkFile(File file) {
        if (!file.exists()) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }
        return file;
    }
}