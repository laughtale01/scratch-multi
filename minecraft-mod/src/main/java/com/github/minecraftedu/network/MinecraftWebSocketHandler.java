package com.github.minecraftedu.network;

import com.github.minecraftedu.MinecraftEduMod;
import com.github.minecraftedu.commands.CommandExecutor;
import com.github.minecraftedu.multiplayer.ClientSession;
import com.github.minecraftedu.multiplayer.ConnectionManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * WebSocketメッセージハンドラー（マルチプレイヤー対応版）
 */
public class MinecraftWebSocketHandler {

    private final MinecraftServer minecraftServer;
    private final ConnectionManager connectionManager;
    private final SimpleWebSocketServer webSocketServer;
    private final SimpleWebSocketServer.WebSocketConnection wsConnection;
    private final Gson gson;
    private final CommandExecutor commandExecutor;
    private String sessionId;

    public MinecraftWebSocketHandler(
        MinecraftServer minecraftServer,
        ConnectionManager connectionManager,
        SimpleWebSocketServer webSocketServer,
        SimpleWebSocketServer.WebSocketConnection wsConnection
    ) {
        this.minecraftServer = minecraftServer;
        this.connectionManager = connectionManager;
        this.webSocketServer = webSocketServer;
        this.wsConnection = wsConnection;
        this.gson = new Gson();
        this.commandExecutor = new CommandExecutor(minecraftServer);
    }

    public String handleMessage(String request) {
        MinecraftEduMod.LOGGER.debug("Received: " + request);

        try {
            JsonObject message = gson.fromJson(request, JsonObject.class);
            return processMessage(message);
        } catch (Exception e) {
            MinecraftEduMod.LOGGER.error("Error processing message", e);
            return createError("INTERNAL_ERROR", "Failed to process message: " + e.getMessage());
        }
    }

    private String processMessage(JsonObject message) {
        String type = message.get("type").getAsString();

        switch (type) {
            case "connect":
                return handleConnect(message);

            case "command":
                return handleCommand(message);

            case "query":
                return handleQuery(message);

            case "heartbeat":
                return handleHeartbeat();

            default:
                return createError("UNKNOWN_TYPE", "Unknown message type: " + type);
        }
    }

    private String handleConnect(JsonObject message) {
        JsonObject payload = message.getAsJsonObject("payload");
        String clientName = payload.has("clientName") ? payload.get("clientName").getAsString() : "Unknown";
        String token = payload.has("token") ? payload.get("token").getAsString() : "";

        // ConnectionManagerで認証・接続処理
        ConnectionManager.ConnectionResult result = connectionManager.handleNewConnection(clientName, token);

        if (!result.isSuccess()) {
            // 接続失敗
            return createError("CONNECTION_FAILED", result.getMessage());
        }

        // 接続成功
        sessionId = result.getSessionId();
        ClientSession session = result.getSession();

        // WebSocket接続を登録
        String connectionId = wsConnection.socket.getRemoteSocketAddress().toString();
        webSocketServer.registerWebSocketConnection(connectionId, sessionId);

        MinecraftEduMod.LOGGER.info(String.format(
            "Client authenticated: %s (%s) - Session: %s",
            session.getClientName(),
            session.getRole().name(),
            sessionId.substring(0, 8)
        ));

        // 接続成功レスポンス
        JsonObject response = new JsonObject();
        response.addProperty("version", "1.0");
        response.addProperty("messageId", UUID.randomUUID().toString());
        response.addProperty("timestamp", System.currentTimeMillis());
        response.addProperty("sessionId", sessionId);
        response.addProperty("type", "connect_response");

        if (message.has("messageId")) {
            response.addProperty("requestId", message.get("messageId").getAsString());
        }

        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("success", true);
        responsePayload.addProperty("sessionId", sessionId);
        responsePayload.addProperty("clientName", session.getClientName());
        responsePayload.addProperty("role", session.getRole().name());

        // 権限リストを追加
        JsonObject permissions = new JsonObject();
        session.getPermissions().forEach(perm ->
            permissions.addProperty(perm.name(), perm.getDescription())
        );
        responsePayload.add("permissions", permissions);

        // サーバー情報
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("version", "0.1.0");
        serverInfo.addProperty("minecraftVersion", "1.20.1");
        serverInfo.addProperty("maxClients", 10);
        serverInfo.addProperty("currentClients", connectionManager.getActiveConnectionCount());
        responsePayload.add("serverInfo", serverInfo);

        response.add("payload", responsePayload);

        // 他のクライアントに接続通知をブロードキャスト
        broadcastEvent("client_connected", createClientEvent(session));

        return gson.toJson(response);
    }

