package com.municipal.tracker.controller;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.model.User;
import com.municipal.tracker.dto.WardWriteRequest;
import com.municipal.tracker.dto.WardResponse;
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

    @GetMapping
    public ResponseEntity<List<WardResponse>> getAllActiveWards(){ // get all active wards
        List<Ward> wards = wardService.getAllActiveWards();
        return ResponseEntity.ok(wards.stream().map(WardResponse::from).toList());
    }
    @GetMapping("/{id}") // get ward by id
    public ResponseEntity<WardResponse> getWardById(@PathVariable Long id){
        return wardService.getWardById(id).map(WardResponse::from)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/city/{city}") // get wards by city
    public ResponseEntity<List<WardResponse>> getWardsByCity(@PathVariable String city){
        List<Ward> wards = wardService.getWardsByCity(city);
        return ResponseEntity.ok(wards.stream().map(WardResponse::from).toList());
    }
    @GetMapping("/number/{wardNumber}") // get ward by ward number
    public ResponseEntity<WardResponse> getWardByNumber(@PathVariable String wardNumber){
        return wardService.getWardByNumber(wardNumber)
                .map(WardResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping // post create a new ward
    @PreAuthorize("hasRole('MUNICIPAL_ADMIN')")
    public ResponseEntity<WardResponse> createWard(@Valid @RequestBody WardWriteRequest request){
        Ward created = wardService.createWard(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(WardResponse.from(created));
    }
    @PutMapping("/{id}") // update ward by id
    @PreAuthorize("hasAnyRole('MUNICIPAL_ADMIN','WARD_OFFICER')")
    public ResponseEntity<WardResponse> updateWard(@PathVariable Long id, @Valid @RequestBody WardWriteRequest request,
                                            @AuthenticationPrincipal User currentUser){
        Ward updated = wardService.updateWard(id, request.toEntity(), currentUser);
        return ResponseEntity.ok(WardResponse.from(updated));
    }
    @DeleteMapping("/{id}") // soft deleting a ward
    @PreAuthorize("hasRole('MUNICIPAL_ADMIN')")
    public ResponseEntity<Void> deactivateWard(@PathVariable long id){
        wardService.deactivateWard(id);
        return ResponseEntity.ok().build();
    }
}
