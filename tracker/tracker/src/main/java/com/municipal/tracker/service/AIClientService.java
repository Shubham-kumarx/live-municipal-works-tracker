package com.municipal.tracker.service;

import com.municipal.tracker.dto.AIAnalysisResponse;
import com.municipal.tracker.model.ComplaintCategory;
import com.municipal.tracker.model.ComplaintIssueType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class AIClientService {
    private final RestClient restClient;

    public AIClientService(
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.connect-timeout}") Duration connectTimeout,
            @Value("${app.ai.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public AIAnalysisResponse analyze(MunicipalImageValidator.ValidatedImage image) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(image.bytes()) {
            @Override
            public String getFilename() {
                return "municipal-issue." + image.extension();
            }
        };
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(image.contentType()));
        parts.add("image", new HttpEntity<>(resource, imageHeaders));
        try {
            AIAnalysisResponse response = restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(AIAnalysisResponse.class);
            validate(response);
            return response;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private void validate(AIAnalysisResponse response) {
        boolean lowConfidence = response != null
                && response.confidenceLevel() == AIAnalysisResponse.ConfidenceLevel.LOW;
        if (response == null || response.candidateIssueType() == null
                || !response.candidateIssueType().isSupportedClassification()
                || (response.issueType() != null
                    && !response.issueType().isSupportedClassification())
                || response.confidenceLevel() == null || response.category() == null
                || response.suggestedSeverity() == null || !Double.isFinite(response.confidence())
                || response.confidence() < 0 || response.confidence() > 1
                || (lowConfidence && response.issueType() != null)
                || (!lowConfidence && response.issueType() != response.candidateIssueType())
                || response.requiresManualReview() != lowConfidence
                || response.category() != expectedCategory(response.candidateIssueType())) {
            throw unavailable();
        }
    }

    private ComplaintCategory expectedCategory(ComplaintIssueType issueType) {
        return issueType == ComplaintIssueType.DOMESTIC_TRASH
                ? ComplaintCategory.SANITATION : ComplaintCategory.ROAD;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(SERVICE_UNAVAILABLE,
                "AI analysis is temporarily unavailable; continue with manual classification");
    }
}
