# マルチプレイヤー機能フラグ戦略

## 目的

将来的に基本版とマルチ版を統合する際、**シングルプレイヤーユーザーがマルチプレイヤー機能をほとんど意識しなくて済む**ようにする設計戦略。

## 基本方針

### 原則1: デフォルトはシングルプレイヤーモード
- 初めて使うユーザーはマルチ機能を見ない
- マルチ機能は**明示的に有効化**した場合のみ表示

### 原則2: 段階的な機能公開
- レベル1: 基本ブロックのみ（シングルプレイヤー）
- レベル2: マルチプレイヤーブロック追加（オプトイン）
- レベル3: 高度なマルチ機能（上級者向け）

### 原則3: UIの分離
- マルチ関連のブロックは別カテゴリ
- シングルモードでは非表示

---

## 実装戦略

### A. ビルド時フラグ（推奨）

異なるビルドを作成：

```javascript
// webpack.config.js
const ENABLE_MULTIPLAYER = process.env.ENABLE_MULTIPLAYER === 'true';

module.exports = {
  // ...
  plugins: [
    new webpack.DefinePlugin({
      'process.env.ENABLE_MULTIPLAYER': JSON.stringify(ENABLE_MULTIPLAYER)
    })
  ]
};
```

```javascript
// scratch-vm/src/extensions/scratch3_minecraft/index.js
class Scratch3MinecraftBlocks {
    getInfo() {
        const baseBlocks = this._getBaseBlocks();

        // マルチプレイヤー機能は環境変数で制御
        if (process.env.ENABLE_MULTIPLAYER === 'true') {
            const multiplayerBlocks = this._getMultiplayerBlocks();
            return {
                id: 'minecraft',
                name: 'Minecraft (Multi)',
                blocks: [...baseBlocks, ...multiplayerBlocks]
            };
        }

        return {
            id: 'minecraft',
            name: 'Minecraft',
            blocks: baseBlocks
        };
    }

    _getBaseBlocks() {
        return [
            {
                opcode: 'connect',
                blockType: BlockType.COMMAND,
                text: formatMessage({
                    id: 'minecraft.connect',
                    default: 'Connect to [HOST]:[PORT]'
                }),
                arguments: {
                    HOST: { type: ArgumentType.STRING, defaultValue: 'localhost' },
                    PORT: { type: ArgumentType.NUMBER, defaultValue: 14711 }
                }
            },
            // ... 他の基本ブロック
        ];
    }

    _getMultiplayerBlocks() {
        return [
            '---', // セパレータ
            {
                opcode: 'connectWithAuth',
                blockType: BlockType.COMMAND,
                text: formatMessage({
                    id: 'minecraft.connectWithAuth',
                    default: 'Connect as [NAME] with token [TOKEN]'
                }),
                arguments: {
                    NAME: { type: ArgumentType.STRING, defaultValue: '生徒1' },
                    TOKEN: { type: ArgumentType.STRING, defaultValue: 'STUDENT01' }
                }
            },
            {
                opcode: 'getConnectedUserCount',
                blockType: BlockType.REPORTER,
                text: formatMessage({
                    id: 'minecraft.getConnectedUserCount',
                    default: 'Connected users'
                })
            },
            // ... 他のマルチプレイヤーブロック
        ];
    }
}
```

**デプロイ方法:**
```bash
# シングルプレイヤー版
npm run build
# → https://laughtale01.github.io/Scratch/

# マルチプレイヤー版
ENABLE_MULTIPLAYER=true npm run build
# → https://laughtale01.github.io/scratch-multi/
```

---

### B. ランタイムフラグ（柔軟性重視）

ユーザーが設定画面で切り替え可能：

