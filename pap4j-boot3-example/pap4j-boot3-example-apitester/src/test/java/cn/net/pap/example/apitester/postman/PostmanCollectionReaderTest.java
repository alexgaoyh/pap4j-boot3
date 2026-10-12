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

    private static final String VAR_OVERRIDES = buildVarOverrides();

    private static String buildVarOverrides() {
        ObjectNode node = MAPPER.createObjectNode();
        for (String pair : System.getProperty("postman.vars", "").split("[;,]")) {
            int i = pair.indexOf('=');
            if (i > 0) node.put(pair.substring(0, i).trim(), pair.substring(i + 1).trim());
        }
        return node.toString();
    }

    /** 在页面里执行的 JS：读 IndexedDB，展平成 [{ name, curl }]。 __NAME__ 会被替换成集合名。 */
    private static final String JS = """
        (async () => {
          const COLLECTION_NAME = '__NAME__';
          const OVERRIDES = __VARS__;   // -Dpostman.vars 注入的变量，优先级最高
          const SQ = String.fromCharCode(39);   // '
          const BS = String.fromCharCode(92);   // backslash

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
          const q = s => SQ + String(s == null ? '' : s).split(SQ).join(SQ + BS + SQ + SQ) + SQ;

          // 用变量表把 {{var}} 替换成实际值（解析不了就保留原样）
          const resolve = (s, vars) => String(s == null ? '' : s)
            .replace(/\\{\\{([^}]+)\\}\\}/g, (m, k) => vars[k] ? vars[k] : m);

          // 把单独的 queryParams 合并进 url（url 里已有的不重复）
          function withQuery(url, queryParams) {
            const enabled = (queryParams || []).filter(p => p && !p.disabled && p.key);
            if (!enabled.length) return url;
            const existing = new Set((url.split('?')[1] || '').split('&').filter(Boolean)
              .map(p => decodeURIComponent(p.split('=')[0])));
            const extra = enabled.filter(p => !existing.has(p.key))
              .map(p => encodeURIComponent(p.key) + '=' + encodeURIComponent(p.value == null ? '' : p.value));
            return extra.length ? url + (url.indexOf('?') === -1 ? '?' : '&') + extra.join('&') : url;
          }

          // 请求体 -> curl data 参数（兼容 {type,content} 与 v2 的 {mode,raw} / 表单）
          function dataArgs(body, vars) {
            if (!body) return [];
            if (typeof body.content === 'string' && body.content !== '') return ['--data-raw ' + q(resolve(body.content, vars))];
            if (typeof body.raw === 'string' && body.raw !== '') return ['--data-raw ' + q(resolve(body.raw, vars))];
            if (Array.isArray(body.urlencoded)) {
              const d = body.urlencoded.filter(x => x && !x.disabled && x.key)
                .map(x => encodeURIComponent(x.key) + '=' + encodeURIComponent(x.value == null ? '' : x.value)).join('&');
              return d ? ['--data-raw ' + q(d)] : [];
            }
            if (Array.isArray(body.formdata)) {
              return body.formdata.filter(x => x && !x.disabled && x.key)
                .map(x => x.type === 'file' ? '-F ' + q(x.key + '=@' + (x.src || '')) : '-F ' + q(x.key + '=' + (x.value || '')));
            }
            return [];
          }

          // 由内部实体拼一条可执行 curl
          function toCurl(v, vars) {
            const method = (v.method || v.descriptor || 'GET').toUpperCase();
            const url = typeof v.url === 'string' ? v.url : (v.url && v.url.raw ? v.url.raw : '');
            const parts = ['curl'];
            if (method !== 'GET') parts.push('-X ' + method);
            parts.push(q(withQuery(resolve(url, vars), v.queryParams)));
            (v.headers || []).forEach(h => {
              if (h && !h.disabled && h.key) parts.push('-H ' + q(resolve(h.key + ': ' + (h.value || ''), vars)));
            });
            dataArgs(v.body, vars).forEach(a => parts.push(a));
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

          let rootId = null;
          for (const [k, v] of map) {
            if (v && v.$kind === 'collection' && v.name === COLLECTION_NAME) { rootId = k; break; }
          }
          if (!rootId) {
            const all = [];
            for (const [k, v] of map) if (v && v.$kind === 'collection') all.push({ id: k, name: v.name });
            return JSON.stringify({ error: '未找到集合: ' + COLLECTION_NAME, collections: all }, null, 2);
          }

          // 变量表：环境变量兜底，集合变量优先
          const vars = {};
          const addVars = (list, overwrite) => (list || []).forEach(x => {
            if (x && x.key && x.disabled !== true && x.enabled !== false && x.value != null && x.value !== ''
                && (overwrite || vars[x.key] == null)) vars[x.key] = x.value;
          });
          const envDb = dbs.find(d => d.name && d.name.indexOf('-environments') !== -1);
          if (envDb) {
            const edb = await open(envDb.name);
            const [ek, ev] = await getAll(edb, 'environments');
            for (let i = 0; i < ek.length; i++) {
              const e = ev[i] && ev[i].value ? ev[i].value : ev[i];
              addVars(e && e.values, false);
            }
          }
          addVars(map.get(rootId).variables, true);
          addVars(Object.keys(OVERRIDES).map(k => ({ key: k, value: OVERRIDES[k] })), true);

          // 展平成 [{ name: '集合 / 目录 / 接口', curl: '...' }]
          const out = [];
          (function walk(node, path) {
            const v = map.get(node.id);
            if (!v) return;
            const name = path ? path + ' / ' + v.name : v.name;
            if (v.$kind === 'collection' || v.$kind === 'folder') {
              (v.items || []).forEach(child => walk(child, name));
            } else if (v.$kind === 'http-request') {
              out.push({ name: name, curl: toCurl(v, vars) });
            }
          })({ id: rootId }, '');

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

        String json = evaluate(wsUrl, JS.replace("__NAME__", COLLECTION_NAME).replace("__VARS__", VAR_OVERRIDES));
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
