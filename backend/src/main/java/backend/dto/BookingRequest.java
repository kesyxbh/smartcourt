package backend.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record BookingRequest(
        @NotNull Long facilityId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime) {
}
