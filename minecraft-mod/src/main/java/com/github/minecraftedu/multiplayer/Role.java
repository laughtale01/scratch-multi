package com.github.minecraftedu.multiplayer;

/**
 * ユーザーの役割を定義
 * Phase 1ではHOSTとGUESTの2つのみ
 */
public enum Role {
    /**
     * ホスト - すべての権限を持つ
     */
    HOST("ホスト", 100),

    /**
     * ゲスト - 基本的な操作のみ可能
     */
    GUEST("ゲスト", 50);

    private final String displayName;
    private final int priority;

    Role(String displayName, int priority) {
        this.displayName = displayName;
        this.priority = priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * この役割が他の役割より優先度が高いかチェック
     */
    public boolean hasHigherPriorityThan(Role other) {
        return this.priority > other.priority;
    }

    /**
     * 文字列から役割を取得
     */
    public static Role fromString(String roleStr) {
        if (roleStr == null) {
            return GUEST; // デフォルト
        }

        try {
            return Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GUEST; // 不明な役割はゲストとして扱う
        }
    }
}
