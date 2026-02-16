//package main.managers;
//
//import com.google.gson.Gson;
//import main.menu.DreamBotMenu;
//import org.dreambot.api.Client;
//import org.dreambot.api.methods.interactive.Players;
//import org.dreambot.api.utilities.Logger;
//
//import javax.swing.*;
//import java.io.*;
//import java.net.*;
//import java.nio.charset.StandardCharsets;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//public class DataMan223 {
//    ///
//    ///     HTTP REQUEST/DATABASE ENUMS
//    ///
//
//    /**
//     * An enum containing all available database tables in the DreamBot server, for quick-reference.
//     */
//    public enum Database { DreamBot };
//    /**
//     * An enum containing all available database columns in the DreamBot server, for quick-reference.
//     */
//    public enum DREAMBOT_COLUMNS { username, timestamp, settings, presets };
//    /**
//     * An enum containing all available HTTP request methods used to interface the ETA Bot server.
//     */
//    public enum REQUEST_METHOD {POST, PATCH, GET}
//    /**
//     * The public key used to access the Supabase database that ETA Bot uses to dynamically save/load settings.
//     */
//    private final String SUPABASE_KEY;
//
//    private final String BASE_URL;
//    private final String FILTER;
//    /**
//     * A list of all settings columns in the settings database listed in order as they appear in the table,
//     * from left-to-right except for the 0th item, which is always the tables primary key(s).
//     */
//    private static final String[] DREAMBOT_SERVER_COLUMNS = {"username", "timestamp", "settings", "presets"};
//    //TODO: add to botmenu General settings tab
//    /**
//     * Connection timeout in milliseconds
//     */
//    private static final int CONNECTION_TIMEOUT = 5000;
//    //TODO: add to botmenu General settings tab
//    /** Read timeout in milliseconds */
//    private static final int READ_TIMEOUT = 5000;
//
//    /**
//     * A callback interface to handle responses from threaded database calls.
//     */
//    public interface DataCallback {
//        /**
//         * Called when the server request finishes.
//         *
//         * @param response The raw string response from the server.
//         */
//        void onComplete(String response);
//
//        void onComplete();
//    }
//
//    public DataMan223() {
//        /// set default values
//        SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF5Z2xma3Bvamtqd3Zjc215Ym"
//                + "xyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjgxNzUxMDMsImV4cCI6MjA4Mzc1MTEwM30.YJW7H1Kj-tGsYhpQwTk-7ALOF1yXK"
//                + "zO-I3LEt63-rRA";
//        BASE_URL = getBaseURL();
//        FILTER = filterByPlayerName();
//    }
//
////    To get all rows:
////    https://ayglfkpojkjwvcsmyblr.supabase.co/rest/v1/DreamBot?select=*
////
////    To get a specific player by username:
////    https://ayglfkpojkjwvcsmyblr.supabase.co/rest/v1/DreamBot?username=eq.PlayerName&select=*
//    /**
//     * @return The base URL for ETA Bot database requests.
//     */
//    private String getBaseURL() {
//        String url = BASE_URL;
//        if (url == null) {
//            // coded this way for easier encryption later
//            String projectID = "ayglfkpojkjwvcsmyblr";
//            url = String.format("https://%s.supabase.co/rest/v1/%s", projectID, getDatabaseTable());
//        }
//
//        return url;
//    }
//
//    private String getDatabaseTable() {
//        return Database.DreamBot.name();
//    }
//
//    private String getPostURL(String column) {
//        return getBaseURL() + Database.DreamBot + column;
//    }
//
////    public void getThreaded(String columnName, DataCallback callback) {
////        new Thread(() -> {
////            try {
////                String result = getServerSettings(columnName);
////                if (result != null && !isEmptySelectResult(result)) {
////                    if (callback != null) SwingUtilities.invokeLater(() -> callback.onComplete(result));
////                }
////            } catch (IOException e) {
////                Logger.log("Threaded GET Error: " + e.getMessage());
////            }
////        }).start();
////    }
//
//    /**
//     * Uploads the current Task Library set to the database server using the players name as the key.
//     * <p>
//     * If a player is not currently logged in, the most recent or currently loaded account will be used instead.
//     * <p>
//     * In the event no valid accounts are found logged in or loaded, this function will throw an error.
//     */
//    //TODO read this description above and ensure the 2 fail-safes are implemented
//    public void saveTaskLibrary(JList<DreamBotMenu.Task> libraryList) {
//        try {
//            Logger.log(Logger.LogType.INFO, "Attempting to upload presets...");
//            // Fail-safe logic
//            String playerName = Client.isLoggedIn() ? Players.getLocal().getName() : null;
//            if (playerName == null || playerName.isEmpty()) {
//                throw new IllegalStateException("No valid accounts are found logged in or loaded.");
//            }
//
//            // Pack data into a dictionary: { "TaskName": {TaskObject}, ... }
//            Map<String, DreamBotMenu.Task> taskMap = new LinkedHashMap<>(); // LinkedHashMap preserves JList order
//            for (int i = 0; i < libraryList.getModel().getSize(); i++) {
//                DreamBotMenu.Task task = libraryList.getModel().getElementAt(i);
//                taskMap.put(task.getName(), task);
//            }
//
//            Logger.log(Logger.LogType.INFO, "Attempting to save " + taskMap.size() + " tasks, please wait...");
//
//            String result = generateRequest(REQUEST_METHOD.POST, getPostURL(), taskMap);
//            Logger.log(Logger.LogType.DEBUG, "POST result: " + result);
//            Logger.log(Logger.LogType.INFO, "Successfully saved task library.");
//
//        } catch (Exception e) {
//            Logger.log(Logger.LogType.ERROR, "Failed to save task library: " + e.getMessage());
//            JOptionPane.showMessageDialog(null, "Error: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Generates an HTTP connection request using the passed parameters, and if valid, connects to the host
//     * using the generated connection, and returns the connection response.
//     *
//     * @param method The {@link REQUEST_METHOD HTTP request method} to use for this request.
//     * @param url The location path of the data on the server-side.
//     * @param jsonBody The body to attach to this request (required for PATCH & POST requests).
//     * @return A {@link String} value denoting the connection response.
//     */
//    private String generateRequest(REQUEST_METHOD method, String url, String jsonBody) {
//        try {
//            HttpURLConnection request = setPropertiesHTTP(method, url);
//            Logger.log(Logger.LogType.INFO, "Generated request: "
//                    + "Method: " + request.getRequestMethod()
//                    + "Body: " + jsonBody
//                    + "URL: " + url
//                    //"Connection: " + request, //TODO consider deleting
//                    + "Properties: " + request.getRequestProperties());
//
//            // write body only if provided (POST/PATCH)
//            if (jsonBody != null) {
//                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
//                try (OutputStream os = request.getOutputStream()) {
//                    os.write(bytes);
//                    os.flush();
//                }
//            }
//
//            return sendRequest(request);
//
//        } catch (Exception e) {
//            Logger.log(Logger.LogType.ERROR, "Failed to post data: " + e.getMessage());
//            return null;
//        }
//    }
//
//
////    public void getThreaded(String columnName, DataCallback callback) {
////        new Thread(() -> {
////            try {
////                // get column data
////                String result = getServerData(columnName);
////                // ONLY callback if result is valid data
////                if (result != null && callback != null)
////                    SwingUtilities.invokeLater(() -> callback.onComplete(result));
////                else
////                    Logger.log("No data found for: " + columnName);
////
////            } catch (IOException e) {
////                Logger.log("GET Failed: " + e.getMessage());
////            }
////        }).start();
////    }
//
//    private String getColumnURL(Database table, String columnName) {
//        return BASE_URL + table + FILTER + "&select=" + columnName;
//    }
//
//    // Save settings for a user
//    public boolean generatePOST(Database table, String columnName, String value) throws IOException {
//        String url = getPostURL(table);
//        String payload = convertToJsonString(columnName, value);
//
//        // log info to console for debugging
//        Logger.log("Posting setting:"
//                + "\n URL: " + url
//                + "\n Payload: " + payload
//                + "\n Column: " + columnName
//                + "\n Data: " + value);
//
//        return POST(url, payload);
//    }
//
////    private String generatePATCH(Database table, String columnName, String value) throws IOException {
////        String url = getTableURL(table);
////        String payload = convertToJsonString(columnName, value);
////
////        // log info to console for debugging
////        Logger.log("Patching setting:"
////                + "\n URL: " + url
////                + "\n Payload: " + payload
////                + "\n Column: " + columnName
////                + "\n Data: " + value);
////
////        return PATCH(url, payload);
////    }
//
//    /**
//     * Generate a URL to fetch the passed column data from the passed table in the ETA Bot database and return the
//     * response.
//     *
//     * @return A {@link String} containing the data from the requested column - if it exists in the database.
//     */
//    private String generateGET(Database table, String column) {
//        String url = getColumnURL(table, column);
//        // log info to console for debugging
//        Logger.log(Logger.LogType.INFO, String.format("Fetching \"%s\" from the \"%s\" table.", column, table));
//        return url;
//    }
//
////    /**
////     * Builds a JSON payload like {"column": value} where value must already be valid JSON.
////     */
////    private String convertToJsonString(String column, String jsonData) {
////        return "{\"" + column + "\":" + jsonData + "}";
////    }
//
//    /**
//     * Returns a filter for the HTTP request which compares the tables primary key against the players username before
//     * returning a row of results.
//     *
//     * @return A {@link String} which can be added to an HTTP url request to filter data by player name, reducing load.
//     */
//    private String filterByPlayerName() {
//        try {
//            // filter table by comparing table primary key (username), which is always column 0, against this players name
//            return "?" + DREAMBOT_COLUMNS.username + "=eq." + URLEncoder.encode(Players.getLocal().getName(), "UTF-8");
//        } catch (Exception e) {
//            e.printStackTrace();
//            return "";
//        }
//    }
//
////    /**
////     * Create and execute a GET request to retrieve the requested setting column.
////     * <n>
////     * Note: passing a null, empty or "*" columnName will return all settings.
////     *
////     * @return The HTTP GET response from the ETA Bot's server request.
////     */
////    public String getServerData(String columnName) throws IOException {
////        // clean column name to prevent invalid references
////        columnName = (columnName == null || columnName.isEmpty()) ? "*" : columnName;
////        // get "settings" column data from "Settings" table
////        String getSettingURL = generateGET(Database.DreamBot, columnName);
////        Logger.log(Logger.LogType.INFO,"Generated SELECT \"Settings\" URL: " + getSettingURL);
////
////        // generate and send the GET request to the server and collect the response for validation
////        String settings = GET(getSettingURL);
////        //
////        if (isEmptySelectResult(settings)) {
////            Logger.log(Logger.LogType.INFO,"No settings found for player: " + Players.getLocal().getName() + " (no data exists for this player!)");
////            return null;
////        }
////
////        // validate settings here
////        return settings;
////    }
////
////    /**
////     * Create and execute a PATCH request to update the passed (existing) column with the passed data value.
////     *
////     * @return The HTTP PATCH response from the ETA Bot's server request.
////     */
////    public String patchServerSetting(String columnName, String jsonData) throws IOException {
////        // generate a URL suitable for a PATCH HTTP request
////        String patchSettingURL = getTableURL(Database.DreamBot);
////        // convert the column header name and data provided into a payload to update database
////        String payload =  convertToJsonString(columnName, jsonData);
////
////        Logger.log(Logger.LogType.INFO, "Patching setting:"
////                + "\n URL: " + patchSettingURL
////                + "\n Column: " + columnName
////                + "\n JSON Data: " + jsonData
////                + "\n Payload: " + payload);
////
////        // send the patch request to the server
////        return PATCH(patchSettingURL, payload);
////    }
//
//    /**
//     * Create and execute a POST request to create or update the passed column with the passed data value.
//     *
//     * @return The HTTP POST response from the ETA Bot's server request.
//     */
//    private boolean postServerData(String columnName, Object jsonData) throws IOException {
//        try {
//            /// Prepare the payload to be uploaded to the database
//            //        // fetch the players username to use as a primary key in the post request
//            //        String username = Players.getLocal().getName();
//            // generate a URL suitable for a POST HTTP request
//            String postSettingURL = getPostURL(Database.DreamBot, false);
//            // convert the column header name and data provided into a payload to update/insert into database
//            String payload = convertToJsonString(columnName, jsonData);
//
//            ///  Print the post request to the logger for debugging
//            Logger.log(Logger.LogType.INFO, "Posting setting:"
//                    + "\n URL: " + postSettingURL
//                    + "\n Column: " + columnName
//                    + "\n JSON Data: " + jsonData
//                    + "\n Payload: " + payload);
//
//            // send the post request to the server
//            return POST(postSettingURL, payload);
//
//        } catch (Exception e) {
//            Logger.log(Logger.LogType.INFO, "Failed to post data: " + e.getMessage());
//            return false;
//        }
//    }
//
//    private String convertToJsonString(String column, Object data) {
//        Gson gson = new Gson();
//
//        Map<String, Object> payload = new HashMap<>();
//        payload.put(column, data);
//
//        return gson.toJson(payload);
//    }
////
////    private void postThreaded(String columnName, Object jsonData, DataCallback callback) {
////        new Thread(() -> {
////            try {
////                // This calls your existing postServerSetting function
////                if (postServerData(columnName, jsonData) && callback != null)
////                    SwingUtilities.invokeLater(callback::onComplete);
////            } catch (IOException e) {
////                Logger.log("Threaded POST Error: " + e.getMessage());
////            }
////        }).start();
////    }
//
////    /**
////     * Generates, executes and returns the result of an HTTP PUT request url connection suitable for the ETA Bot's
////     * server interpretation.
////     *
////     * @param path A {@link String} value denoting the full HTTP path to the data being updated/inserted.
////     * @return The connection response as a {@link String} value.
////     */
////    private boolean POST(String path, String payload) throws IOException {
////        return generateRequest(REQUEST_METHOD.POST, path, payload);
////    }
//
////    /**
////     * Generates and returns an HTTP PATCH request url connection suitable for ETA Bot's server to interpret, returning
////     * the result as a {@link String}.
////     */
////    private String PATCH(String path, String payload) throws IOException {
////        return generateRequest(REQUEST_METHOD.PATCH, path, payload);
////    }
////
////    private String GET(String path) throws IOException {
////        Logger.log(Logger.LogType.INFO, "Fetching data from: " + path);
////        // no payload passed with GET requests
////        return generateRequest(REQUEST_METHOD.GET, path, null);
////    }
////
////    private boolean isEmptySelectResult(String body) {
////        if (body == null) return true;
////        String s = body.trim();
////        // Supabase returns "[]" for an empty select, but sometimes also "null"
////        return s.isEmpty() || s.equals("[]") || s.equals("null");
////    }
//
//    /**
//     * Generate a POST, PATCH OR GET HTTP connection request URL using the passed path and request method.
//     *
//     * @param connectionURL The url denoting the path to collect data from the server.
//     * @param method The HTTP request to use in this response
//     * @return A {@link String} value representing a connection request URL ready for HTTP transmission.
//     */
//    private HttpURLConnection setPropertiesHTTP(REQUEST_METHOD method, String connectionURL) throws IOException {
//        // connect to the database using the passed connection URL
//        HttpURLConnection request = connect(connectionURL);
//        // set HTTP request method type
//        request.setRequestMethod(method.toString());
//        // set API key & authorization
//        request.setRequestProperty("apikey", SUPABASE_KEY);
//        request.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
//        // set safety timeouts to prevent endless loops
//        request.setConnectTimeout(CONNECTION_TIMEOUT);
//        request.setReadTimeout(READ_TIMEOUT);
//
//        // extra logic for patch/put since they have bodies too
//        if (method == REQUEST_METHOD.PATCH ||  method == REQUEST_METHOD.POST) {
//            request.setRequestProperty("Content-Type", "application/json");
//            request.setDoOutput(true);
//            // reduce load by merging duplicates on entry
//            request.setRequestProperty("Prefer", // set preferences for this request
//                    "return=representation," + // return object to prevent numerous empty response error codes
//                            "resolution=merge-duplicates"); // auto-merge data to prevent duplicates and wasted resources
//        }
//
//        return request;
//    }
//
//    /**
//     * Create a url using the passed request string and try open a connection to it, returning the connection object.
//     *
//     * @param path The URL used to GET/POST data from/to the ETA Bot database.
//     * @return A reference to the connection if it was successful.
//     */
//    private HttpURLConnection connect(String path) throws IOException {
//        // create a url using the passed request string and connect to it, returning the connection
//        return (HttpURLConnection) new URL(path).openConnection();
//    }
//
//    /**
//     * Reads the passed {@link HttpURLConnection connection request result}, logging and returning the full text.
//     *
//     * @param request The {@link HttpURLConnection} to read.
//     * @return The response of the {@link HttpURLConnection} connection request, if successful.
//     */
//    private boolean sendRequest(HttpURLConnection request) throws IOException {
//        // validate response code before continuing
//        int code = request.getResponseCode();
//        // convert HTTP Request into LogSource for better debugging
//        Logger.log(Logger.LogType.INFO, request.getRequestMethod());
//        Logger.log(Logger.LogType.INFO, "Response [" + code + "] " + request.getResponseMessage());
//        // use error stream on failure, input stream on success
//        InputStream stream = (code >= 200 && code < 300)
//                ? request.getInputStream()
//                : request.getErrorStream();
//
//        // some responses (e.g., 204) may have no body
//        if (stream == null)
//            return false;
//
//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
//            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = reader.readLine()) != null)
//                response.append(line);
//            Logger.log(Logger.LogType.INFO, response.toString());
//            return true;
//        }
//    }
//}