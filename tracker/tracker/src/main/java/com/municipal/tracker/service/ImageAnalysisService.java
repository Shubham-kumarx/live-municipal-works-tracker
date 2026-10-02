package com.municipal.tracker.service;

import com.municipal.tracker.dto.AIAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImageAnalysisService {
    private final MunicipalImageValidator imageValidator;
    private final AIClientService aiClientService;

    public AIAnalysisResponse analyze(MultipartFile image) {
        MunicipalImageValidator.ValidatedImage validated = imageValidator.validate(image);
        return aiClientService.analyze(validated);
    }
}
