package com.github.minecraftedu.multiplayer;

import java.util.Set;
import java.util.UUID;

/**
 * クライアントのセッション情報
 */
public class ClientSession {
    private final String sessionId;
    private final String clientName;
    private final String token;
    private final Role role;
    private final Set<Permission> permissions;
    private final long connectionTime;
    private long lastHeartbeat;
    private boolean active;

    private ClientSession(Builder builder) {
        this.sessionId = builder.sessionId;
        this.clientName = builder.clientName;
        this.token = builder.token;
        this.role = builder.role;
        this.permissions = builder.permissions;
        this.connectionTime = builder.connectionTime;
        this.lastHeartbeat = System.currentTimeMillis();
        this.active = true;
    }

    // Getters
    public String getSessionId() {
        return sessionId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getToken() {
        return token;
    }

    public Role getRole() {
        return role;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public long getConnectionTime() {
        return connectionTime;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public boolean isActive() {
        return active;
    }

    // Setters
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * 権限チェック
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * 複数権限チェック（すべて必要）
     */
    public boolean hasAllPermissions(Permission... perms) {
        for (Permission perm : perms) {
            if (!hasPermission(perm)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builder パターン
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String clientName;
        private String token;
        private Role role;
        private Set<Permission> permissions;
        private long connectionTime;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder clientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            this.permissions = Permission.getPermissionsForRole(role);
            return this;
        }

        public Builder permissions(Set<Permission> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder connectionTime(long connectionTime) {
            this.connectionTime = connectionTime;
            return this;
        }

        public ClientSession build() {
            // デフォルト値の設定
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString();
            }
            if (connectionTime == 0) {
                connectionTime = System.currentTimeMillis();
            }
            if (role == null) {
                role = Role.GUEST;
            }
            if (permissions == null) {
                permissions = Permission.getPermissionsForRole(role);
            }

            return new ClientSession(this);
        }
    }

    @Override
    public String toString() {
        return String.format("ClientSession{name='%s', role=%s, sessionId='%s', active=%s}",
            clientName, role, sessionId, active);
    }
}
