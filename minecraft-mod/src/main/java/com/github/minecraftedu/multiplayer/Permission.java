package com.github.minecraftedu.multiplayer;

import java.util.Set;
import java.util.EnumSet;

/**
 * 操作権限の定義
 */
public enum Permission {
    // 基本操作
    CHAT("チャット送信"),
    PLACE_BLOCK("ブロック配置"),
    GET_BLOCK("ブロック取得"),
    FILL_BLOCKS("範囲ブロック配置"),

    // エンティティ操作
    SUMMON_ENTITY("エンティティ召喚"),

    // プレイヤー操作
    TELEPORT("テレポート"),
    GET_POSITION("位置取得"),
    GET_FACING("向き取得"),

    // ワールド操作（HOST限定）
    SET_WEATHER("天気変更"),
    SET_TIME("時刻変更"),
    SET_GAMEMODE("ゲームモード変更"),
    CLEAR_AREA("エリアクリア"),
    CLEAR_ENTITIES("エンティティ全削除"),
    SET_GAMERULE("ゲームルール変更"),

    // 管理操作（HOST限定）
    MANAGE_CLIENTS("クライアント管理"),
    EXECUTE_COMMAND("任意コマンド実行");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 役割に応じた権限セットを取得
     */
    public static Set<Permission> getPermissionsForRole(Role role) {
        switch (role) {
            case HOST:
                // ホストはすべての権限を持つ
                return EnumSet.allOf(Permission.class);

            case GUEST:
                // ゲストは基本的な操作のみ
                return EnumSet.of(
                    CHAT,
                    PLACE_BLOCK,
                    GET_BLOCK,
                    FILL_BLOCKS,
                    SUMMON_ENTITY,
                    TELEPORT,
                    GET_POSITION,
                    GET_FACING
                );

            default:
                return EnumSet.noneOf(Permission.class);
        }
    }
}
