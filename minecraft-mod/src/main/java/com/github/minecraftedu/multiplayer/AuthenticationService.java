package com.github.minecraftedu.multiplayer;

/**
 * クライアント認証サービス
 */
public class AuthenticationService {

    private final MultiplayerConfig config;

    public AuthenticationService(MultiplayerConfig config) {
        this.config = config;
    }

    /**
     * トークンで認証
     */
    public AuthResult authenticate(String clientName, String token) {
        if (token == null || token.trim().isEmpty()) {
            return AuthResult.failure("トークンが指定されていません");
        }

        // トークンから設定を検索
        MultiplayerConfig.ClientConfig clientConfig = config.findClientByToken(token);

        if (clientConfig == null) {
            return AuthResult.failure("無効なトークンです");
        }

        // 名前が指定されている場合は照合
        if (clientName != null && !clientName.trim().isEmpty()) {
            if (!clientName.equals(clientConfig.getName())) {
                return AuthResult.failure("名前とトークンが一致しません");
            }
        }

        // 認証成功
        return AuthResult.success(
            clientConfig.getName(),
            clientConfig.getRole()
        );
    }

    /**
     * トークンの検証のみ
     */
    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        return config.findClientByToken(token) != null;
    }

    /**
     * 認証結果
     */
    public static class AuthResult {
        private final boolean success;
        private final String message;
        private final String clientName;
        private final Role role;

        private AuthResult(boolean success, String message, String clientName, Role role) {
            this.success = success;
            this.message = message;
            this.clientName = clientName;
            this.role = role;
        }

        public static AuthResult success(String clientName, Role role) {
            return new AuthResult(true, "認証成功", clientName, role);
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, message, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getClientName() {
            return clientName;
        }

        public Role getRole() {
            return role;
        }

        @Override
        public String toString() {
            if (success) {
                return String.format("AuthResult{success=true, name='%s', role=%s}",
                    clientName, role);
            } else {
                return String.format("AuthResult{success=false, message='%s'}", message);
            }
        }
    }
}
