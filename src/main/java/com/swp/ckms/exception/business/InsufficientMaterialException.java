package com.swp.ckms.exception.business;

import com.swp.ckms.dto.response.MissingMaterialResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientMaterialException extends RuntimeException {
    
    private final List<MissingMaterialResponse> missingMaterials;

    public InsufficientMaterialException(String message, List<MissingMaterialResponse> missingMaterials) {
        super(message);
        this.missingMaterials = missingMaterials;
    }
}
