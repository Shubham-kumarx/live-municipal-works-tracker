package com.municipal.tracker;

import com.municipal.tracker.dto.AIAnalysisResponse;
import com.municipal.tracker.model.ComplaintIssueType;
import com.municipal.tracker.service.AIClientService;
import com.municipal.tracker.service.MunicipalImageValidator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void acceptsControlledLowConfidenceContract() throws Exception {
        String json = """
                {"issueType":null,"candidateIssueType":"POTHOLE","confidence":0.4,
                 "confidenceLevel":"LOW","category":"ROAD","suggestedSeverity":"MEDIUM",
                 "requiresManualReview":true}
                """;
        server = jsonServer(json);
        AIClientService client = clientFor(server);

        AIAnalysisResponse response = client.analyze(image());

        assertThat(response.issueType()).isNull();
        assertThat(response.candidateIssueType()).isEqualTo(ComplaintIssueType.POTHOLE);
        assertThat(response.requiresManualReview()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"issueType\":\"POTHOLE\",\"candidateIssueType\":\"POTHOLE\",\"confidence\":0.9,"
                    + "\"confidenceLevel\":\"HIGH\",\"category\":\"ROAD\",\"suggestedSeverity\":\"HIGH\","
                    + "\"requiresManualReview\":true}",
            "{\"issueType\":\"POTHOLE\",\"candidateIssueType\":\"POTHOLE\",\"confidence\":0.9,"
                    + "\"confidenceLevel\":\"HIGH\",\"category\":\"SANITATION\",\"suggestedSeverity\":\"HIGH\","
                    + "\"requiresManualReview\":false}",
            "{\"issueType\":\"DAMAGED_SIGN\",\"candidateIssueType\":\"POTHOLE\",\"confidence\":0.9,"
                    + "\"confidenceLevel\":\"HIGH\",\"category\":\"ROAD\",\"suggestedSeverity\":\"HIGH\","
                    + "\"requiresManualReview\":false}"
    })
    void rejectsInconsistentPredictionContract(String json) throws Exception {
        server = jsonServer(json);
        AIClientService client = clientFor(server);

        assertThatThrownBy(() -> client.analyze(image()))
                .hasMessageContaining("AI analysis is temporarily unavailable");
    }

    @Test
    void rejectsLegacyIssueTypeFromAiService() throws Exception {
        String json = """
                {"issueType":"ROAD_CRACK","candidateIssueType":"ROAD_CRACK","confidence":0.9,
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

        assertThatThrownBy(() -> client.analyze(image()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("AI analysis is temporarily unavailable");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INFRASTRUCTURE_DAMAGE", "VANDALISM", "GRAFFITI", "VANDALISM_GRAFFITI"
    })
    void rejectsExcludedIssueTypeFromAiService(String excludedType) throws Exception {
        String json = """
                {"issueType":"%s","candidateIssueType":"%s","confidence":0.9,
                 "confidenceLevel":"HIGH","category":"ROAD","suggestedSeverity":"HIGH",
                 "requiresManualReview":false}
                """.formatted(excludedType, excludedType);
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

        assertThatThrownBy(() -> client.analyze(image()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
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

    @Test
    void translatesConnectionFailureToServiceUnavailable() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        AIClientService client = new AIClientService("http://127.0.0.1:" + unusedPort,
                Duration.ofMillis(100), Duration.ofMillis(100));

        assertThatThrownBy(() -> client.analyze(image()))
                .hasMessageContaining("continue with manual classification");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AI_LIVE_TEST_URL", matches = ".+")
    void callsLiveFastApiAndParsesTrainedModelResponse() throws Exception {
        Path imagePath = Path.of(System.getenv("AI_LIVE_TEST_IMAGE"));
        AIClientService client = new AIClientService(System.getenv("AI_LIVE_TEST_URL"),
                Duration.ofSeconds(2), Duration.ofSeconds(30));

        AIAnalysisResponse response = client.analyze(new MunicipalImageValidator.ValidatedImage(
                "jpg", "image/jpeg", Files.readAllBytes(imagePath)));

        assertThat(response.candidateIssueType().isSupportedClassification()).isTrue();
        assertThat(response.confidence()).isBetween(0.0, 1.0);
    }

    private HttpServer jsonServer(String json) throws Exception {
        HttpServer jsonServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jsonServer.createContext("/predict", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jsonServer.start();
        return jsonServer;
    }

    private AIClientService clientFor(HttpServer jsonServer) {
        return new AIClientService("http://127.0.0.1:" + jsonServer.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private MunicipalImageValidator.ValidatedImage image() {
        return new MunicipalImageValidator.ValidatedImage("png", "image/png", new byte[]{1, 2, 3});
    }
}
