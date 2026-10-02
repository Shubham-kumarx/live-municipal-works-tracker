package com.municipal.tracker.service;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {
    public void requireMutationAccess(User actor, MunicipalProject project) {
        if (actor == null) throw new AccessDeniedException("Authentication required");
        if (actor.getRole() == Role.MUNICIPAL_ADMIN) return;
        if (actor.getRole() == Role.WARD_OFFICER && sameWard(actor, project)) return;
        if (actor.getRole() == Role.FIELD_WORKER && project.getAssignedWorker() != null
                && project.getAssignedWorker().getId().equals(actor.getId())) return;
        throw new AccessDeniedException("You cannot modify this project");
    }

    public void requireOfficerWardAccess(User actor, MunicipalProject project) {
        if (actor == null) throw new AccessDeniedException("Authentication required");
        if (actor.getRole() == Role.MUNICIPAL_ADMIN) return;
        if (actor.getRole() == Role.WARD_OFFICER && sameWard(actor, project)) return;
        throw new AccessDeniedException("You cannot manage this project");
    }

    public void requireWardAccess(User actor, Long wardId) {
        if (actor == null) throw new AccessDeniedException("Authentication required");
        if (actor.getRole() == Role.MUNICIPAL_ADMIN || actor.getRole() == Role.AUDITOR) return;
        if (actor.getWard() != null && actor.getWard().getId().equals(wardId)) return;
        throw new AccessDeniedException("You cannot access this ward");
    }

    public void requireProjectWardAccess(User actor, MunicipalProject project) {
        if (project == null || project.getWard() == null) {
            throw new AccessDeniedException("Project ward is unavailable");
        }
        requireWardAccess(actor, project.getWard().getId());
    }

    private boolean sameWard(User actor, MunicipalProject project) {
        return actor.getWard() != null && project.getWard() != null
                && actor.getWard().getId().equals(project.getWard().getId());
    }
}
