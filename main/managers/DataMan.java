package main.managers;

import com.google.gson.Gson;
import main.menu.DreamBotMenu;
import org.dreambot.api.Client;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.utilities.Logger;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * DataMan (Data Manager) handles all HTTP communication with the Supabase database.
 * It provides methods to save and load player-specific settings and presets.
 */
public class DataMan {

    // --- Database Constants ---
    public enum Database { DreamBot }
    public enum REQUEST_METHOD { POST, GET, PATCH }

    /** The project-specific Supabase API key (Anon Key) */
    private final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF5Z2xma3Bvamtqd3Zjc215Ym"
            + "xyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgxNzUxMDMsImV4cCI6MjA4Mzc1MTEwM30.YJW7H1Kj-tGsYhpQwTk-7ALOF1yXK"
            + "zO-I3LEt63-rRA";

    /** Base URL for the specific database table REST endpoint */
    private final String TABLE_URL = "https://ayglfkpojkjwvcsmyblr.supabase.co/rest/v1/" + Database.DreamBot.name();

    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    private final Gson gson = new Gson();

    /**
     * Constructor for the Data Manager.
     */
    public DataMan() {
        // Initialization if needed
    }

    // --- Primary Public Methods ---

//    public void saveTaskList(JList<DreamBotMenu.Task> taskList) {
//        new Thread(() -> {
//            try {
//                String playerName = getValidPlayerName();
//                if (playerName == null)
//                    return;
//
//                Logger.log(Logger.LogType.INFO, "Saving " + playerName + "'s task list...");
//
//                // Convert JList items into a Map to preserve structure
//                Map<String, DreamBotMenu.Task> taskMap = new LinkedHashMap<>();
//                for (int i = 0; i < taskList.getModel().getSize(); i++) {
//                    DreamBotMenu.Task task = taskList.getModel().getElementAt(i);
//                    taskMap.put(task.getName(), task);
//                }
//
//                // Construct the JSON payload for Supabase
//                // Note: The key names must match your database column names exactly
//                Map<String, Object> payload = new HashMap<>();
//                payload.put("username", playerName);
//                payload.put("tasks", taskMap);
//                payload.put("last_accessed", "now()");
//
//                String jsonBody = gson.toJson(payload);
//
//                // POST to Supabase (configured as UPSERT in setPropertiesHTTP)
//                boolean success = executeRequest(REQUEST_METHOD.POST, TABLE_URL, jsonBody);
//
//                if (success)
//                    Logger.log(Logger.LogType.INFO, "Save success!");
//
//            } catch (Exception e) {
//                Logger.log(Logger.LogType.ERROR, "Save error: " + e.getMessage());
//            }
//        }).start();
//    }

//    public void saveTaskList(JList<DreamBotMenu.Task> taskList) {
//        new Thread(() -> {
//            try {
//                String playerName = getValidPlayerName();
//                if (playerName == null)
//                    return;
//
//                Logger.log(Logger.LogType.INFO, "Saving " + playerName + "'s task list...");
//
//                // Use a List instead of a Map to preserve duplicates
//                List<DreamBotMenu.Task> taskListData = new ArrayList<>();
//                for (int i = 0; i < taskList.getModel().getSize(); i++) {
//                    taskListData.add(taskList.getModel().getElementAt(i));
//                }
//
//                Map<String, Object> payload = new HashMap<>();
//                payload.put("username", playerName);
//                payload.put("tasks", taskListData);
//                payload.put("last_accessed", "now()");
//
//                String jsonBody = gson.toJson(payload);
//                Logger.log(Logger.LogType.INFO, "Sending Payload: " + jsonBody);
//
//                boolean success = executeRequest(REQUEST_METHOD.POST, TABLE_URL, jsonBody);
//
//                if (success)
//                    Logger.log(Logger.LogType.INFO, "Save success!");
//                else
//                    Logger.log(Logger.LogType.ERROR, "Save failed: Server rejected the request.");
//
//            } catch (Exception e) {
//                Logger.log(Logger.LogType.ERROR, "Save error: " + e.getMessage());
//            }
//        }).start();
//    }

//    /**
//     * Uploads the current Task Library (Presets) to the database.
//     * <p>
//     * Uses Supabase 'Upsert' logic to update the row if the username already exists.
//     *
//     * @param libraryList The JList containing task library items
//     */
//    public void saveTaskLibrary(JList<DreamBotMenu.Task> libraryList) {
//        new Thread(() -> {
//            try {
//                String playerName = getValidPlayerName();
//                if (playerName == null)
//                    return;
//
//                Logger.log(Logger.LogType.INFO, "Saving " + playerName + "'s library list...");
//
//                // Convert JList items into a Map to preserve structure
//                Map<String, DreamBotMenu.Task> taskMap = new LinkedHashMap<>();
//                for (int i = 0; i < libraryList.getModel().getSize(); i++) {
//                    DreamBotMenu.Task task = libraryList.getModel().getElementAt(i);
//                    taskMap.put(task.getName(), task);
//                }
//
//                // Construct the JSON payload for Supabase
//                // Note: The key names must match your database column names exactly
//                Map<String, Object> payload = new HashMap<>();
//                payload.put("username", playerName);
//                payload.put("library", taskMap);
//                payload.put("last_accessed", "now()");
//
//                String jsonBody = gson.toJson(payload);
//
//                // POST to Supabase (configured as UPSERT in setPropertiesHTTP)
//                boolean success = executeRequest(REQUEST_METHOD.POST, TABLE_URL, jsonBody);
//
//                if (success)
//                    Logger.log(Logger.LogType.INFO, "Save success!");
//
//            } catch (Exception e) {
//                Logger.log(Logger.LogType.ERROR, "Save error: " + e.getMessage());
//            }
//        }).start();
//    }

