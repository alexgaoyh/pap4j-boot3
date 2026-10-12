package cn.net.pap.example.apitester.postman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 通过 Postman 的 DevTools(CDP) 调试端口，读取本机 Postman 里指定集合的全部接口定义。
 *
 * 原理：
 *   1. 从 %APPDATA%\Postman\DevToolsActivePort 拿到调试端口号；
 *   2. GET http://127.0.0.1:&lt;port&gt;/json/list 找到 Postman 页面 target；
 *   3. 连上该 target 的 WebSocket，执行 Runtime.evaluate；
 *   4. 在页面上下文里读 IndexedDB 的 object store "v3-collections"，
 *      按 parentId/items 把扁平的实体表拼成集合树；
 *   5. 展平成 [{ name, curl }] 写到本地文件。
 *
 * 前提：Postman 正在运行（且开了远程调试端口，一般默认就有），否则该用例会被跳过。
 */
public class PostmanCollectionReaderTest {

    private static final Logger log = LoggerFactory.getLogger(PostmanCollectionReaderTest.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String COLLECTION_NAME = "pap4j-boot3";

    private static final Path OUTPUT = Paths.get(System.getProperty("java.io.tmpdir"), "postman-collection.json");

    /** 在页面里执行的 JS：读 IndexedDB 并把集合拼成树。 __NAME__ 会被替换成集合名。 */
    private static final String JS = """
        (async () => {
          const COLLECTION_NAME = '__NAME__';

          function open(name) {
            return new Promise((res, rej) => {
              const r = indexedDB.open(name);
              r.onsuccess = () => res(r.result);
              r.onerror = () => rej(r.error);
            });
          }
          function getAll(db, store) {
            return new Promise((res, rej) => {
              const tx = db.transaction(store, 'readonly');
              const s = tx.objectStore(store);
              const rk = s.getAllKeys();
              const rv = s.getAll();
              let keys, vals;
              rk.onsuccess = () => { keys = rk.result; if (vals) res([keys, vals]); };
              rv.onsuccess = () => { vals = rv.result; if (keys) res([keys, vals]); };
              rk.onerror = () => rej(rk.error);
              rv.onerror = () => rej(rv.error);
            });
          }

          // shell 单引号转义
          const q = s => "'" + String(s == null ? '' : s).replace(/'/g, "'\\''") + "'";

          // 由内部实体拼一条 curl（partial 实体只有 descriptor/url）
          function toCurl(v) {
            const method = (v.method || v.descriptor || 'GET').toUpperCase();
            const parts = ['curl'];
            if (method !== 'GET') parts.push('-X ' + method);
            parts.push(q(v.url));
            (v.headers || []).forEach(h => {
              if (h && !h.disabled && h.key) parts.push('-H ' + q(h.key + ': ' + (h.value || '')));
            });
            const b = v.body;
            if (b && b.mode === 'raw' && b.raw) {
              parts.push('--data-raw ' + q(b.raw));
            } else if (b && b.mode === 'urlencoded' && Array.isArray(b.urlencoded)) {
              const d = b.urlencoded.filter(x => x && !x.disabled && x.key)
                .map(x => encodeURIComponent(x.key) + '=' + encodeURIComponent(x.value || '')).join('&');
              if (d) parts.push('--data-raw ' + q(d));
            }
            return parts.join(' ');
          }

          // 找到 v3-collections 这个 IndexedDB 库（名字里带 -v3-collections）
          const dbs = await indexedDB.databases();
          const target = dbs.find(d => d.name && d.name.indexOf('v3-collections') !== -1);
          if (!target) return JSON.stringify({ error: '未找到 v3-collections 数据库', dbs: dbs.map(d => d.name) }, null, 2);

          const db = await open(target.name);
          const [keys, vals] = await getAll(db, 'v3-collections');

          const map = new Map();
          for (let i = 0; i < keys.length; i++) {
            const v = vals[i] && vals[i].value ? vals[i].value : vals[i];
            map.set(keys[i], v);
          }

          // 展平成 [{ name: '集合 / 目录 / 接口', curl: '...' }]
          const out = [];
          function walk(node, path) {
            const v = map.get(node.id);
            if (!v) return;
            const name = path ? path + ' / ' + v.name : v.name;
            if (v.$kind === 'collection' || v.$kind === 'folder') {
              (v.items || []).forEach(child => walk(child, name));
            } else if (v.$kind === 'http-request') {
              out.push({ name: name, curl: toCurl(v) });
            }
          }

          let rootId = null;
          for (const [k, v] of map) {
            if (v && v.$kind === 'collection' && v.name === COLLECTION_NAME) { rootId = k; break; }
          }
          if (!rootId) {
            const all = [];
            for (const [k, v] of map) if (v && v.$kind === 'collection') all.push({ id: k, name: v.name });
            return JSON.stringify({ error: '未找到集合: ' + COLLECTION_NAME, collections: all }, null, 2);
          }
          walk({ id: rootId }, '');
          return JSON.stringify(out, null, 2);
        })()
        """;

    @Test
    public void readCollection() throws Exception {
        String appData = System.getenv("APPDATA");
        Assumptions.assumeTrue(appData != null, "跳过：环境变量 APPDATA 不存在");

        Path portFile = Paths.get(appData, "Postman", "DevToolsActivePort");
        Assumptions.assumeTrue(Files.exists(portFile),
                "跳过：找不到 " + portFile + "，请确认 Postman 正在运行（且开启了远程调试端口）");

        int port = Integer.parseInt(Files.readString(portFile).trim().split("\\R")[0].trim());
        log.info("[1/3] DevTools 端口 = {}", port);

        String wsUrl = findPageWebSocket(port, COLLECTION_NAME);
        log.info("[2/3] 页面 target = {}", wsUrl);

        String json = evaluate(wsUrl, JS.replace("__NAME__", COLLECTION_NAME));
        Files.writeString(OUTPUT, json, StandardCharsets.UTF_8);
        JsonNode arr = MAPPER.readTree(json);
        log.info("[3/3] 已写入 {}（{} 个接口）", OUTPUT.toAbsolutePath(), arr.isArray() ? arr.size() : 0);
    }

    /** 从 /json/list 里挑出 desktop.postman.com 的页面 target，优先标题匹配集合名的那个。 */
    private static String findPageWebSocket(int port, String collectionName) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode arr = MAPPER.readTree(resp.body());

        String fallback = null;
        for (JsonNode o : arr) {
            if (!"page".equals(opt(o, "type"))) continue;
            String ws = opt(o, "webSocketDebuggerUrl");
            if (ws == null) continue;
            String url = opt(o, "url");
            String title = opt(o, "title");
            if (url != null && url.contains("desktop.postman.com")) fallback = ws;
            if (title != null && (title.contains(collectionName) || title.contains("Postman"))) return ws;
        }
        if (fallback == null) throw new IllegalStateException("未找到 Postman 页面 target，请确认 Postman 已打开");
        return fallback;
    }

