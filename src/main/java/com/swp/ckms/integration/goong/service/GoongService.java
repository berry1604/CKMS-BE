package com.swp.ckms.integration.goong.service;

import com.swp.ckms.integration.goong.dto.response.GoongGeocodeResult;
import com.swp.ckms.integration.goong.dto.response.GoongReverseGeocodeResult;

import java.util.List;

public interface GoongService {
	List<GoongGeocodeResult> geocodeAddress(String address);

	List<GoongGeocodeResult> geocodeAddress(String address, Boolean hasDeprecatedAdministrativeUnit);

	GoongGeocodeResult geocodeFirstAddress(String address);

	List<GoongReverseGeocodeResult> reverseGeocodeCoordinates(Double latitude, Double longitude);

	List<GoongReverseGeocodeResult> reverseGeocodeCoordinates(
		Double latitude,
		Double longitude,
		Integer limit,
		Boolean hasDeprecatedAdministrativeUnit,
		Boolean hasVnid);

	GoongReverseGeocodeResult reverseGeocodeFirstCoordinate(Double latitude, Double longitude);
}
