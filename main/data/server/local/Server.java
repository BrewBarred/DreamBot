package main.data.server.local;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server {

    private static final int PORT = 8080;
    private static final Path SETTINGS_FILE = Paths.get("settings.json");

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);

        server.createContext("/settings", new com.sun.net.httpserver.HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    String method = exchange.getRequestMethod();
                    if ("GET".equalsIgnoreCase(method)) {
                        handleGet(exchange);
                    } else if ("POST".equalsIgnoreCase(method)) {
                        handlePost(exchange);
                    } else {
                        send(exchange, 405, "Method Not Allowed");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    send(exchange, 500, "Server Error");
                }
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("SettingsServer running at http://127.0.0.1:" + PORT);
    }

    private static void handleGet(HttpExchange exchange) throws IOException {
        String json = "{}";
        if (Files.exists(SETTINGS_FILE)) {
            json = readFileUtf8(SETTINGS_FILE);
            if (json == null || json.trim().isEmpty()) {
                json = "{}";
            }
        }
        sendJson(exchange, 200, json);
    }

    private static void handlePost(HttpExchange exchange) throws IOException {
        String body = readStreamUtf8(exchange.getRequestBody());

        // Save EXACT raw JSON (your client should send valid JSON)
        writeFileUtf8(SETTINGS_FILE, body);

        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, code, json);
    }

    private static void send(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = null;
        try {
            os = exchange.getResponseBody();
            os.write(bytes);
        } finally {
            if (os != null) os.close();
        }
    }

    // -------- Java 8 helpers --------

    private static String readStreamUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String readFileUtf8(Path path) throws IOException {
        InputStream in = null;
        try {
            in = Files.newInputStream(path);
            return readStreamUtf8(in);
        } finally {
            if (in != null) in.close();
        }
    }

    private static void writeFileUtf8(Path path, String content) throws IOException {
        OutputStream out = null;
        try {
            out = Files.newOutputStream(path);
            byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
            out.write(bytes);
        } finally {
            if (out != null) out.close();
        }
    }
}