    /**
     * Retrieves specific data from the database for the current player.
     * * @param columnName The column to fetch (e.g., "presets", "settings").
     * @return The raw JSON response string or null if not found.
     */
    public String loadDataByPlayer(String columnName) {
        try {
            String playerName = getValidPlayerName();

            if (playerName != null) {
                String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8.toString()).replace("+", "%20");
                String requestUrl = String.format("%s?username=eq.%s&select=%s", TABLE_URL, encodedName, columnName);
                String result = fetchRequest(requestUrl);

                if (result != null && !result.trim().equals("[]"))
                    return result;

                Logger.log(Logger.LogType.INFO, playerName + " has no saved data, loading defaults...");
            }

            Logger.log(Logger.LogType.INFO, "Loading default data from Zezima...");
            String encodedDefault = URLEncoder.encode("Zezima", StandardCharsets.UTF_8.toString());

            // Restored exactly to eq. to fix the "failed to parse filter" error
            String defaultUrl = String.format("%s?username=eq.%s&select=%s", TABLE_URL, encodedDefault, columnName);
            String result = fetchRequest(defaultUrl);

            if (result == null || result.trim().equals("[]")) {
                Logger.log(Logger.LogType.ERROR, "CRITICAL: Zezima fallback returned empty! Check your Supabase table for the exact spelling of Zezima.");
                return null;
            }

            return result;

        } catch (Exception e) {
            Logger.log(Logger.LogType.ERROR, "Load Error: " + e.getMessage());
            return null;
        }
    }

    // --- Networking Logic ---

    /**
     * Executes a POST/PATCH request with a JSON body.
     */
    private boolean executeRequest(REQUEST_METHOD method, String urlString, String jsonBody) throws IOException {
        HttpURLConnection conn = setPropertiesHTTP(method, urlString);

        if (jsonBody != null) {
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        int code = conn.getResponseCode();
        return handleResponse(conn, code);
    }

    /**
     * Executes a GET request and returns the response body.
     */
    private String fetchRequest(String urlString) throws IOException {
        HttpURLConnection conn = setPropertiesHTTP(REQUEST_METHOD.GET, urlString);
        int code = conn.getResponseCode();

        // If Supabase throws an error (like a bad URL), catch it and return null
        // so Gson doesn't try to parse an error message and crash the client.
        if (code < 200 || code >= 300) {
            if (conn.getErrorStream() != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder error = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) error.append(line);
                    Logger.log(Logger.LogType.ERROR, "Supabase GET Error: " + error.toString());
                }
            }
            conn.disconnect();
            return null;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                response.append(line);

            String result = response.toString();

            // Supabase returns an array for selects; check if empty
            if (result.trim().equals("[]")) {
                return null;
            }
            return result;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Configures the HttpURLConnection with required Supabase headers and timeouts.
     */
    private HttpURLConnection setPropertiesHTTP(REQUEST_METHOD method, String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod(method.name());
        conn.setRequestProperty("apikey", SUPABASE_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
        conn.setRequestProperty("Content-Type", "application/json");

        // Timeout handling
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);

        // Supabase-specific: 'resolution=merge-duplicates' allows POST to act as UPSERT
        if (method == REQUEST_METHOD.POST) {
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
            conn.setDoOutput(true);
        } else if (method == REQUEST_METHOD.PATCH) {
            conn.setDoOutput(true);
        }

        return conn;
    }

    /**
     * Processes the server response code and logs errors if necessary.
     */
    private boolean handleResponse(HttpURLConnection conn, int code) throws IOException {
        if (code >= 200 && code < 300) {
            return true;
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) error.append(line);
                Logger.log(Logger.LogType.ERROR, "Server returned code " + code + ": " + error.toString());
            }
            return false;
        }
    }

    /**
     * Helper to get the player name safely.
     */
    private String getValidPlayerName() {
        if (!Client.isLoggedIn()) {
            Logger.log(Logger.LogType.ERROR, "Action failed: Player is not logged in.");
            return null;
        }

        String name = Players.getLocal().getName();
        if (name == null || name.isEmpty()) {
            Logger.log(Logger.LogType.ERROR, "Action failed: Could not retrieve player name.");
            return null;
        }

        return name;
    }

    /**
     * Unified save method to package all user data into a single, clean JSON payload.
     * This relies on Supabase's 'resolution=merge-duplicates' to overwrite the single row
     * identified by the player's username.
     */
    public void saveEverything(
            JList<DreamBotMenu.Task> taskList,
            JList<DreamBotMenu.Task> libraryList,
            DreamBotMenu.BuilderSnapshot builder,
            DreamBotMenu.SettingsSnapshot settings,
            List<DreamBotMenu.Preset> presets, int[] loc,
            int[] inv,
            int[] worn,
            int[] skills,
            Runnable onComplete) {

        new Thread(() -> {
            try {
                String playerName = getValidPlayerName();
                if (playerName == null) return;

                // 1. Pack the Task List
                List<DreamBotMenu.Task> taskListData = new ArrayList<>();
                for (int i = 0; i < taskList.getModel().getSize(); i++) {
                    taskListData.add(taskList.getModel().getElementAt(i));
                }

                // 2. Pack the Library Map
                Map<String, DreamBotMenu.Task> libraryMap = new LinkedHashMap<>();
                for (int i = 0; i < libraryList.getModel().getSize(); i++) {
                    DreamBotMenu.Task task = libraryList.getModel().getElementAt(i);
                    libraryMap.put(task.getName(), task);
                }

                // 3. Construct the massive unified payload
                Map<String, Object> payload = new HashMap<>();
                payload.put("username", playerName);// Generate an exact timestamp with your local timezone offset attached
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                payload.put("last_accessed", sdf.format(new java.util.Date()));
                payload.put("tasks", taskListData);
                payload.put("library", libraryMap);
                payload.put("builder", builder);
                payload.put("settings", settings);
                payload.put("last_known_location", loc);
                payload.put("inventory", inv);
                payload.put("worn", worn);
                payload.put("skills", skills);

                // 4. Send exactly ONE request to the database
                String jsonBody = gson.toJson(payload);
                boolean success = executeRequest(REQUEST_METHOD.POST, TABLE_URL, jsonBody);

                if (success) {
                    Logger.log(Logger.LogType.INFO, "Player save complete!");
                    if (onComplete != null) {
                        onComplete.run();
                    }
                } else {
                    Logger.log(Logger.LogType.ERROR, "Failed to save player data.");
                }

            } catch (Exception e) {
                Logger.log(Logger.LogType.ERROR, "Error saving player data. " + e.getMessage());
            }
        }).start();
    }
}