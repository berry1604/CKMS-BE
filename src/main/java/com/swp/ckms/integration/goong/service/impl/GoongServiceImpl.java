package com.swp.ckms.integration.goong.service.impl;

import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.exception.system.ExternalServiceException;
import com.swp.ckms.exception.validation.InvalidRequestException;
import com.swp.ckms.integration.goong.config.GoongProperties;
import com.swp.ckms.integration.goong.dto.response.GoongGeocodeApiResponse;
import com.swp.ckms.integration.goong.dto.response.GoongGeocodeResult;
import com.swp.ckms.integration.goong.service.GoongService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoongServiceImpl implements GoongService {

	private final GoongProperties properties;
	private final WebClient webClient;

	@Override
	public List<GoongGeocodeResult> geocodeAddress(String address) {
		return geocodeAddress(address, properties.getHasDeprecatedAdministrativeUnit());
	}

	@Override
	public List<GoongGeocodeResult> geocodeAddress(String address, Boolean hasDeprecatedAdministrativeUnit) {
		if (address == null || address.isBlank()) {
			throw new InvalidRequestException("Address is required for geocoding");
		}
		if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
			throw new ExternalServiceException("Goong base URL is not configured");
		}
		if (properties.getKey() == null || properties.getKey().isBlank()) {
			throw new ExternalServiceException("Goong API key is not configured");
		}

		String endpoint = properties.getBaseUrl() + "/v2/geocode";
		Boolean deprecatedUnitFlag = hasDeprecatedAdministrativeUnit != null
			? hasDeprecatedAdministrativeUnit
			: properties.getHasDeprecatedAdministrativeUnit();

		try {
			GoongGeocodeApiResponse response = webClient.get()
			    .uri(endpoint + "?address={address}&api_key={apiKey}&has_deprecated_administrative_unit={hasDeprecatedAdministrativeUnit}",
				    address,
				    properties.getKey(),
				    deprecatedUnitFlag)
					.retrieve()
					.bodyToMono(GoongGeocodeApiResponse.class)
					.block();

			if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
				throw new ResourceNotFoundException("No geocode results found for address: " + address);
			}

			List<GoongGeocodeResult> mappedResults = response.getResults().stream()
					.map(this::mapResult)
				    .filter(Objects::nonNull)
					.toList();

			if (mappedResults.isEmpty()) {
				throw new ResourceNotFoundException("No geocode results found for address: " + address);
			}

			return mappedResults;
		} catch (ResourceNotFoundException ex) {
			throw ex;
		} catch (WebClientResponseException ex) {
			log.error("Goong geocode request failed. status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
			throw new ExternalServiceException("Goong geocode request failed: " + ex.getResponseBodyAsString());
		} catch (Exception ex) {
			log.error("Unexpected error while calling Goong geocode", ex);
			throw new ExternalServiceException("Unexpected error while calling Goong geocode service");
		}
	}

	@Override
	public GoongGeocodeResult geocodeFirstAddress(String address) {
		List<GoongGeocodeResult> results = geocodeAddress(address);
		if (results.isEmpty()) {
			throw new ResourceNotFoundException("No geocode results found for address: " + address);
		}
		return results.getFirst();
	}

	private GoongGeocodeResult mapResult(GoongGeocodeApiResponse.GoongGeocodeResultItem item) {
		if (item == null || item.getGeometry() == null || item.getGeometry().getLocation() == null) {
			return null;
		}

		Double latitude = item.getGeometry().getLocation().getLat();
		Double longitude = item.getGeometry().getLocation().getLng();
		if (latitude == null || longitude == null) {
			return null;
		}

		return GoongGeocodeResult.builder()
				.formattedAddress(item.getFormattedAddress())
				.placeId(item.getPlaceId())
				.latitude(latitude)
				.longitude(longitude)
				.deprecatedDescription(item.getDeprecatedDescription())
				.build();
	}
}
