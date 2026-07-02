package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.response.ApiResponse;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.SettingService;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/teams")
public class TeamController{


    @Autowired
    private SettingService settingService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserPrincipal principal){

        return ResponseEntity.ok(ApiResponse.success(settingService.teamList(principal.getUser().getTenant().getId())));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal UserPrincipal principal){
        return ResponseEntity.ok(ApiResponse.success((settingService.teamList(principal.getUser().getTenant().getId()))));
    }
}
