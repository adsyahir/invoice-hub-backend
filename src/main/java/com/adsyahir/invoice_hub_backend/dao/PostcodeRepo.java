package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.dto.projection.PostcodeProjection;
import com.adsyahir.invoice_hub_backend.model.Postcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostcodeRepo extends JpaRepository<Postcode, Long> {
    List<PostcodeProjection> findPostcodeByCityId(Long id);
    boolean existsByIdAndCityIdAndCityStateId(Long id, Long cityId, Long
            stateId);
}
