package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.CityRepo;
import com.adsyahir.invoice_hub_backend.dao.PostcodeRepo;
import com.adsyahir.invoice_hub_backend.dao.StateRepo;
import com.adsyahir.invoice_hub_backend.dto.projection.CityProjection;
import com.adsyahir.invoice_hub_backend.dto.projection.PostcodeProjection;
import com.adsyahir.invoice_hub_backend.model.City;
import com.adsyahir.invoice_hub_backend.model.Postcode;
import com.adsyahir.invoice_hub_backend.model.State;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeoService {

    @Autowired
    private StateRepo stateRepo;

    @Autowired
    private CityRepo cityRepo;

    @Autowired
    private PostcodeRepo postcodeRepo;
    public List<State> getAllStates() {
        return stateRepo.findAll();
    }

    public List<CityProjection> getCityByStateId(Long stateId) {
        return cityRepo.findByStateId(stateId);
    }

    public List<PostcodeProjection> getPostcodesByCityId(Long cityId) {
        return postcodeRepo.findPostcodeByCityId(cityId);
    }
}
