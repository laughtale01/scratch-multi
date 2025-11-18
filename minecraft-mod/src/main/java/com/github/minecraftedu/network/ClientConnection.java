package com.github.minecraftedu.network;

import com.github.minecraftedu.multiplayer.ClientSession;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * クライアント接続を表すクラス
 * WebSocketハンドラーとの通信を管理
 */
public class ClientConnection {

    private final ClientSession session;
    private final Gson gson;

    // 送信メッセージキュー
    private final BlockingQueue<JsonObject> messageQueue;

    // WebSocketハンドラーへの参照（後で設定）
    private MessageSender messageSender;

    public ClientConnection(ClientSession session) {
        this.session = session;
        this.gson = new Gson();
        this.messageQueue = new LinkedBlockingQueue<>();
    }

    /**
     * メッセージ送信インターフェース
     * WebSocketハンドラーから設定される
     */
    public interface MessageSender {
        void send(String message);
    }

    /**
     * メッセージセンダーを設定
     */
    public void setMessageSender(MessageSender sender) {
        this.messageSender = sender;
    }

    /**
     * メッセージを送信
     */
    public void sendMessage(JsonObject message) {
        if (messageSender != null) {
            String json = gson.toJson(message);
            messageSender.send(json);
        } else {
            // センダーが未設定の場合はキューに追加
            messageQueue.offer(message);
        }
    }

    /**
     * メッセージを送信（String版）
     */
    public void sendMessage(String message) {
        if (messageSender != null) {
            messageSender.send(message);
        }
    }

    /**
     * キューに溜まっているメッセージをフラッシュ
     */
    public void flushQueue() {
        while (!messageQueue.isEmpty() && messageSender != null) {
            JsonObject message = messageQueue.poll();
            if (message != null) {
                sendMessage(message);
            }
        }
    }

    /**
     * セッション取得
     */
    public ClientSession getSession() {
        return session;
    }

    /**
     * 接続がアクティブかチェック
     */
    public boolean isActive() {
        return session.isActive() && messageSender != null;
    }

    @Override
    public String toString() {
        return String.format("ClientConnection{session=%s, active=%s}",
            session, isActive());
    }
}
