package backend.dto;

import backend.entity.Booking;
import backend.entity.BookingStatus;
import java.time.LocalDateTime;

/**
 * @param cancellable whether the server would accept a cancellation right now. The server is the
 *                    source of truth for this, so clients do not have to repeat the 24 hour rule.
 */
public record BookingResponse(Long id, Long facilityId, String facilityName,
                              LocalDateTime startTime, LocalDateTime endTime, BookingStatus status,
                              boolean cancellable) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getFacility().getId(), booking.getFacility().getName(),
                booking.getStartTime(), booking.getEndTime(), booking.getStatus(),
                booking.isCancellableAt(LocalDateTime.now()));
    }
}
