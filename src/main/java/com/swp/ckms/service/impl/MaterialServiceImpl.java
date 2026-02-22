package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.MaterialRequest;
import com.swp.ckms.dto.response.MaterialResponse;
import com.swp.ckms.entity.Material;
import com.swp.ckms.exception.business.DuplicateNameException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.MaterialRepository;
import com.swp.ckms.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialRepository materialRepository;

    @Override
    public List<MaterialResponse> getAllMaterials() {
        return materialRepository.findByIsActiveTrue().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public MaterialResponse createMaterial(MaterialRequest request) {
        if (materialRepository.existsByName(request.getName())) {
            throw new DuplicateNameException("Material with name '" + request.getName() + "' already exists");
        }

        Material material = Material.builder()
                .name(request.getName())
                .unit(request.getUnit())
                .minStockLevel(request.getMinStockLevel())
                .isActive(true)
                .build();

        return mapToResponse(materialRepository.save(material));
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public MaterialResponse updateMaterial(Long id, MaterialRequest request) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found"));

        if (materialRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new DuplicateNameException("Material with name '" + request.getName() + "' already exists");
        }

        material.setName(request.getName());
        material.setUnit(request.getUnit());
        material.setMinStockLevel(request.getMinStockLevel());

        return mapToResponse(materialRepository.save(material));
    }

    @SuppressWarnings("null")
    private MaterialResponse mapToResponse(Material material) {
        return MaterialResponse.builder()
                .id(material.getId())
                .name(material.getName())
                .unit(material.getUnit())
                .minStockLevel(material.getMinStockLevel())
                .isActive(material.getIsActive())
                .build();
    }
}
