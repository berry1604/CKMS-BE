package com.swp.ckms.controller;

import com.swp.ckms.dto.request.CreatePrivilegeRequest;
import com.swp.ckms.dto.request.UpdatePrivilegeRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.PrivilegeResponse;
import com.swp.ckms.service.PrivilegeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/privileges")
@RequiredArgsConstructor
@Tag(name = "Privilege Management", description = "APIs for managing privileges")
public class PrivilegeController {

    private final PrivilegeService privilegeService;

    @GetMapping
    @Operation(summary = "Get all privileges", description = "Retrieve a list of all privileges")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<PrivilegeResponse>>> getAllPrivileges() {
        List<PrivilegeResponse> privileges = privilegeService.getAllPrivileges();
        return ResponseEntity.ok(ApiResponse.success("Privileges retrieved successfully", privileges));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get privilege by ID", description = "Retrieve a specific privilege by its ID")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PrivilegeResponse>> getPrivilegeById(@PathVariable Long id) {
        PrivilegeResponse privilege = privilegeService.getPrivilegeById(id);
        return ResponseEntity.ok(ApiResponse.success("Privilege retrieved successfully", privilege));
    }

    @PostMapping
    @Operation(summary = "Create a new privilege", description = "Create a new privilege with a unique code")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PrivilegeResponse>> createPrivilege(
            @Valid @RequestBody CreatePrivilegeRequest request) {
        PrivilegeResponse privilege = privilegeService.createPrivilege(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Privilege created successfully", privilege));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a privilege", description = "Update an existing privilege")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PrivilegeResponse>> updatePrivilege(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePrivilegeRequest request) {
        PrivilegeResponse privilege = privilegeService.updatePrivilege(id, request);
        return ResponseEntity.ok(ApiResponse.success("Privilege updated successfully", privilege));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a privilege", description = "Delete an existing privilege by its ID")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePrivilege(@PathVariable Long id) {
        privilegeService.deletePrivilege(id);
        return ResponseEntity.ok(ApiResponse.success("Privilege deleted successfully", null));
    }
}
