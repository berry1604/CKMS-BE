package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.CreateRoleRequest;
import com.swp.ckms.dto.request.UpdateRoleRequest;
import com.swp.ckms.dto.response.PrivilegeResponse;
import com.swp.ckms.dto.response.RoleResponse;
import com.swp.ckms.entity.Privilege;
import com.swp.ckms.entity.Role;
import com.swp.ckms.exception.business.DuplicateResourceException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.PrivilegeRepository;
import com.swp.ckms.repository.RoleRepository;
import com.swp.ckms.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PrivilegeRepository privilegeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
        return toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.findByRoleName(request.getRoleName()).isPresent()) {
            throw new DuplicateResourceException("Role with name '" + request.getRoleName() + "' already exists");
        }

        Set<Privilege> privileges = privilegeRepository.findByPrivilegeIdIn(request.getPrivilegeIds());
        if (privileges.size() != request.getPrivilegeIds().size()) {
            throw new ResourceNotFoundException("One or more privileges not found");
        }

        Role role = Role.builder()
                .roleName(request.getRoleName())
                .privileges(privileges)
                .build();

        role = roleRepository.save(role);
        return toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));

        if (request.getRoleName() != null && !request.getRoleName().equals(role.getRoleName())) {
            if (roleRepository.findByRoleName(request.getRoleName()).isPresent()) {
                throw new DuplicateResourceException("Role with name '" + request.getRoleName() + "' already exists");
            }
            role.setRoleName(request.getRoleName());
        }

        if (request.getPrivilegeIds() != null) {
            Set<Privilege> privileges = privilegeRepository.findByPrivilegeIdIn(request.getPrivilegeIds());
            if (privileges.size() != request.getPrivilegeIds().size()) {
                throw new ResourceNotFoundException("One or more privileges not found");
            }
            role.setPrivileges(privileges);
        }

        role = roleRepository.save(role);
        return toResponse(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Role not found with id: " + id);
        }
        roleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public RoleResponse assignPrivilegesToRole(Long roleId, Set<Long> privilegeIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        Set<Privilege> privileges = privilegeRepository.findByPrivilegeIdIn(privilegeIds);
        if (privileges.size() != privilegeIds.size()) {
            throw new ResourceNotFoundException("One or more privileges not found");
        }

        role.getPrivileges().addAll(privileges);
        role = roleRepository.save(role);
        return toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse removePrivilegesFromRole(Long roleId, Set<Long> privilegeIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        role.getPrivileges().removeIf(p -> privilegeIds.contains(p.getPrivilegeId()));
        role = roleRepository.save(role);
        return toResponse(role);
    }

    private RoleResponse toResponse(Role role) {
        Set<PrivilegeResponse> privilegeResponses = role.getPrivileges().stream()
                .map(p -> PrivilegeResponse.builder()
                        .privilegeId(p.getPrivilegeId())
                        .code(p.getCode())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toSet());

        return RoleResponse.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .privileges(privilegeResponses)
                .build();
    }
}