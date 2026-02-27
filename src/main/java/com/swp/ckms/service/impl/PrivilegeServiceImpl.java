package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.CreatePrivilegeRequest;
import com.swp.ckms.dto.request.UpdatePrivilegeRequest;
import com.swp.ckms.dto.response.PrivilegeResponse;
import com.swp.ckms.entity.Privilege;
import com.swp.ckms.exception.business.DuplicateResourceException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.PrivilegeRepository;
import com.swp.ckms.service.PrivilegeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrivilegeServiceImpl implements PrivilegeService {

    private final PrivilegeRepository privilegeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PrivilegeResponse> getAllPrivileges() {
        return privilegeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PrivilegeResponse getPrivilegeById(Long id) {
        Privilege privilege = privilegeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Privilege not found with id: " + id));
        return toResponse(privilege);
    }

    @Override
    @Transactional
    public PrivilegeResponse createPrivilege(CreatePrivilegeRequest request) {
        if (privilegeRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Privilege with code '" + request.getCode() + "' already exists");
        }

        Privilege privilege = Privilege.builder()
                .code(request.getCode())
                .description(request.getDescription())
                .build();

        privilege = privilegeRepository.save(privilege);
        return toResponse(privilege);
    }

    @Override
    @Transactional
    public PrivilegeResponse updatePrivilege(Long id, UpdatePrivilegeRequest request) {
        Privilege privilege = privilegeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Privilege not found with id: " + id));

        if (request.getCode() != null && !request.getCode().equals(privilege.getCode())) {
            if (privilegeRepository.existsByCode(request.getCode())) {
                throw new DuplicateResourceException("Privilege with code '" + request.getCode() + "' already exists");
            }
            privilege.setCode(request.getCode());
        }

        if (request.getDescription() != null) {
            privilege.setDescription(request.getDescription());
        }

        privilege = privilegeRepository.save(privilege);
        return toResponse(privilege);
    }

    @Override
    @Transactional
    public void deletePrivilege(Long id) {
        if (!privilegeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Privilege not found with id: " + id);
        }
        privilegeRepository.deleteById(id);
    }

    private PrivilegeResponse toResponse(Privilege privilege) {
        return PrivilegeResponse.builder()
                .privilegeId(privilege.getPrivilegeId())
                .code(privilege.getCode())
                .description(privilege.getDescription())
                .build();
    }
}