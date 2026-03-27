package com.swp.ckms.integration.goong.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoongGeocodeResult {
    private String formattedAddress;
    private String placeId;
    private Double latitude;
    private Double longitude;
    private String deprecatedDescription;
}
