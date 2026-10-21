import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.FlagsmithClient;
import com.flagsmith.Versions;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.Flags;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter that lets the Flagsmith SDK behavioural spec drive this SDK.
 *
 * <p>Protocol: https://github.com/Flagsmith/sdk-spec/blob/main/ADAPTER.md
 */
public class Adapter {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> JSON_OBJECT =
      new TypeReference<Map<String, Object>>() {};
  private static final Pattern CLIENT_ROUTE =
      Pattern.compile("^/clients/(?<clientId>[^/]+)(?<action>/[a-z-]+)?$");

  private static final Map<String, FlagsmithClient> clients = new ConcurrentHashMap<>();
  private static final AtomicLong clientIds = new AtomicLong();

  public static void main(String[] args) throws IOException {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
    server.createContext("/", Adapter::dispatch);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    System.out.println("adapter: listening on :" + port);
  }

  private static void dispatch(HttpExchange exchange) throws IOException {
    try {
      route(exchange);
    } catch (Exception e) {
      // Report SDK errors to the runner.
      e.printStackTrace();
      send(exchange, 500, error(e.getClass().getSimpleName() + ": " + e.getMessage()));
    } finally {
      System.out.println("adapter: " + exchange.getRequestMethod() + " "
          + exchange.getRequestURI() + " " + exchange.getResponseCode());
      exchange.close();
    }
  }

  private static void route(HttpExchange exchange) throws Exception {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();

    if (method.equals("GET") && path.equals("/health")) {
      Map<String, Object> health = new LinkedHashMap<>();
      health.put("sdk", "flagsmith-java-client");
      health.put("version", Versions.getVersion());
      send(exchange, 200, health);
      return;
    }

    if (method.equals("POST") && path.equals("/clients")) {
      Map<String, Object> body = body(exchange);
      String clientId = String.valueOf(clientIds.incrementAndGet());
      clients.put(clientId, FlagsmithClient.newBuilder()
          .setApiKey((String) body.get("environmentKey"))
          .withApiUrl((String) body.get("apiUrl"))
          .build());
      send(exchange, 201, Map.of("clientId", clientId));
      return;
    }

    Matcher match = CLIENT_ROUTE.matcher(path);
    FlagsmithClient client = match.matches() ? clients.get(match.group("clientId")) : null;
    if (client == null) {
      send(exchange, 404, error("No route for " + method + " " + path));
      return;
    }
    String action = match.group("action");

    if (method.equals("DELETE") && action == null) {
      clients.remove(match.group("clientId")).close();
      send(exchange, 204, null);
      return;
    }

    if (method.equals("POST") && "/environment-flags".equals(action)) {
      send(exchange, 200, serialiseFlags(client.getEnvironmentFlags()));
      return;
    }

    if (method.equals("POST") && "/identity-flags".equals(action)) {
      Map<String, Object> body = body(exchange);
      @SuppressWarnings("unchecked")
      Map<String, Object> traits = (Map<String, Object>) body.get("traits");
      Flags flags = client.getIdentityFlags(
          (String) body.get("identifier"), traits != null ? traits : new HashMap<>());
      send(exchange, 200, serialiseFlags(flags));
      return;
    }

    send(exchange, 404, error("No route for " + method + " " + path));
  }

  private static Map<String, Object> serialiseFlags(Flags flags) {
    List<Map<String, Object>> serialised = new ArrayList<>();
    for (BaseFlag flag : flags.getAllFlags()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("featureName", flag.getFeatureName());
      entry.put("enabled", flag.getEnabled());
      // The SDK keeps API values as Jackson nodes, which serialise with their JSON type intact.
      entry.put("value", flag.getValue());
      serialised.add(entry);
    }
    return Map.of("flags", serialised);
  }

  private static Map<String, Object> error(String message) {
    return Map.of("error", String.valueOf(message));
  }

  private static Map<String, Object> body(HttpExchange exchange) throws IOException {
    byte[] bytes = exchange.getRequestBody().readAllBytes();
    return bytes.length > 0 ? MAPPER.readValue(bytes, JSON_OBJECT) : new HashMap<>();
  }

  private static void send(HttpExchange exchange, int status, Object body) throws IOException {
    if (body == null) {
      exchange.sendResponseHeaders(status, -1);
      return;
    }
    byte[] payload = MAPPER.writeValueAsBytes(body);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }
}
