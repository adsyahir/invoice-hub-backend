package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.dto.projection.CityProjection;
import com.adsyahir.invoice_hub_backend.model.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CityRepo extends JpaRepository<City, Long> {

    List<CityProjection> findByStateId(Long stateId);

    // True when the city exists AND belongs to the given state (City.state.id).
    boolean existsByIdAndStateId(Long id, Long stateId);
}
