package com.swp.ckms.service;

import com.swp.ckms.dto.request.CreateRoleRequest;
import com.swp.ckms.dto.request.UpdateRoleRequest;
import com.swp.ckms.dto.response.RoleResponse;

import java.util.List;
import java.util.Set;

public interface RoleService {
    List<RoleResponse> getAllRoles();
    RoleResponse getRoleById(Long id);
    RoleResponse createRole(CreateRoleRequest request);
    RoleResponse updateRole(Long id, UpdateRoleRequest request);
    void deleteRole(Long id);
    RoleResponse assignPrivilegesToRole(Long roleId, Set<Long> privilegeIds);
    RoleResponse removePrivilegesFromRole(Long roleId, Set<Long> privilegeIds);
}