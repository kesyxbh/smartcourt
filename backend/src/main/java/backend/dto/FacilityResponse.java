package backend.dto;

import backend.entity.Facility;
import backend.entity.FacilityStatus;


import java.math.BigDecimal;

public record FacilityResponse(Long id, String name, String type,
                               String location, Integer capacity,
                               BigDecimal hourlyRate, FacilityStatus status) {
    public static  FacilityResponse from(Facility facility) {
        return new FacilityResponse(facility.getId(), facility.getName(), facility.getType(),
                facility.getLocation(), facility.getCapacity(),
                facility.getHourlyRate(), facility.getStatus());
    }
}
