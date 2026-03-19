package com.swp.ckms.service;

import com.swp.ckms.dto.request.KitchenCreateRequest;
import com.swp.ckms.dto.request.KitchenUpdateRequest;
import com.swp.ckms.dto.response.KitchenResponse;

import java.util.List;

public interface KitchenService {
    KitchenResponse createKitchen(KitchenCreateRequest request);
    KitchenResponse updateKitchen(Long kitchenId, KitchenUpdateRequest request);
    KitchenResponse getKitchenById(Long kitchenId);
    List<KitchenResponse> getAllKitchens();
    void deleteKitchen(Long kitchenId);
}
