package main.data.IPC;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class ServerSocketIPC {

    private ServerSocket server;
    private Socket client;
    private BufferedWriter out;
    private BufferedReader in;

    private final String token;
    private volatile boolean running;

    public ServerSocketIPC() {
        this.token = genToken();
    }

    public void start() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")); // port 0 = auto
        running = true;

        Thread t = new Thread(this::acceptLoop, "SimpleIpcServer");
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                client = server.accept();
                client.setTcpNoDelay(true);

                in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));

                // --- handshake: first line must equal token ---
                String got = in.readLine();
                if (!token.equals(got)) {
                    closeClient();
                    continue;
                }

                // connected, stop accepting new clients (optional)
                break;

            } catch (IOException e) {
                closeClient();
            }
        }
    }

    public void send(String msg) {
        try {
            if (out == null) return;
            synchronized (out) {
                out.write(msg);
                out.write("\n");
                out.flush();
            }
        } catch (IOException e) {
            closeClient();
        }
    }

    public String readLine() {
        try {
            if (in == null) return null;
            return in.readLine();
        } catch (IOException e) {
            closeClient();
            return null;
        }
    }

    public int getPort() {
        return server.getLocalPort();
    }

    public String getToken() {
        return token;
    }

    public void stop() {
        running = false;
        closeClient();
        try { if (server != null) server.close(); } catch (IOException ignored) {}
    }

    private void closeClient() {
        try { if (client != null) client.close(); } catch (IOException ignored) {}
        client = null; in = null; out = null;
    }

    private static String genToken() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
