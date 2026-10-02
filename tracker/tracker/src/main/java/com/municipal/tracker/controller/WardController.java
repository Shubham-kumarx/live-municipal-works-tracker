package com.municipal.tracker.controller;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.model.User;
import com.municipal.tracker.dto.WardWriteRequest;
import com.municipal.tracker.service.ProjectAccessService;
import com.municipal.tracker.service.WardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/wards")
@RequiredArgsConstructor
public class WardController {
    private final WardService wardService;
    private final ProjectAccessService projectAccessService;

    @GetMapping
    public ResponseEntity<List<Ward>> getAllActiveWards(){ // get all active wards
        List<Ward> wards = wardService.getAllActiveWards();
        return ResponseEntity.ok(wards);
    }
    @GetMapping("/{id}") // get ward by id
    public ResponseEntity<Ward> getWardById(@PathVariable Long id){
        return wardService.getWardById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/city/{city}") // get wards by city
    public ResponseEntity<List<Ward>> getWardsByCity(@PathVariable String city){
        List<Ward> wards = wardService.getWardsByCity(city);
        return ResponseEntity.ok(wards);
    }
    @GetMapping("/number/{wardNumber}") // get ward by ward number
    public ResponseEntity<Ward> getWardByNumber(@PathVariable String wardNumber){
        return wardService.getWardByNumber(wardNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping // post create a new ward
    @PreAuthorize("hasRole('MUNICIPAL_ADMIN')")
    public ResponseEntity<Ward> createWard(@Valid @RequestBody WardWriteRequest request){
        Ward created = wardService.createWard(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    @PutMapping("/{id}") // update ward by id
    @PreAuthorize("hasAnyRole('MUNICIPAL_ADMIN','WARD_OFFICER')")
    public ResponseEntity<Ward> updateWard(@PathVariable Long id, @Valid @RequestBody WardWriteRequest request,
                                            @AuthenticationPrincipal User currentUser){
        projectAccessService.requireWardAccess(currentUser, id);
        Ward updated = wardService.updateWard(id, request.toEntity());
        return ResponseEntity.ok(updated);
    }
    @DeleteMapping("/{id}") // soft deleting a ward
    @PreAuthorize("hasRole('MUNICIPAL_ADMIN')")
    public ResponseEntity<Void> deactivateWard(@PathVariable long id){
        wardService.deactivateWard(id);
        return ResponseEntity.ok().build();
    }
}
