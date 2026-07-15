package com.municipal.tracker.controller;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.service.WardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/wards")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WardController {
    private final WardService wardService;

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
    public ResponseEntity<Ward> createWard(@RequestBody Ward ward){
        try{
            Ward created = wardService.createWard(ward);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch(RuntimeException e){
            return ResponseEntity.badRequest().build();
        }
    }
    @PutMapping("/{id}") // update ward by id
    public ResponseEntity<Ward> updateWard(@PathVariable Long id, @RequestBody Ward ward){
        try {
            Ward updated = wardService.updateWard(id, ward);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    @DeleteMapping("/{id}") // soft deleting a ward
    public ResponseEntity<Void> deactivateWard(@PathVariable long id){
        try{
            wardService.deactivateWard(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