    /** 通过 CDP WebSocket 执行 JS 并返回调用结果。 */
    private static String evaluate(String wsUrl, String expression) throws Exception {
        CompletableFuture<String> done = new CompletableFuture<>();
        HttpClient client = HttpClient.newHttpClient();

        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder buf = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                ObjectNode params = MAPPER.createObjectNode();
                params.put("expression", expression);
                params.put("awaitPromise", true);   // JS 返回 Promise，等待它 resolve
                params.put("returnByValue", true);  // 用值而不是对象引用返回
                ObjectNode req = MAPPER.createObjectNode();
                req.put("id", 1);
                req.put("method", "Runtime.evaluate");
                req.set("params", params);
                webSocket.sendText(req.toString(), true);
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buf.append(data);
                if (last) {
                    String msg = buf.toString();
                    buf.setLength(0);
                    try {
                        JsonNode o = MAPPER.readTree(msg);
                        if (o.has("id") && o.get("id").asInt() == 1) {
                            JsonNode result = o.get("result");
                            if (result != null && result.has("result")) {
                                JsonNode value = result.get("result").get("value");
                                done.complete(value == null || value.isNull() ? null : value.asText());
                            } else {
                                done.completeExceptionally(new RuntimeException("CDP 返回异常: " + msg));
                            }
                        }
                    } catch (Exception ignore) {
                        // 不是我们关心的消息，忽略
                    }
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                done.completeExceptionally(error);
            }
        };

        client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(wsUrl), listener)
                .join();

        return done.get(60, TimeUnit.SECONDS);
    }

    private static String opt(JsonNode node, String key) {
        JsonNode e = node.get(key);
        return (e == null || e.isNull()) ? null : e.asText();
    }
}
