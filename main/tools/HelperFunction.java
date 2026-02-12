package main.tools;

import main.BotMan;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

/**
 * Randomly assorted functions used to help develop this application.
 */
public class HelperFunction {
    BotMan bot;

    public HelperFunction(BotMan bot) {
        this.bot = bot;
    }
    /**
     * Prints a list of running processes currently open. This is used to detect if OSBot is open and which screen it is
     * on for multi-display setups.
     */
    private void listProcesses() throws IOException {
        Process process = Runtime.getRuntime().exec("tasklist.exe");
        Scanner scanner = new Scanner(new InputStreamReader(process.getInputStream()));
        for (String p = null; scanner.hasNext(); p = scanner.nextLine()) {
            if (p != null && p.contains("OS"))
                bot.setBotStatus("Found OSBot client: " + p);
        }
        scanner.close();
    }
}
