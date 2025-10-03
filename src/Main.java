import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String STATIC_DIR = "public";

    // In-memory task store
    private static final Map<Integer, Task> tasks = new ConcurrentHashMap<>();
    private static final AtomicInteger idSeq = new AtomicInteger(1);

    public static void main(String[] args) throws IOException {
        int port = resolvePort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler(STATIC_DIR));
        server.createContext("/api/tasks", new TasksHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Server started at http://localhost:" + server.getAddress().getPort());
    }

    private static int resolvePort() {
        String env = System.getenv("PORT");
        if (env != null) {
            try {
                int p = Integer.parseInt(env);
                if (p > 0 && p < 65536) return p;
            } catch (Exception ignored) {}
        }
        return 0; // Use ephemeral port assigned by OS if not specified
    }

    // Task model
    static class Task {
        int id;
        String title;
        boolean completed;
        long createdAt;

        Task(int id, String title, boolean completed, long createdAt) {
            this.id = id;
            this.title = title;
            this.completed = completed;
            this.createdAt = createdAt;
        }

        String toJson() {
            // Minimal manual JSON serialization
            String safeTitle = title.replace("\\", "\\\\").replace("\"", "\\\"");
            return "{" +
                    "\"id\":" + id + "," +
                    "\"title\":\"" + safeTitle + "\"," +
                    "\"completed\":" + completed + "," +
                    "\"createdAt\":" + createdAt +
                    "}";
        }
    }

    // Handler for static files in /public
    static class StaticHandler implements HttpHandler {
        private final Path baseDir;

        StaticHandler(String baseDir) {
            this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            if (path == null || path.equals("/")) {
                serveFile(exchange, baseDir.resolve("index.html"));
                return;
            }

            // Strip leading '/'
            String rel = path.startsWith("/") ? path.substring(1) : path;
            Path file = baseDir.resolve(rel).normalize();

            // Prevent directory traversal
            if (!file.toAbsolutePath().startsWith(baseDir)) {
                sendText(exchange, 403, "Forbidden");
                return;
            }

            if (Files.exists(file) && !Files.isDirectory(file)) {
                serveFile(exchange, file);
            } else {
                sendText(exchange, 404, "Not Found");
            }
        }

        private void serveFile(HttpExchange exchange, Path file) throws IOException {
            String contentType = contentTypeFor(file.getFileName().toString());
            byte[] data = Files.readAllBytes(file);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private void sendText(HttpExchange exchange, int status, String body) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/plain; charset=utf-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String contentTypeFor(String name) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".html")) return "text/html; charset=utf-8";
            if (lower.endsWith(".css")) return "text/css; charset=utf-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            return "application/octet-stream";
        }
    }

    // Handler for /api/tasks and /api/tasks/{id}
    static class TasksHandler implements HttpHandler {
        private final Pattern titlePattern = Pattern.compile("\"title\"\s*:\s*\"(.*?)\"");
        private final Pattern completedPattern = Pattern.compile("\"completed\"\s*:\s*(true|false)");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=utf-8");

            try {
                if (path.equals("/api/tasks")) {
                    if (method.equals("GET")) {
                        handleList(exchange);
                    } else if (method.equals("POST")) {
                        handleCreate(exchange);
                    } else {
                        sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    }
                } else if (path.startsWith("/api/tasks/")) {
                    String idStr = path.substring("/api/tasks/".length());
                    Integer id = parseIntSafe(idStr);
                    if (id == null) {
                        sendJson(exchange, 400, "{\"error\":\"Invalid ID\"}");
                        return;
                    }

                    if (method.equals("PUT")) {
                        handleUpdate(exchange, id);
                    } else if (method.equals("DELETE")) {
                        handleDelete(exchange, id);
                    } else {
                        sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    }
                } else {
                    sendJson(exchange, 404, "{\"error\":\"Not Found\"}");
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"Server Error\"}");
            }
        }

        private void handleList(HttpExchange exchange) throws IOException {
            List<String> json = new ArrayList<>();
            for (Task t : tasks.values()) {
                json.add(t.toJson());
            }
            String body = "[" + String.join(",", json) + "]";
            sendJson(exchange, 200, body);
        }

        private void handleCreate(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            Matcher mTitle = titlePattern.matcher(body);
            String title = mTitle.find() ? mTitle.group(1) : null;
            if (title == null || title.trim().isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"Missing title\"}");
                return;
            }
            boolean completed = false;
            Matcher mCompleted = completedPattern.matcher(body);
            if (mCompleted.find()) {
                completed = Boolean.parseBoolean(mCompleted.group(1));
            }
            int id = idSeq.getAndIncrement();
            Task t = new Task(id, title.trim(), completed, System.currentTimeMillis());
            tasks.put(id, t);
            String resp = t.toJson();
            sendJson(exchange, 201, resp);
        }

        private void handleUpdate(HttpExchange exchange, int id) throws IOException {
            Task t = tasks.get(id);
            if (t == null) {
                sendJson(exchange, 404, "{\"error\":\"Not Found\"}");
                return;
            }
            String body = readBody(exchange);
            Matcher mTitle = titlePattern.matcher(body);
            if (mTitle.find()) {
                String title = mTitle.group(1);
                if (title != null) t.title = title.trim();
            }
            Matcher mCompleted = completedPattern.matcher(body);
            if (mCompleted.find()) {
                t.completed = Boolean.parseBoolean(mCompleted.group(1));
            }
            tasks.put(id, t);
            sendJson(exchange, 200, t.toJson());
        }

        private void handleDelete(HttpExchange exchange, int id) throws IOException {
            Task removed = tasks.remove(id);
            if (removed == null) {
                sendJson(exchange, 404, "{\"error\":\"Not Found\"}");
            } else {
                // Return a small JSON body to keep clients happy
                sendJson(exchange, 200, "{\"ok\":true}");
            }
        }

        private Integer parseIntSafe(String s) {
            try { return Integer.parseInt(s); } catch (Exception e) { return null; }
        }

        private String readBody(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                byte[] bytes = is.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }

        private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}