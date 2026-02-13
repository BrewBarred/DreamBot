//package main.data.server.local;
//
//import java.io.*;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.nio.charset.StandardCharsets;
//
//public class Client {
//    private static final boolean localHost = false;
//    private static final String hostHTTP = localHost ? "http://127.0.0.1:8080/settings" : "";
//
//    public static String loadSettingsJson() throws IOException {
//        HttpURLConnection con = (HttpURLConnection) new URL(hostHTTP).openConnection();
//        con.setRequestMethod("GET");
//        con.setConnectTimeout(2000);
//        con.setReadTimeout(2000);
//
//        return readStream(con.getInputStream());
//    }
//
//    public static boolean saveSettingsJson(String json) throws IOException {
//        HttpURLConnection con = (HttpURLConnection) new URL(hostHTTP).openConnection();
//        con.setRequestMethod("POST");
//        con.setDoOutput(true);
//        con.setConnectTimeout(2000);
//        con.setReadTimeout(2000);
//        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
//
//        try (OutputStream os = con.getOutputStream()) {
//            os.write(json.getBytes(StandardCharsets.UTF_8));
//        }
//
//        int code = con.getResponseCode();
//        return code == 200;
//    }
//
//    private static String readStream(InputStream in) throws IOException {
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        byte[] buf = new byte[4096];
//        int r;
//        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
//        return new String(out.toByteArray(), StandardCharsets.UTF_8);
//    }
//}
//
