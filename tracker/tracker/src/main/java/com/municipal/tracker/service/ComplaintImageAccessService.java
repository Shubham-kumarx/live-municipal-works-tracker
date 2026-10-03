package com.municipal.tracker.service;

import com.municipal.tracker.model.Complaint;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.repository.ComplaintRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ComplaintImageAccessService {
    private static final String URL_PREFIX = "/uploads/";
    private final ComplaintRepository complaintRepository;
    private final Path uploadRoot;

    public ComplaintImageAccessService(ComplaintRepository complaintRepository,
            @Value("${app.upload.root:uploads}") String uploadRoot) {
        this.complaintRepository = complaintRepository;
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public AuthorizedImage load(Long complaintId, User actor) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Complaint not found: " + complaintId));
        requireAccess(actor, complaint);

        String imageUrl = complaint.getImageUrl();
        if (imageUrl == null || !imageUrl.startsWith(URL_PREFIX + "complaints/")) {
            throw new ResponseStatusException(NOT_FOUND, "Complaint image not found");
        }
        Path path = uploadRoot.resolve(imageUrl.substring(URL_PREFIX.length())).normalize();
        if (!path.startsWith(uploadRoot.resolve("complaints").normalize()) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(NOT_FOUND, "Complaint image not found");
        }
        try {
            String detected = Files.probeContentType(path);
            MediaType mediaType = detected == null
                    ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(detected);
            return new AuthorizedImage(Files.readAllBytes(path), mediaType);
        } catch (IOException exception) {
            throw new IllegalStateException("Complaint image could not be read", exception);
        }
    }

    private void requireAccess(User actor, Complaint complaint) {
        if (actor == null) throw new AccessDeniedException("Authentication required");
        User reporter = complaint.getReportingUser();
        if (reporter != null && reporter.getId() != null && reporter.getId().equals(actor.getId())) return;
        if (actor.getRole() == Role.MUNICIPAL_ADMIN) return;
        if (actor.getRole() == Role.WARD_OFFICER && sameWard(actor.getWard(),
                reporter == null ? null : reporter.getWard())) return;
        throw new AccessDeniedException("You cannot access this complaint image");
    }

    private boolean sameWard(Ward first, Ward second) {
        return first != null && second != null && first.getId() != null
                && first.getId().equals(second.getId());
    }

    public record AuthorizedImage(byte[] bytes, MediaType mediaType) { }
}
