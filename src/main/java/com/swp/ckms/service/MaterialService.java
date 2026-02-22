package com.swp.ckms.service;

import com.swp.ckms.dto.request.MaterialRequest;
import com.swp.ckms.dto.response.MaterialResponse;

import java.util.List;

public interface MaterialService {
    List<MaterialResponse> getAllMaterials();
    MaterialResponse createMaterial(MaterialRequest request);
    MaterialResponse updateMaterial(Long id, MaterialRequest request);
}
