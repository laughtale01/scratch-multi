package com.github.minecraftedu.multiplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * マルチプレイヤー設定を管理するクラス
 */
public class MultiplayerConfig {

    @SerializedName("enabled")
    private boolean enabled = true;

    @SerializedName("maxClients")
    private int maxClients = 10;

    @SerializedName("autoGenerateTokens")
    private boolean autoGenerateTokens = true;

    @SerializedName("clients")
    private List<ClientConfig> clients = new ArrayList<>();

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxClients() {
        return maxClients;
    }

    public boolean isAutoGenerateTokens() {
        return autoGenerateTokens;
    }

    public List<ClientConfig> getClients() {
        return clients;
    }

    /**
     * クライアント設定
     */
    public static class ClientConfig {
        @SerializedName("name")
        private String name;

        @SerializedName("token")
        private String token;

        @SerializedName("role")
        private String role = "GUEST";

        public ClientConfig() {
        }

        public ClientConfig(String name, String token, String role) {
            this.name = name;
            this.token = token;
            this.role = role;
        }

        public String getName() {
            return name;
        }

        public String getToken() {
            return token;
        }

        public Role getRole() {
            return Role.fromString(role);
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    /**
     * 設定ファイルを読み込む
     */
    public static MultiplayerConfig load(Path configPath) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            if (Files.exists(configPath)) {
                // 既存の設定を読み込む
                try (Reader reader = Files.newBufferedReader(configPath)) {
                    MultiplayerConfig config = gson.fromJson(reader, MultiplayerConfig.class);

                    // autoGenerateTokensがtrueの場合、トークンを自動生成
                    if (config.isAutoGenerateTokens()) {
                        boolean updated = false;
                        for (ClientConfig client : config.getClients()) {
                            if (client.getToken() == null || client.getToken().isEmpty()) {
                                client.setToken(generateToken());
                                updated = true;
                            }
                        }

                        // トークンを生成した場合は保存
                        if (updated) {
                            config.save(configPath);
                        }
                    }

                    return config;
                }
            } else {
                // デフォルト設定を作成
                MultiplayerConfig defaultConfig = createDefault();
                defaultConfig.save(configPath);
                return defaultConfig;
            }
        } catch (IOException e) {
            System.err.println("Failed to load multiplayer config: " + e.getMessage());
            return createDefault();
        }
    }

    /**
     * 設定ファイルを保存
     */
    public void save(Path configPath) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            // ディレクトリが存在しない場合は作成
            Files.createDirectories(configPath.getParent());

            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(this, writer);
            }

            System.out.println("Multiplayer config saved to: " + configPath);
        } catch (IOException e) {
            System.err.println("Failed to save multiplayer config: " + e.getMessage());
        }
    }

    /**
     * デフォルト設定を作成
     */
    private static MultiplayerConfig createDefault() {
        MultiplayerConfig config = new MultiplayerConfig();
        config.enabled = true;
        config.maxClients = 10;
        config.autoGenerateTokens = true;

        // デフォルトのホストを追加
        config.clients.add(new ClientConfig(
            "ホスト",
            generateToken(),
            "HOST"
        ));

        // サンプルのゲストを追加
        for (int i = 1; i <= 3; i++) {
            config.clients.add(new ClientConfig(
                "ゲスト" + i,
                generateToken(),
                "GUEST"
            ));
        }

        return config;
    }

    /**
     * ランダムなトークンを生成
     * 形式: ABC123 (6文字の英数字)
     */
    private static String generateToken() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder token = new StringBuilder(6);

        for (int i = 0; i < 6; i++) {
            token.append(chars.charAt(random.nextInt(chars.length())));
        }

        return token.toString();
    }

    /**
     * トークンから クライアント設定を検索
     */
    public ClientConfig findClientByToken(String token) {
        if (token == null) {
            return null;
        }

        for (ClientConfig client : clients) {
            if (token.equals(client.getToken())) {
                return client;
            }
        }

        return null;
    }

    /**
     * 名前からクライアント設定を検索
     */
    public ClientConfig findClientByName(String name) {
        if (name == null) {
            return null;
        }

        for (ClientConfig client : clients) {
            if (name.equals(client.getName())) {
                return client;
            }
        }

        return null;
    }

    /**
     * 接続情報を表示（起動時にコンソールに出力）
     */
    public void printConnectionInfo() {
        System.out.println("=".repeat(50));
        System.out.println("  MinecraftEdu - マルチプレイヤー設定");
        System.out.println("=".repeat(50));
        System.out.println("ステータス: " + (enabled ? "有効" : "無効"));
        System.out.println("最大クライアント数: " + maxClients);
        System.out.println("自動トークン生成: " + (autoGenerateTokens ? "有効" : "無効"));
        System.out.println();
        System.out.println("接続情報:");
        System.out.println("-".repeat(50));

        for (ClientConfig client : clients) {
            System.out.println(String.format("  名前: %-15s 役割: %-6s トークン: %s",
                client.getName(),
                client.getRole().name(),
                client.getToken()
            ));
        }

        System.out.println("=".repeat(50));
        System.out.println();
    }
}
