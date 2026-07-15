package com.municipal.tracker.service;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class WardService {
    private final WardRepository wardRepository;
    public List<Ward> getAllActiveWards(){ // get all active wards
        return wardRepository.findByIsActiveTrue();
    }
    public List<Ward> getAllWards(){ // get all  wards.
        return wardRepository.findAll();
    }
    public Optional<Ward> getWardById(Long id){ // get ward by id.
        return wardRepository.findById(id);
    }
    public Optional<Ward> getWardByNumber(String wardNumber){
        return wardRepository.findByWardNumber(wardNumber);
    }
    public List<Ward> getWardsByCity(String city){
        return wardRepository.findByCity(city);
    }
    public Ward createWard(Ward ward){ // create new ward
        if(wardRepository.existsByWardNumber(ward.getWardNumber())){
            throw new RuntimeException(
                    "Ward with number " + ward.getWardNumber() + " already exists"
            );

        }
        return wardRepository.save(ward);
    }
     public Ward updateWard(long id, Ward updatedWard){ // updating existing ward
        Ward existingWard = wardRepository.findById(id).orElseThrow(() -> new RuntimeException("Ward not found with id: " + id));
        existingWard.setWardName(updatedWard.getWardName());
        existingWard.setCity(updatedWard.getCity());
        existingWard.setDistrict(updatedWard.getDistrict());
        existingWard.setState(updatedWard.getState());
        existingWard.setCenterLatitude(updatedWard.getCenterLatitude());
        existingWard.setCenterLongitude(updatedWard.getCenterLongitude());
        existingWard.setBoundaryGeoJson(updatedWard.getBoundaryGeoJson());
        return wardRepository.save(existingWard);
     }
     public void deactivateWard(Long id){
        Ward ward = wardRepository.findById(id).orElseThrow(() -> new RuntimeException("Ward not found with id: " + id));
        ward.setActive(false);
        wardRepository.save(ward);
     }
}
