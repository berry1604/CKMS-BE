package com.swp.ckms.controller;

import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.integration.goong.dto.response.GoongGeocodeResult;
import com.swp.ckms.integration.goong.dto.response.GoongReverseGeocodeResult;
import com.swp.ckms.integration.goong.service.GoongService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/geocode")
@RequiredArgsConstructor
public class GeocodeController {

	private final GoongService goongService;

	@GetMapping
	@PreAuthorize("hasAnyAuthority('MANAGE_STORES','MANAGE_KITCHEN_CONFIG')")
	public ApiResponse<List<GoongGeocodeResult>> geocodeAddress(
			@RequestParam String address,
			@RequestParam(value = "hasDeprecatedAdministrativeUnit", required = false) Boolean hasDeprecatedAdministrativeUnit
	) {
		List<GoongGeocodeResult> results = hasDeprecatedAdministrativeUnit == null
				? goongService.geocodeAddress(address)
				: goongService.geocodeAddress(address, hasDeprecatedAdministrativeUnit);
		return ApiResponse.success(results);
	}

	@GetMapping("/reverse")
	@PreAuthorize("hasAnyAuthority('MANAGE_STORES','MANAGE_KITCHEN_CONFIG')")
	public ApiResponse<List<GoongReverseGeocodeResult>> reverseGeocode(
			@RequestParam Double latitude,
			@RequestParam Double longitude,
			@RequestParam(value = "limit", required = false) Integer limit,
			@RequestParam(value = "hasDeprecatedAdministrativeUnit", required = false) Boolean hasDeprecatedAdministrativeUnit,
			@RequestParam(value = "hasVnid", required = false) Boolean hasVnid
	) {
		List<GoongReverseGeocodeResult> results = goongService.reverseGeocodeCoordinates(
				latitude,
				longitude,
				limit != null ? limit : 1,
				hasDeprecatedAdministrativeUnit,
				hasVnid);
		return ApiResponse.success(results);
	}
}
