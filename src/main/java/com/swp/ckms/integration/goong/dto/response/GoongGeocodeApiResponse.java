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
		@JsonProperty("address_components")
		private List<AddressComponent> addressComponents;

		@JsonProperty("formatted_address")
		private String formattedAddress;

		@JsonProperty("place_id")
		private String placeId;

		private Geometry geometry;

		private List<String> types;

		@JsonProperty("deprecated_description")
		private String deprecatedDescription;

		@JsonProperty("compound_id")
		private CompoundId compoundId;

		@JsonProperty("deprecated_compound_id")
		private CompoundId deprecatedCompoundId;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class AddressComponent {
		@JsonProperty("long_name")
		private String longName;

		@JsonProperty("short_name")
		private String shortName;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class CompoundId {
		private Integer province;
		private Integer district;
		private Integer commune;
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
