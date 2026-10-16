package backend.service;

import backend.dto.BookingRequest;
import backend.dto.BookingResponse;
import backend.entity.Booking;
import backend.entity.BookingStatus;
import backend.entity.Facility;
import backend.entity.FacilityStatus;
import backend.entity.User;
import backend.repository.BookingRepository;
import backend.repository.FacilityRepository;
import backend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository, FacilityRepository facilityRepository,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BookingResponse create(Long userId, BookingRequest request) {
        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must not be in the past");
        }

        // Key step: lock the facility row first. Concurrent requests for the same facility queue up here,
        // and the lock is only released when this transaction commits or rolls back.
        Facility facility = facilityRepository.findByIdForUpdate(request.facilityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));
        if (facility.getStatus() != FacilityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Facility is not open for booking");
        }
        if (bookingRepository.existsConfirmedOverlap(facility.getId(), start, end)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time slot is already booked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        Booking booking = bookingRepository.save(new Booking(facility, user, start, end));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForUser(Long userId) {
        return bookingRepository.findByUserIdOrderByStartTimeDesc(userId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own bookings");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is already cancelled");
        }
        // The same predicate that fills BookingResponse.cancellable, so the rule exists once. At this point the
        // booking is CONFIRMED, so only the time is left to check; it also covers bookings that already started.
        if (!booking.isCancellableAt(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A booking can only be cancelled at least 24 hours before it starts");
        }
        // No save() call is needed: dirty checking makes Hibernate issue an UPDATE when the transaction
        // commits, because the status field changed.
        booking.cancel();
        return BookingResponse.from(booking);
    }
}
