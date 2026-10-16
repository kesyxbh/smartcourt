package backend.dto;

import backend.entity.Booking;
import java.time.LocalDateTime;

public record SlotResponse(LocalDateTime startTime, LocalDateTime endTime) {
    public static SlotResponse from(Booking booking) {
        return new SlotResponse(booking.getStartTime(), booking.getEndTime());
    }
}