package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.CityRepo;
import com.adsyahir.invoice_hub_backend.dao.ClientRepo;
import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dao.PostcodeRepo;
import com.adsyahir.invoice_hub_backend.dao.StateRepo;
import com.adsyahir.invoice_hub_backend.dto.request.CreateClientRequest;
import com.adsyahir.invoice_hub_backend.dto.request.UpdateClientRequest;
import com.adsyahir.invoice_hub_backend.dto.response.ClientResponse;
import com.adsyahir.invoice_hub_backend.exception.ValidationException;
import com.adsyahir.invoice_hub_backend.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

@Service
public class ClientService {

    @Autowired
    private ClientRepo clientRepo;

    @Autowired
    private StateRepo stateRepo;

    @Autowired
    private CityRepo cityRepo;

    @Autowired
    private PostcodeRepo postcodeRepo;

    @Autowired
    private InvoiceRepo invoiceRepo;

    public Client createClient(CreateClientRequest request, User currentUser) {
        if (!stateRepo.existsById(request.getState())) {
            throw new ValidationException(Map.of("state", "State not found"));
        }

        if (!cityRepo.existsByIdAndStateId(request.getCity(), request.getState())) {
            throw new ValidationException(Map.of("city", "City not found"));
        }

        if (!postcodeRepo.existsByIdAndCityIdAndCityStateId(request.getPostcode(), request.getCity(),
                request.getState())) {
            throw new ValidationException(Map.of("postcodeId", "Invalid postcode for the selected city/state"));
        }

        // FK entities are set via lazy proxies (getReferenceById = no extra SELECT,
        // safe because we just verified each id exists above).
        Client client = Client.builder()
                .name(request.getName())
            .email(request.getEmail())
                .phone(request.getPhone())
                .taxId(request.getTaxId())
                .addressLine1(request.getAddressLine1())
                .tenant(currentUser.getTenant())
                .country(request.getCountry())
                .currency(request.getCurrency())
                .paymentTermsDays(request.getPaymentTermsDays())
                .state(stateRepo.getReferenceById(request.getState()))
                .city(cityRepo.getReferenceById(request.getCity()))
                .postcode(postcodeRepo.getReferenceById(request.getPostcode()))
                .createdAt(LocalDateTime.now())
                .build();

        return clientRepo.save(client);
    }

    private Long parseId(String value, String fieldName, String errorMessage) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw new ValidationException(Map.of(fieldName, errorMessage));
        }
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> findAllClients(User currentUser) {
        // Super-admin has no tenant scope — return nothing rather than NPE.
        if (currentUser.getTenant() == null) {
            return List.of();
        }

        return clientRepo.findAllByTenantId(currentUser.getTenant().getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteClient(Long id, User currentUser) {
        try {
            // 1. Check if client exists and belongs to current user
            boolean exists = clientRepo.existsByIdAndTenantId(id, currentUser.getTenant().getId());

            // 2. If not found, throw error
            if (!exists) {
                throw new RuntimeException("Client not found with id: " + id);
            }

            // 3. Cascade: soft delete all of this client's live invoices first.
            invoiceRepo.softDeleteByClientId(id);

            // 4. Soft delete the client (deleteById triggers @SQLDelete -> sets deleted_at).
            clientRepo.deleteById(id);

        } catch (RuntimeException e) {
            throw e;

        } catch (Exception e) {
            throw new RuntimeException("Something went wrong while deleting client: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ClientResponse findClientByUuid(UUID uuid, User currentUser) {
        if (currentUser.getTenant() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        }
        // Look up by the public uuid AND tenant together. A client owned by another
        // tenant won't match → 404 (not 403, so we don't reveal it exists).
        Client client = clientRepo.findByUuidAndTenantId(uuid, currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        return toResponse(client);
    }

    @Transactional
    public ClientResponse updateClient(UUID uuid, UpdateClientRequest request, User currentUser) {
        if (currentUser.getTenant() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        }

        // Tenant-scoped fetch by uuid → 404 if missing or owned by another tenant.
        Client client = clientRepo.findByUuidAndTenantId(uuid, currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

        // Validate the geo chain just like createClient (state -> city -> postcode).
        if (!stateRepo.existsById(request.getState())) {
            throw new ValidationException(Map.of("state", "State not found"));
        }
        if (!cityRepo.existsByIdAndStateId(request.getCity(), request.getState())) {
            throw new ValidationException(Map.of("city", "City not found"));
        }
        if (!postcodeRepo.existsByIdAndCityIdAndCityStateId(request.getPostcode(), request.getCity(),
                request.getState())) {
            throw new ValidationException(Map.of("postcodeId", "Invalid postcode for the selected city/state"));
        }

        // Apply the editable fields onto the managed entity (FKs via lazy proxies).
        client.setName(request.getName());
        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());
        client.setTaxId(request.getTaxId());
        client.setAddressLine1(request.getAddressLine1());
        client.setCountry(request.getCountry());
        client.setCurrency(request.getCurrency());
        client.setPaymentTermsDays(request.getPaymentTermsDays());
        client.setState(stateRepo.getReferenceById(request.getState()));
        client.setCity(cityRepo.getReferenceById(request.getCity()));
        client.setPostcode(postcodeRepo.getReferenceById(request.getPostcode()));

        return toResponse(clientRepo.save(client));
    }

    private ClientResponse toResponse(Client c) {
        return ClientResponse.builder()
                .id(c.getId())
                .uuid(c.getUuid())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .taxId(c.getTaxId())
                .addressLine1(c.getAddressLine1())
                .stateId(c.getState() != null ? c.getState().getId() : null)
                .state(c.getState() != null ? c.getState().getName() : null)
                .cityId(c.getCity() != null ? c.getCity().getId() : null)
                .city(c.getCity() != null ? c.getCity().getName() : null)
                .postcodeId(c.getPostcode() != null ? c.getPostcode().getId() : null)
                .postcode(c.getPostcode() != null ? c.getPostcode().getCode() : null)
                .country(c.getCountry())
                .currency(c.getCurrency())
                .paymentTermsDays(c.getPaymentTermsDays())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
