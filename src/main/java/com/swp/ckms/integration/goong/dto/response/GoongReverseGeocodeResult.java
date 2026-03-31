package com.swp.ckms.integration.goong.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoongReverseGeocodeResult {
	private String formattedAddress;
	private String placeId;
	private Double latitude;
	private Double longitude;
	private List<String> types;
	private String deprecatedDescription;
	private List<AddressComponent> addressComponents;
	private CompoundId vnid;
	private CompoundId deprecatedVnid;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AddressComponent {
		private String longName;
		private String shortName;
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CompoundId {
		private Integer province;
		private Integer district;
		private Integer commune;
	}
}
