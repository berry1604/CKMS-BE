package com.swp.ckms.controller;

import com.swp.ckms.dto.request.CreateRoleRequest;
import com.swp.ckms.dto.request.UpdateRoleRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.RoleResponse;
import com.swp.ckms.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "APIs for managing roles and their privileges")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "Get all roles", description = "Retrieve a list of all roles with their privileges")
    // @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", roles));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID", description = "Retrieve a specific role with its privileges")
    // @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        RoleResponse role = roleService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success("Role retrieved successfully", role));
    }

    @PostMapping
    @Operation(summary = "Create a new role", description = "Create a new role and assign privileges to it")
    // @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse role = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", role));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a role", description = "Update an existing role's name and/or privileges")
    // @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse role = roleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", role));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a role", description = "Delete an existing role by its ID")
    // @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully", null));
    }

    @PostMapping("/{roleId}/privileges")
    @Operation(summary = "Assign privileges to a role", description = "Add privileges to an existing role")
    // @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPrivileges(
            @PathVariable Long roleId,
            @RequestBody Set<Long> privilegeIds) {
        RoleResponse role = roleService.assignPrivilegesToRole(roleId, privilegeIds);
        return ResponseEntity.ok(ApiResponse.success("Privileges assigned successfully", role));
    }

    @DeleteMapping("/{roleId}/privileges")
    @Operation(summary = "Remove privileges from a role", description = "Remove privileges from an existing role")
    // @PreAuthorize("hasAuthority('MANAGE_ROLES')")
    public ResponseEntity<ApiResponse<RoleResponse>> removePrivileges(
            @PathVariable Long roleId,
            @RequestBody Set<Long> privilegeIds) {
        RoleResponse role = roleService.removePrivilegesFromRole(roleId, privilegeIds);
        return ResponseEntity.ok(ApiResponse.success("Privileges removed successfully", role));
    }
}
