package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dao.ClientRepo;
import com.adsyahir.invoice_hub_backend.dto.request.CreateClientRequest;
import com.adsyahir.invoice_hub_backend.dto.request.UpdateClientRequest;
import com.adsyahir.invoice_hub_backend.dto.response.ClientResponse;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/clients")
public class ClientController {

    @Autowired
    private ClientRepo clientRepo;

    @Autowired
    private ClientService clientService;

    @PostMapping
    @PreAuthorize("hasAuthority('client:write')")
    public ResponseEntity<?> createClient(@Valid @RequestBody CreateClientRequest request,  @AuthenticationPrincipal UserPrincipal principal)
    {
        Client client = clientService.createClient(request, principal.getUser());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", client.getId(),
                "name", client.getName()
        ));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<?> update(@PathVariable UUID uuid, @Valid @RequestBody UpdateClientRequest request, @AuthenticationPrincipal UserPrincipal principal) {

        ClientResponse updated = clientService.updateClient(uuid, request, principal.getUser());

        return ResponseEntity.ok(updated);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('client:read')")
    public ResponseEntity<?> getAllClient(@AuthenticationPrincipal UserPrincipal principal)
    {
//        List <Client> client = clientService.findAllClients(principal.getUser());
//        System.out.println(client);
        return ResponseEntity.ok(clientService.findAllClients(principal.getUser()));

//        return ResponseEntity.ok().body(ApiResponse.success(Map.of(
//                "id", "test",
//                "name", "test"
//        )));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('client:read')")
    public ResponseEntity<?> getClient(@PathVariable UUID uuid, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clientService.findClientByUuid(uuid, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('client:delete')")
    public ResponseEntity<?> deleteClient(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        clientService.deleteClient(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
