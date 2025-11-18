package com.github.minecraftedu.network;

import com.github.minecraftedu.MinecraftEduMod;
import com.github.minecraftedu.multiplayer.ConnectionManager;
import com.github.minecraftedu.multiplayer.MultiplayerConfig;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SimpleWebSocketServer {
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final int port;
    private final MinecraftServer minecraftServer;
    private final MultiplayerConfig config;
    private final ConnectionManager connectionManager;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private ScheduledExecutorService healthCheckExecutor;
    private volatile boolean running = false;

    // アクティブなWebSocket接続を管理
    private final Map<String, WebSocketConnection> activeWebSockets;

    public SimpleWebSocketServer(int port, MinecraftServer minecraftServer) {
        this.port = port;
        this.minecraftServer = minecraftServer;
        this.executor = Executors.newCachedThreadPool();
        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        this.activeWebSockets = new ConcurrentHashMap<>();

        // 設定ファイル読み込み
        Path configPath = Paths.get("config", "minecraftedu", "multiplayer.json");
        this.config = MultiplayerConfig.load(configPath);
        this.config.printConnectionInfo();

        // ConnectionManager初期化
        this.connectionManager = new ConnectionManager(config);
    }

    /**
     * WebSocket接続を格納するクラス
     */
    public static class WebSocketConnection {
        public final Socket socket;
        public final OutputStream outputStream;
        public String sessionId;

        public WebSocketConnection(Socket socket, OutputStream outputStream) {
            this.socket = socket;
            this.outputStream = outputStream;
        }
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        MinecraftEduMod.LOGGER.info("WebSocket server started on port " + port);

        // ヘルスチェックタスク開始（60秒ごと）
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                connectionManager.performHealthCheck();
            } catch (Exception e) {
                MinecraftEduMod.LOGGER.error("Error during health check", e);
            }
        }, 60, 60, TimeUnit.SECONDS);

        // Accept connections in a separate thread
        executor.submit(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    MinecraftEduMod.LOGGER.info("Client connected: " + client.getRemoteSocketAddress());
                    executor.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) {
                        MinecraftEduMod.LOGGER.error("Error accepting client", e);
                    }
                }
            }
        });
    }

    private void handleClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {

            // Read HTTP headers
            String line;
            StringBuilder headers = new StringBuilder();
            String webSocketKey = null;

            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                headers.append(line).append("\r\n");
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    webSocketKey = line.substring("Sec-WebSocket-Key:".length()).trim();
                }
            }

            if (webSocketKey == null) {
                MinecraftEduMod.LOGGER.warn("No WebSocket key found in headers");
                client.close();
                return;
            }

            // Perform WebSocket handshake
            String acceptKey = generateAcceptKey(webSocketKey);
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                    "\r\n";

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();

            MinecraftEduMod.LOGGER.info("WebSocket handshake completed");

            // WebSocket接続を保存
            String connectionId = client.getRemoteSocketAddress().toString();
            WebSocketConnection wsConnection = new WebSocketConnection(client, out);
            activeWebSockets.put(connectionId, wsConnection);

            // Handle WebSocket frames
            InputStream in = client.getInputStream();
            while (running && !client.isClosed()) {
                // Read WebSocket frame
                int firstByte = in.read();
                if (firstByte == -1) break;

                boolean fin = (firstByte & 0x80) != 0;
                int opcode = firstByte & 0x0F;

                int secondByte = in.read();
                if (secondByte == -1) break;

                boolean masked = (secondByte & 0x80) != 0;
                int payloadLength = secondByte & 0x7F;

                // Extended payload length
                if (payloadLength == 126) {
                    payloadLength = (in.read() << 8) | in.read();
                } else if (payloadLength == 127) {
                    payloadLength = 0;
                    for (int i = 0; i < 8; i++) {
                        payloadLength = (payloadLength << 8) | in.read();
                    }
                }

                // Masking key
                byte[] maskingKey = new byte[4];
                if (masked) {
                    in.read(maskingKey);
                }

                // Payload data
                byte[] payload = new byte[payloadLength];
                in.read(payload);

                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
                    }
                }

                // Handle message based on opcode
                if (opcode == 0x1) { // Text frame
                    String message = new String(payload, StandardCharsets.UTF_8);
                    MinecraftEduMod.LOGGER.info("Received WebSocket message: " + message);
                    handleWebSocketMessage(message, wsConnection);
                } else if (opcode == 0x8) { // Close frame
                    MinecraftEduMod.LOGGER.info("Client requested close");
                    break;
                } else if (opcode == 0x9) { // Ping frame
                    // Send pong
                    sendPong(out, payload);
                }
            }

        } catch (Exception e) {
            MinecraftEduMod.LOGGER.error("Error handling client", e);
        } finally {
            // 切断処理
            try {
                String connectionId = client.getRemoteSocketAddress().toString();
                WebSocketConnection wsConn = activeWebSockets.remove(connectionId);
                if (wsConn != null && wsConn.sessionId != null) {
                    connectionManager.handleDisconnection(wsConn.sessionId);
                }
                client.close();
                MinecraftEduMod.LOGGER.info("Client disconnected");
            } catch (IOException e) {
                MinecraftEduMod.LOGGER.error("Error closing client", e);
            }
        }
    }

    private void handleWebSocketMessage(String message, WebSocketConnection wsConnection) {
        try {
            // メッセージハンドラーを作成（接続情報を渡す）
            MinecraftWebSocketHandler handler = new MinecraftWebSocketHandler(
                minecraftServer,
                connectionManager,
                this,
                wsConnection
            );

            String response = handler.handleMessage(message);

            if (response != null) {
                sendTextFrame(wsConnection.outputStream, response);
            }
        } catch (Exception e) {
            MinecraftEduMod.LOGGER.error("Error handling message", e);
        }
    }

    /**
     * セッションIDからWebSocket接続を取得
     */
    public WebSocketConnection getWebSocketConnection(String sessionId) {
        for (WebSocketConnection conn : activeWebSockets.values()) {
            if (sessionId.equals(conn.sessionId)) {
                return conn;
            }
        }
        return null;
    }

    /**
     * すべてのクライアントにメッセージを送信
     */
    public void broadcastMessage(String message) {
        for (WebSocketConnection conn : activeWebSockets.values()) {
            try {
                sendTextFrame(conn.outputStream, message);
            } catch (IOException e) {
                MinecraftEduMod.LOGGER.error("Error broadcasting message", e);
            }
        }
    }

    /**
     * 特定のセッション以外にメッセージを送信
     */
    public void broadcastMessageExcept(String excludeSessionId, String message) {
        for (WebSocketConnection conn : activeWebSockets.values()) {
            if (!excludeSessionId.equals(conn.sessionId)) {
                try {
                    sendTextFrame(conn.outputStream, message);
                } catch (IOException e) {
                    MinecraftEduMod.LOGGER.error("Error broadcasting message", e);
                }
            }
        }
    }

    /**
     * WebSocket接続を登録
     */
    public void registerWebSocketConnection(String connectionId, String sessionId) {
        WebSocketConnection conn = activeWebSockets.get(connectionId);
        if (conn != null) {
            conn.sessionId = sessionId;
        }
    }

    private void sendTextFrame(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();

        // First byte: FIN + opcode (0x1 for text)
        frame.write(0x81);

        // Second byte: payload length (no mask for server-to-client)
        if (payload.length < 126) {
            frame.write(payload.length);
        } else if (payload.length < 65536) {
            frame.write(126);
            frame.write((payload.length >> 8) & 0xFF);
            frame.write(payload.length & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) ((payload.length >> (i * 8)) & 0xFF));
            }
        }

        // Payload
        frame.write(payload);

        out.write(frame.toByteArray());
        out.flush();
    }

    private void sendPong(OutputStream out, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x8A); // FIN + opcode 0xA (pong)
        frame.write(payload.length);
        frame.write(payload);
        out.write(frame.toByteArray());
        out.flush();
    }

    private String generateAcceptKey(String webSocketKey) {
        try {
            String combined = webSocketKey + WEBSOCKET_GUID;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            MinecraftEduMod.LOGGER.error("Error generating accept key", e);
            return "";
        }
    }

    public void stop() {
        running = false;
        try {
            // すべての接続をクローズ
            for (WebSocketConnection conn : activeWebSockets.values()) {
                try {
                    conn.socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            activeWebSockets.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            if (healthCheckExecutor != null) {
                healthCheckExecutor.shutdownNow();
            }
            MinecraftEduMod.LOGGER.info("WebSocket server stopped");
        } catch (IOException e) {
            MinecraftEduMod.LOGGER.error("Error stopping server", e);
        }
    }
}
