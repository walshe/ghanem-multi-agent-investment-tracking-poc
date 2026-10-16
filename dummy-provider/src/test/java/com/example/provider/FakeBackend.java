package com.example.provider;

import com.example.provider.callback.Signer;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stands in for the backend's POST /callbacks. Records every request exactly as received and
 * answers with a status chosen by the test (by default: 200 if the signature is valid, else 401).
 */
public class FakeBackend implements AutoCloseable {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public record Received(String signature, byte[] body, long receivedAtNanos, int answered) {
        public JsonNode json() {
            return JSON.readTree(body);
        }

        public String eventId() {
            return json().get("eventId").asString();
        }

        public String status() {
            return json().get("status").asString();
        }

        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private final HttpServer server;
    private final String secret;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private volatile Function<Received, Integer> responder;
    private volatile long handlerDelayMillis;

    public FakeBackend(String secret) throws IOException {
        this.secret = secret;
        this.responder = r -> signatureValid(r) ? 200 : 401;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 100);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.server.createContext("/callbacks", exchange -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String signature = exchange.getRequestHeaders().getFirst("X-Signature");
                Received pending = new Received(signature, body, System.nanoTime(), 0);
                if (handlerDelayMillis > 0) {
                    Thread.sleep(handlerDelayMillis);
                }
                int status = responder.apply(pending);
                received.add(new Received(signature, body, pending.receivedAtNanos(), status));
                exchange.sendResponseHeaders(status, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(500, -1);
            } finally {
                inFlight.decrementAndGet();
                exchange.close();
            }
        });
        this.server.start();
    }

    public String callbackUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/callbacks";
    }

    public boolean signatureValid(Received r) {
        return r.signature() != null && r.signature().equals(Signer.hmacHex(secret.getBytes(StandardCharsets.UTF_8), r.body()));
    }

    public void respondWith(Function<Received, Integer> responder) {
        this.responder = responder;
    }

    public void delayEachRequest(long millis) {
        this.handlerDelayMillis = millis;
    }

    public List<Received> received() {
        return new ArrayList<>(received);
    }

    public List<Received> receivedFor(String paymentId) {
        return received.stream().filter(r -> paymentId.equals(r.json().get("paymentId").asString())).toList();
    }

    public int maxInFlight() {
        return maxInFlight.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
