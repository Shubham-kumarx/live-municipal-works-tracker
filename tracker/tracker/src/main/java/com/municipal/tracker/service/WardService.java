package com.municipal.tracker.service;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
@Service
@RequiredArgsConstructor
public class WardService {
    private final WardRepository wardRepository;
    private final ProjectAccessService projectAccessService;
    @Transactional(readOnly = true)
    public List<Ward> getAllActiveWards(){ // get all active wards
        return wardRepository.findByActiveTrue();
    }
    @Transactional(readOnly = true)
    public List<Ward> getAllWards(){ // get all  wards.
        return wardRepository.findAll();
    }
    @Transactional(readOnly = true)
    public Optional<Ward> getWardById(Long id){ // get ward by id.
        return wardRepository.findById(id);
    }
    @Transactional(readOnly = true)
    public Optional<Ward> getWardByNumber(String wardNumber){
        return wardRepository.findByWardNumber(wardNumber);
    }
    @Transactional(readOnly = true)
    public List<Ward> getWardsByCity(String city){
        return wardRepository.findByCity(city);
    }
    @Transactional
    public Ward createWard(Ward ward){ // create new ward
        if(wardRepository.existsByWardNumber(ward.getWardNumber())){
            throw new ResponseStatusException(CONFLICT,
                    "Ward with number " + ward.getWardNumber() + " already exists"
            );

        }
        return wardRepository.save(ward);
    }
     @Transactional
     public Ward updateWard(long id, Ward updatedWard, User actor){ // updating existing ward
        projectAccessService.requireWardAccess(actor, id);
         Ward existingWard = wardRepository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ward not found with id: " + id));
        existingWard.setWardName(updatedWard.getWardName());
        existingWard.setCity(updatedWard.getCity());
        existingWard.setDistrict(updatedWard.getDistrict());
        existingWard.setState(updatedWard.getState());
        existingWard.setCenterLatitude(updatedWard.getCenterLatitude());
        existingWard.setCenterLongitude(updatedWard.getCenterLongitude());
        existingWard.setBoundaryGeoJson(updatedWard.getBoundaryGeoJson());
        return wardRepository.save(existingWard);
     }
     @Transactional
     public void deactivateWard(Long id){
        Ward ward = wardRepository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ward not found with id: " + id));
        ward.setActive(false);
        wardRepository.save(ward);
     }
}
