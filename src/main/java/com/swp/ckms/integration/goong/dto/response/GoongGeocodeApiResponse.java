package com.swp.ckms.integration.goong.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoongGeocodeApiResponse {
	private List<GoongGeocodeResultItem> results;
	private String status;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class GoongGeocodeResultItem {
		@JsonProperty("formatted_address")
		private String formattedAddress;

		@JsonProperty("place_id")
		private String placeId;

		private Geometry geometry;

		@JsonProperty("deprecated_description")
		private String deprecatedDescription;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Geometry {
		private Location location;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Location {
		private Double lat;
		private Double lng;
	}
}
