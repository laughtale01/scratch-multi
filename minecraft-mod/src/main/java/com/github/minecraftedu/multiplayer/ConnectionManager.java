package com.github.minecraftedu.multiplayer;

import com.github.minecraftedu.network.ClientConnection;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クライアント接続を管理するマネージャー
 */
public class ConnectionManager {

    // アクティブな接続プール
    private final ConcurrentHashMap<String, ClientConnection> activeConnections;

    // 最大接続数
    private final int maxConnections;

    // サービス
    private final AuthenticationService authService;

    public ConnectionManager(MultiplayerConfig config) {
        this.activeConnections = new ConcurrentHashMap<>();
        this.maxConnections = config.getMaxClients();
        this.authService = new AuthenticationService(config);
    }

    /**
     * 新しいクライアント接続を処理
     */
    public ConnectionResult handleNewConnection(String clientName, String token) {
        // 1. 接続数チェック
        if (activeConnections.size() >= maxConnections) {
            return ConnectionResult.error("サーバーが満員です（最大" + maxConnections + "人）");
        }

        // 2. 認証
        AuthenticationService.AuthResult authResult = authService.authenticate(clientName, token);
        if (!authResult.isSuccess()) {
            return ConnectionResult.error(authResult.getMessage());
        }

        // 3. 既に同じトークンで接続しているかチェック
        for (ClientConnection conn : activeConnections.values()) {
            if (conn.getSession().getToken().equals(token)) {
                return ConnectionResult.error("このトークンは既に使用中です");
            }
        }

        // 4. セッション作成
        String sessionId = UUID.randomUUID().toString();
        ClientSession session = ClientSession.builder()
            .sessionId(sessionId)
            .clientName(authResult.getClientName())
            .token(token)
            .role(authResult.getRole())
            .build();

        // 5. 接続オブジェクト作成
        ClientConnection connection = new ClientConnection(session);
        activeConnections.put(sessionId, connection);

        System.out.println(String.format("[Connection] %s (%s) が接続しました [%s]",
            session.getClientName(),
            session.getRole().getDisplayName(),
            sessionId.substring(0, 8)));

        // 6. 成功レスポンス
        return ConnectionResult.success(sessionId, session);
    }

    /**
     * クライアント切断処理
     */
    public void handleDisconnection(String sessionId) {
        ClientConnection connection = activeConnections.remove(sessionId);
        if (connection != null) {
            ClientSession session = connection.getSession();
            session.setActive(false);

            System.out.println(String.format("[Disconnection] %s (%s) が切断しました [%s]",
                session.getClientName(),
                session.getRole().getDisplayName(),
                sessionId.substring(0, 8)));
        }
    }

    /**
     * セッションIDから接続を取得
     */
    public Optional<ClientConnection> getConnection(String sessionId) {
        return Optional.ofNullable(activeConnections.get(sessionId));
    }

    /**
     * すべてのアクティブセッションを取得
     */
    public List<ClientSession> getActiveSessions() {
        List<ClientSession> sessions = new ArrayList<>();
        for (ClientConnection conn : activeConnections.values()) {
            sessions.add(conn.getSession());
        }
        return sessions;
    }

    /**
     * アクティブな接続数を取得
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    /**
     * すべての接続にメッセージを送信（ブロードキャスト）
     */
    public void broadcastMessage(JsonObject message) {
        for (ClientConnection conn : activeConnections.values()) {
            conn.sendMessage(message);
        }
    }

    /**
     * 特定のセッション以外にメッセージを送信
     */
    public void broadcastMessageExcept(String excludeSessionId, JsonObject message) {
        for (Map.Entry<String, ClientConnection> entry : activeConnections.entrySet()) {
            if (!entry.getKey().equals(excludeSessionId)) {
                entry.getValue().sendMessage(message);
            }
        }
    }

    /**
     * ハートビートチェック（60秒以上応答がない接続を切断）
     */
    public void performHealthCheck() {
        long now = System.currentTimeMillis();
        long timeout = 60_000; // 60秒

        List<String> toDisconnect = new ArrayList<>();

        for (Map.Entry<String, ClientConnection> entry : activeConnections.entrySet()) {
            ClientConnection conn = entry.getValue();
            if (now - conn.getSession().getLastHeartbeat() > timeout) {
                toDisconnect.add(entry.getKey());
            }
        }

        for (String sessionId : toDisconnect) {
            System.out.println("[HealthCheck] タイムアウトによる切断: " + sessionId.substring(0, 8));
            handleDisconnection(sessionId);
        }
    }

    /**
     * ハートビートを更新
     */
    public void updateHeartbeat(String sessionId) {
        ClientConnection conn = activeConnections.get(sessionId);
        if (conn != null) {
            conn.getSession().updateHeartbeat();
        }
    }

    /**
     * 接続結果
     */
    public static class ConnectionResult {
        private final boolean success;
        private final String message;
        private final String sessionId;
        private final ClientSession session;

        private ConnectionResult(boolean success, String message, String sessionId, ClientSession session) {
            this.success = success;
            this.message = message;
            this.sessionId = sessionId;
            this.session = session;
        }

        public static ConnectionResult success(String sessionId, ClientSession session) {
            return new ConnectionResult(true, "接続成功", sessionId, session);
        }

        public static ConnectionResult error(String message) {
            return new ConnectionResult(false, message, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getSessionId() {
            return sessionId;
        }

        public ClientSession getSession() {
            return session;
        }
    }
}
