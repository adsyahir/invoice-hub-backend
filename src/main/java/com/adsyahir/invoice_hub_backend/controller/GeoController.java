package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.service.GeoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/geo")
public class GeoController {

    @Autowired
    private GeoService geoService;

    @GetMapping("/states")
    public ResponseEntity<?> getAllState() {
        return ResponseEntity.ok(geoService.getAllStates());
    }

    @GetMapping("/{stateId}/cities")
    public ResponseEntity<?> findCitiesByStateId(@PathVariable Long stateId) {
        return ResponseEntity.ok(geoService.getCityByStateId(stateId));
    }

    @GetMapping("/{cityId}/postcodes")
    public ResponseEntity<?> findPostcodeByCityId(@PathVariable Long cityId) {
        return ResponseEntity.ok(geoService.getPostcodesByCityId(cityId));
    }
}