```javascript
// scratch-vm/src/extensions/scratch3_minecraft/index.js
class Scratch3MinecraftBlocks {
    constructor(runtime) {
        this.runtime = runtime;

        // ローカルストレージから設定を読み込み
        this.multiplayerEnabled = this._loadMultiplayerSetting();
    }

    _loadMultiplayerSetting() {
        if (typeof window !== 'undefined' && window.localStorage) {
            const setting = window.localStorage.getItem('minecraft_multiplayer_enabled');
            return setting === 'true';
        }
        return false; // デフォルトはシングルプレイヤー
    }

    getInfo() {
        const baseBlocks = this._getBaseBlocks();

        if (this.multiplayerEnabled) {
            return {
                id: 'minecraft',
                name: 'Minecraft',
                blocks: [
                    ...baseBlocks,
                    '---',
                    ...this._getMultiplayerBlocks(),
                    '---',
                    this._getMultiplayerToggleBlock()
                ]
            };
        }

        return {
            id: 'minecraft',
            name: 'Minecraft',
            blocks: [
                ...baseBlocks,
                '---',
                this._getMultiplayerToggleBlock()
            ]
        };
    }

    _getMultiplayerToggleBlock() {
        return {
            opcode: 'enableMultiplayer',
            blockType: BlockType.COMMAND,
            text: formatMessage({
                id: 'minecraft.enableMultiplayer',
                default: 'Enable Multiplayer Features'
            }),
            hideFromPalette: this.multiplayerEnabled // 有効化済みなら非表示
        };
    }

    enableMultiplayer() {
        if (typeof window !== 'undefined' && window.localStorage) {
            window.localStorage.setItem('minecraft_multiplayer_enabled', 'true');
            this.multiplayerEnabled = true;

            // 拡張機能をリロード
            this.runtime.emit('EXTENSION_UPDATED');

            return 'Multiplayer features enabled! Please refresh the page.';
        }
    }
}
```

---

### C. サーバーサイド検出（自動判定）

接続先サーバーがマルチプレイヤー対応かどうかで自動判定：

```javascript
class Scratch3MinecraftBlocks {
    async connect(args) {
        const { HOST, PORT } = args;

        try {
            // 接続
            await this._connectToServer(HOST, PORT);

            // サーバー機能を問い合わせ
            const serverInfo = await this._queryServerCapabilities();

            if (serverInfo.multiplayerSupported) {
                // マルチプレイヤー対応サーバー
                this.serverMode = 'multiplayer';
                this._showMultiplayerBlocks();
            } else {
                // シングルプレイヤーサーバー
                this.serverMode = 'single';
                this._hideMultiplayerBlocks();
            }

        } catch (error) {
            // エラーハンドリング
        }
    }

    async _queryServerCapabilities() {
        // サーバーに機能問い合わせ
        const response = await this._sendMessage({
            type: 'query',
            payload: { query: 'capabilities' }
        });

        return response.payload;
    }
}
```

**MOD側の実装:**

```java
// MinecraftWebSocketHandler.java
private void handleQuery(JsonObject payload, ClientConnection connection) {
    String query = payload.get("query").getAsString();

    if ("capabilities".equals(query)) {
        JsonObject response = new JsonObject();
        response.addProperty("multiplayerSupported", true);  // この値で判定
        response.addProperty("maxClients", 10);
        response.addProperty("authenticationRequired", true);

        sendResponse(connection, "query_response", response);
    }
}
```

---

## 推奨実装: ハイブリッド方式

**ビルド時フラグ** + **サーバーサイド検出** の組み合わせ：

### 1. ビルド時に2バージョン作成

```bash
# シングルプレイヤー版（マルチ機能完全除外）
npm run build:single
# → マルチコードがバンドルされない = 軽量

# マルチプレイヤー版（全機能含む）
npm run build:multi
# → マルチコード含む
```

### 2. マルチ版でもデフォルトはシンプル表示

```javascript
// マルチ版でも、接続時にサーバーを検出して動的に表示切替
class Scratch3MinecraftBlocks {
    getInfo() {
        // 初期状態は基本ブロックのみ
        return {
            id: 'minecraft',
            name: 'Minecraft',
            blocks: this._getCurrentBlocks()
        };
    }

    _getCurrentBlocks() {
        const baseBlocks = this._getBaseBlocks();

        // サーバー接続後、マルチ対応サーバーなら追加
        if (this.isConnected && this.serverSupportsMultiplayer) {
            return [...baseBlocks, '---', ...this._getMultiplayerBlocks()];
        }

        return baseBlocks;
    }
}
```

---

## ユーザー体験フロー

### シングルプレイヤーユーザー

```
1. Scratch GUIを開く
   ↓
2. Minecraft拡張を追加
   ↓
3. 「接続」ブロックのみ表示（シンプル！）
   ↓
4. localhost:14711に接続
   ↓
5. ブロック配置、エンティティ召喚など
   ↓
6. マルチ機能は一切見えない ✓
```

### マルチプレイヤーユーザー（教室環境）

