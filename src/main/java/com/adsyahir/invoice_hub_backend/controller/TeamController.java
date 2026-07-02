package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.CreateTeamMemberRequest;
import com.adsyahir.invoice_hub_backend.dto.request.InviteTeamMemberRequest;
import com.adsyahir.invoice_hub_backend.dto.response.AuthResult;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.CookieService;
import com.adsyahir.invoice_hub_backend.service.SettingService;
import com.adsyahir.invoice_hub_backend.service.TeamService;
import com.adsyahir.invoice_hub_backend.service.UserService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/teams")
public class TeamController{

    @Autowired
    private SettingService settingService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private CookieService cookieService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserPrincipal principal){

        return ResponseEntity.ok(teamService.teamList(principal.getUser().getTenant().getId()));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<?> delete(@PathVariable UUID uuid, @AuthenticationPrincipal UserPrincipal principal){
        User current = principal.getUser();
        teamService.delete(uuid, current.getTenant().getId(), current.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite")
    public ResponseEntity<?> invite(@Valid @RequestBody InviteTeamMemberRequest request, @AuthenticationPrincipal UserPrincipal principal) throws MessagingException {

        String organizationName = principal.getUser().getTenant() != null
                ? principal.getUser().getTenant().getName()
                : null;

        Tenant tenant = principal.getUser().getTenant() != null
                ? principal.getUser().getTenant()
                : null;

        teamService.sendTeamInvitationEmail(request.getEmail(), request.getRole(), organizationName, principal.getUser(), tenant);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite/accept")
    public ResponseEntity<?> acceptInvite(@RequestParam String token,
                                          @Valid @RequestBody CreateTeamMemberRequest request,
                                          HttpServletResponse response) {

        // Create the account + consume the invite, then sign the new member in.
        User member = teamService.createTeamMember(request.getFullName(), request.getPassword(), token);
        AuthResult result = userService.issueTokens(member);
        cookieService.setRefreshCookie(response, result.refreshToken(), CookieService.REFRESH_COOKIE_MAX_AGE);

        return ResponseEntity.status(HttpStatus.CREATED).body(result.auth());
    }
}