    private String handleCommand(JsonObject message) {
        if (sessionId == null) {
            return createError("NOT_AUTHENTICATED", "セッションが確立されていません");
        }

        // セッション取得
        ClientSession session = connectionManager.getConnection(sessionId)
            .map(conn -> conn.getSession())
            .orElse(null);

        if (session == null) {
            return createError("SESSION_NOT_FOUND", "セッションが見つかりません");
        }

        // ハートビート更新
        connectionManager.updateHeartbeat(sessionId);

        JsonObject payload = message.getAsJsonObject("payload");
        String action = payload.get("action").getAsString();
        JsonObject params = payload.has("params") ? payload.getAsJsonObject("params") : new JsonObject();

        MinecraftEduMod.LOGGER.info(String.format("[%s] Executing command: %s",
            session.getClientName(), action));

        try {
            // コマンド実行（権限チェック込み）
            boolean success = commandExecutor.execute(action, params, session);

            // レスポンス
            JsonObject response = new JsonObject();
            response.addProperty("version", "1.0");
            response.addProperty("messageId", UUID.randomUUID().toString());
            response.addProperty("timestamp", System.currentTimeMillis());
            response.addProperty("sessionId", sessionId);
            response.addProperty("type", "command_response");

            if (message.has("messageId")) {
                response.addProperty("requestId", message.get("messageId").getAsString());
            }

            JsonObject responsePayload = new JsonObject();
            responsePayload.addProperty("success", success);
            responsePayload.addProperty("action", action);

            if (success) {
                JsonObject result = commandExecutor.getLastResult();
                if (result.size() == 0) {
                    result.addProperty("message", "Command executed successfully");
                }
                responsePayload.add("result", result);

                // 操作通知をブロードキャスト
                broadcastCommandNotification(session, action, params);
            } else {
                String errorMsg = commandExecutor.getLastError();
                responsePayload.addProperty("errorCode", "COMMAND_FAILED");
                responsePayload.addProperty("errorMessage", errorMsg != null ? errorMsg : "Failed to execute command");
            }

            response.add("payload", responsePayload);
            return gson.toJson(response);

        } catch (Exception e) {
            MinecraftEduMod.LOGGER.error("Command execution error", e);
            return createError("COMMAND_FAILED", e.getMessage());
        }
    }

    private String handleQuery(JsonObject message) {
        if (sessionId == null) {
            return createError("NOT_AUTHENTICATED", "セッションが確立されていません");
        }

        // セッション取得
        ClientSession session = connectionManager.getConnection(sessionId)
            .map(conn -> conn.getSession())
            .orElse(null);

        if (session == null) {
            return createError("SESSION_NOT_FOUND", "セッションが見つかりません");
        }

        // ハートビート更新
        connectionManager.updateHeartbeat(sessionId);

        JsonObject payload = message.getAsJsonObject("payload");
        String action = payload.get("action").getAsString();
        JsonObject params = payload.has("params") ? payload.getAsJsonObject("params") : new JsonObject();

        MinecraftEduMod.LOGGER.info(String.format("[%s] Executing query: %s",
            session.getClientName(), action));

        try {
            // クエリ実行
            boolean success = commandExecutor.execute(action, params, session);

            JsonObject response = new JsonObject();
            response.addProperty("version", "1.0");
            response.addProperty("messageId", UUID.randomUUID().toString());
            response.addProperty("timestamp", System.currentTimeMillis());
            response.addProperty("sessionId", sessionId);
            response.addProperty("type", "query_response");

            if (message.has("messageId")) {
                response.addProperty("requestId", message.get("messageId").getAsString());
            }

            JsonObject responsePayload = new JsonObject();
            responsePayload.addProperty("success", success);
            responsePayload.addProperty("action", action);

            if (success) {
                JsonObject result = commandExecutor.getLastResult();
                if (result.size() == 0) {
                    result.addProperty("message", "Query executed successfully");
                }
                responsePayload.add("result", result);
            } else {
                String errorMsg = commandExecutor.getLastError();
                responsePayload.addProperty("errorCode", "QUERY_FAILED");
                responsePayload.addProperty("errorMessage", errorMsg != null ? errorMsg : "Failed to execute query");
            }

            response.add("payload", responsePayload);
            return gson.toJson(response);

        } catch (Exception e) {
            MinecraftEduMod.LOGGER.error("Query execution error", e);
            return createError("QUERY_FAILED", e.getMessage());
        }
    }

