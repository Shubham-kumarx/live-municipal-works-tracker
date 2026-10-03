package com.municipal.tracker;

import com.municipal.tracker.dto.AIAnalysisResponse;
import com.municipal.tracker.model.ComplaintIssueType;
import com.municipal.tracker.service.AIClientService;
import com.municipal.tracker.service.MunicipalImageValidator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIClientServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void acceptsValidPredictionContract() throws Exception {
        String json = """
                {"issueType":"POTHOLE","candidateIssueType":"POTHOLE","confidence":0.91,
                 "confidenceLevel":"HIGH","category":"ROAD","suggestedSeverity":"HIGH",
                 "requiresManualReview":false}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        AIClientService client = new AIClientService("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2));

        AIAnalysisResponse response = client.analyze(
                new MunicipalImageValidator.ValidatedImage("png", "image/png", new byte[]{1, 2, 3}));

        assertThat(response.issueType()).isEqualTo(ComplaintIssueType.POTHOLE);
        assertThat(response.confidence()).isEqualTo(0.91);
    }

    @Test
    void rejectsInvalidLowConfidenceContract() throws Exception {
        String json = """
                {"issueType":"POTHOLE","candidateIssueType":"POTHOLE","confidence":0.2,
                 "confidenceLevel":"LOW","category":"ROAD","suggestedSeverity":"MEDIUM",
                 "requiresManualReview":true}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        AIClientService client = new AIClientService("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2));

        assertThatThrownBy(() -> client.analyze(
                new MunicipalImageValidator.ValidatedImage("png", "image/png", new byte[]{1})))
                .hasMessageContaining("AI analysis is temporarily unavailable");
    }

    @Test
    void translatesMalformedResponseToServiceUnavailable() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        AIClientService client = new AIClientService("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.analyze(image()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("AI analysis is temporarily unavailable");
    }

    @Test
    void translatesReadTimeoutToServiceUnavailable() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(300);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        AIClientService client = new AIClientService("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofMillis(50));

        assertThatThrownBy(() -> client.analyze(image()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("continue with manual classification");
    }

    private MunicipalImageValidator.ValidatedImage image() {
        return new MunicipalImageValidator.ValidatedImage("png", "image/png", new byte[]{1, 2, 3});
    }
}
