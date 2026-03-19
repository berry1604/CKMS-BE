package com.swp.ckms.service;

import com.swp.ckms.dto.request.KitchenUpdateRequest;
import com.swp.ckms.dto.response.KitchenResponse;

import java.util.List;

public interface KitchenService {
    KitchenResponse updateKitchen(Long kitchenId, KitchenUpdateRequest request);
    KitchenResponse getKitchenById(Long kitchenId);
    List<KitchenResponse> getAllKitchens();
}
