package com.swp.ckms.service;

import com.swp.ckms.dto.request.CreatePrivilegeRequest;
import com.swp.ckms.dto.request.UpdatePrivilegeRequest;
import com.swp.ckms.dto.response.PrivilegeResponse;

import java.util.List;

public interface PrivilegeService {
    List<PrivilegeResponse> getAllPrivileges();
    PrivilegeResponse getPrivilegeById(Long id);
    PrivilegeResponse createPrivilege(CreatePrivilegeRequest request);
    PrivilegeResponse updatePrivilege(Long id, UpdatePrivilegeRequest request);
    void deletePrivilege(Long id);
}