package backend.controller;

import backend.dto.BookingRequest;
import backend.dto.BookingResponse;
import backend.security.UserPrincipal;
import backend.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                  @Valid @RequestBody BookingRequest request) {
        return bookingService.create(principal.id(), request);
    }

    @GetMapping("/me")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.listForUser(principal.id());
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return bookingService.cancel(principal.id(), id);
    }
}
