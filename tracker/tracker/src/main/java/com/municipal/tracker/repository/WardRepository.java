package com.municipal.tracker.repository;
import com.municipal.tracker.model.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import  java.util.Optional;
@Repository
public interface WardRepository extends JpaRepository<Ward, Long>{
    Optional<Ward> findByWardNumber(String wardNumber);
    List<Ward> findByCity(String city);
    List<Ward> findByIsActiveTrue();
    boolean existsByWardNumber(String wardNumber);
}
