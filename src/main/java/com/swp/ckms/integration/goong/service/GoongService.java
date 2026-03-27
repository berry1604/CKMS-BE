package com.swp.ckms.integration.goong.service;

import com.swp.ckms.integration.goong.dto.response.GoongGeocodeResult;

import java.util.List;

public interface GoongService {
	List<GoongGeocodeResult> geocodeAddress(String address);

	List<GoongGeocodeResult> geocodeAddress(String address, Boolean hasDeprecatedAdministrativeUnit);

	GoongGeocodeResult geocodeFirstAddress(String address);
}