```
1. Scratch GUIを開く（マルチ版URL）
   ↓
2. Minecraft拡張を追加
   ↓
3. 基本ブロック表示（まだマルチブロックは見えない）
   ↓
4. 先生がマルチ対応サーバーのアドレスを共有
   ↓
5. 「名前とトークンで接続」ブロックで接続
   ↓
6. 接続成功 → マルチブロックが自動で表示される
   ├─ 接続ユーザー数
   ├─ 自分の名前
   └─ 自分の役割
   ↓
7. クラスメイトと協力してプログラミング ✓
```

---

## MOD側の対応

### multiplayer.json に有効/無効フラグ追加

```json
{
  "enabled": true,
  "maxClients": 10,
  "autoGenerateTokens": true,

  "singlePlayerMode": false,  // ← 新規追加

  "clients": [
    {
      "name": "先生",
      "token": "TEACHER",
      "role": "HOST"
    }
  ]
}
```

### シングルプレイヤーモードの挙動

```java
// MultiplayerConfig.java
public class MultiplayerConfig {
    private boolean enabled;
    private boolean singlePlayerMode;  // 新規
    private int maxClients;
    private List<ClientConfig> clients;

    public boolean isMultiplayerEnabled() {
        // シングルプレイヤーモードならマルチ機能無効
        if (singlePlayerMode) {
            return false;
        }
        return enabled;
    }
}

// MinecraftWebSocketHandler.java
private void handleConnect(JsonObject payload, ClientConnection connection) {
    if (config.isMultiplayerEnabled()) {
        // マルチプレイヤー接続処理（トークン認証あり）
        handleMultiplayerConnect(payload, connection);
    } else {
        // シンプルな接続処理（トークン不要）
        handleSimpleConnect(payload, connection);
    }
}

private void handleSimpleConnect(JsonObject payload, ClientConnection connection) {
    // トークン不要、誰でも接続可能
    String sessionId = UUID.randomUUID().toString();
    ClientSession session = new ClientSession(
        sessionId,
        "Player",  // デフォルト名
        null,      // トークン不要
        Role.HOST, // 全員がHOST権限
        Role.HOST.getPermissions()
    );

    connectionManager.registerSession(connection, session);
    sendConnectResponse(connection, session, true);
}
```

---

## 統合チェックリスト

統合時に確認すべき項目：

### 技術的確認
- [ ] `singlePlayerMode: true` でマルチブロックが表示されない
- [ ] `singlePlayerMode: false` でマルチブロックが表示される
- [ ] シングルモードでもすべての基本機能が動作する
- [ ] マルチモードでトークン認証が機能する
- [ ] パフォーマンス劣化がない

### ユーザー体験
- [ ] 初めてのユーザーがマルチ機能を意識しない
- [ ] マルチ機能の有効化が簡単（1クリックまたは自動）
- [ ] マルチ機能が不要なユーザーは無視できる
- [ ] エラーメッセージが分かりやすい

### ドキュメント
- [ ] README にシングル/マルチの切り替え方法を記載
- [ ] インストールガイドに両モードの説明
- [ ] 教師向けガイドにマルチモード設定手順

---

## 今後の開発指針

### Phase 1: 現在（別々に開発）
- 基本版: シングルプレイヤー機能を充実
- マルチ版: マルチプレイヤー機能を充実

### Phase 2: 統合準備
1. マルチ版に `singlePlayerMode` フラグ実装
2. 基本版の改善をマルチ版に毎日同期（現在進行中）
3. マルチ機能の安定化

### Phase 3: 統合実行
1. マルチ版をメインバージョンとする
2. デフォルト設定を `singlePlayerMode: true` に
3. GitHub Pages は2つ維持:
   - `/Scratch/` → シングル版（軽量ビルド）
   - `/scratch-multi/` → マルチ版（全機能）

### Phase 4: 統合後
- ユーザーフィードバック収集
- 必要に応じて調整
- 長期的にはマルチ版に統一も検討

---

## まとめ

この戦略により：

✅ **現在**: 別々に開発しながら、毎日基本機能を同期
✅ **将来**: スムーズに統合可能
✅ **ユーザー**: シングルユーザーはマルチ機能を意識しない
✅ **柔軟性**: ビルド・ランタイム・サーバー検出の組み合わせ

統合のタイミングは、マルチ機能が十分に安定し、この設計が実装された時点で実行できます。