    private String handleHeartbeat() {
        if (sessionId != null) {
            connectionManager.updateHeartbeat(sessionId);
        }

        JsonObject response = new JsonObject();
        response.addProperty("version", "1.0");
        response.addProperty("messageId", UUID.randomUUID().toString());
        response.addProperty("timestamp", System.currentTimeMillis());
        response.addProperty("sessionId", sessionId);
        response.addProperty("type", "heartbeat");

        JsonObject payload = new JsonObject();
        payload.addProperty("serverTime", System.currentTimeMillis());
        payload.addProperty("activeClients", connectionManager.getActiveConnectionCount());
        response.add("payload", payload);

        return gson.toJson(response);
    }

    private String createError(String errorCode, String errorMessage) {
        JsonObject response = new JsonObject();
        response.addProperty("version", "1.0");
        response.addProperty("messageId", UUID.randomUUID().toString());
        response.addProperty("timestamp", System.currentTimeMillis());
        response.addProperty("sessionId", sessionId);
        response.addProperty("type", "error");

        JsonObject payload = new JsonObject();
        payload.addProperty("errorCode", errorCode);
        payload.addProperty("errorMessage", errorMessage);
        response.add("payload", payload);

        return gson.toJson(response);
    }

    /**
     * イベントをすべてのクライアントにブロードキャスト
     */
    private void broadcastEvent(String eventType, JsonObject eventData) {
        JsonObject event = new JsonObject();
        event.addProperty("version", "1.0");
        event.addProperty("messageId", UUID.randomUUID().toString());
        event.addProperty("timestamp", System.currentTimeMillis());
        event.addProperty("type", "event");

        JsonObject payload = new JsonObject();
        payload.addProperty("eventType", eventType);
        payload.add("data", eventData);
        event.add("payload", payload);

        String message = gson.toJson(event);
        webSocketServer.broadcastMessageExcept(sessionId, message);
    }

    /**
     * クライアント情報のイベントデータを作成
     */
    private JsonObject createClientEvent(ClientSession session) {
        JsonObject data = new JsonObject();
        data.addProperty("sessionId", session.getSessionId());
        data.addProperty("clientName", session.getClientName());
        data.addProperty("role", session.getRole().name());
        return data;
    }

    /**
     * コマンド実行通知をブロードキャスト
     */
    private void broadcastCommandNotification(ClientSession session, String action, JsonObject params) {
        // Minecraftのチャットに通知を表示
        String notificationMessage = createNotificationMessage(session, action, params);
        if (notificationMessage != null) {
            commandExecutor.sendChatMessage(notificationMessage);
        }
    }

    /**
     * コマンドに応じた通知メッセージを生成
     */
    private String createNotificationMessage(ClientSession session, String action, JsonObject params) {
        String clientName = session.getClientName();

        switch (action) {
            case "setBlock":
                if (params.has("x") && params.has("y") && params.has("z")) {
                    return String.format("§e[%s] がブロックを配置しました (%d, %d, %d)",
                        clientName,
                        params.get("x").getAsInt(),
                        params.get("y").getAsInt(),
                        params.get("z").getAsInt()
                    );
                }
                break;

            case "fillBlocks":
                return String.format("§e[%s] が範囲ブロックを配置しました", clientName);

            case "summonEntity":
                if (params.has("entityType")) {
                    return String.format("§e[%s] が %s を召喚しました",
                        clientName,
                        params.get("entityType").getAsString()
                    );
                }
                break;

            case "teleport":
                return String.format("§e[%s] がテレポートしました", clientName);

            case "setWeather":
                if (params.has("weather")) {
                    return String.format("§6[%s] が天気を %s に変更しました",
                        clientName,
                        params.get("weather").getAsString()
                    );
                }
                break;

            case "setTime":
                return String.format("§6[%s] が時刻を変更しました", clientName);

            case "clearArea":
                return String.format("§6[%s] がエリアをクリアしました", clientName);

            case "chat":
                // チャットメッセージは既にCommandExecutorで処理されるため、ここでは通知不要
                return null;
        }

        return null;
    }
}
