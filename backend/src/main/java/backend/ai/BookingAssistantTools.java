package backend.ai;

import backend.dto.BookingRequest;
import backend.dto.ToolCallRecord;
import backend.service.BookingService;
import backend.service.FacilityService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The actions the booking assistant may perform. This is a thin adapter over the existing
 * services: every business rule (overlap check, ownership check, 24 hour cancellation notice)
 * still lives in BookingService, so the model can never do anything a normal API client could not.
 *
 * <p>An instance is created for every chat request and is bound to the signed-in user. The user id
 * comes from the server side, never from the model: none of the tools has a userId parameter, so a
 * prompt such as "book this for user 7" cannot act on someone else's behalf.
 *
 * <p>Every tool returns a map of the form {ok:true, data} or {ok:false, error}. Business failures are
 * reported to the model as data instead of exceptions, so it can explain them to the user.
 */
public class BookingAssistantTools {

    private static final Logger log = LoggerFactory.getLogger(BookingAssistantTools.class);

    static final int MAX_SEARCH_RESULTS = 10;
    static final int MAX_BOOKINGS_LISTED = 20;

    private final Long userId;
    private final FacilityService facilityService;
    private final BookingService bookingService;
    private final int maxCalls;
    private final List<ToolCallRecord> calls = new CopyOnWriteArrayList<>();

    public BookingAssistantTools(Long userId, FacilityService facilityService, BookingService bookingService,
                                 int maxCalls) {
        this.userId = userId;
        this.facilityService = facilityService;
        this.bookingService = bookingService;
        this.maxCalls = maxCalls;
    }

    /** The tools invoked so far, in order. */
    public List<ToolCallRecord> calls() {
        return List.copyOf(calls);
    }

    @Tool(description = "Search facilities. Every filter is optional. Returns at most 10 facilities with "
            + "id, name, type, location, capacity, hourlyRate and status.")
    public Map<String, Object> searchFacilities(
            @ToolParam(required = false, description = "Free text matched against name, location and type") String keyword,
            @ToolParam(required = false, description = "Exact facility type, for example BADMINTON") String type,
            @ToolParam(required = false, description = "Minimum hourly rate") BigDecimal minPrice,
            @ToolParam(required = false, description = "Maximum hourly rate") BigDecimal maxPrice,
            @ToolParam(required = false, description = "Minimum number of people the facility must hold") Integer minCapacity) {
        Map<String, Object> args = describe("keyword", keyword, "type", type, "minPrice", minPrice,
                "maxPrice", maxPrice, "minCapacity", minCapacity);
        return invoke("searchFacilities", args, () -> facilityService
                .search(keyword, type, minPrice, maxPrice, minCapacity, 0, MAX_SEARCH_RESULTS, "name", "asc")
                .content());
    }

    @Tool(description = "List the time slots that are already booked for a facility on one day. "
            + "Any time on that day outside these slots is free, as long as the facility status is ACTIVE.")
    public Map<String, Object> getOccupiedSlots(
            @ToolParam(description = "Facility id, taken from searchFacilities") Long facilityId,
            @ToolParam(description = "The day, in ISO format, for example 2026-10-14") String date) {
        return invoke("getOccupiedSlots", describe("facilityId", facilityId, "date", date),
                () -> facilityService.occupiedSlots(facilityId, LocalDate.parse(date)));
    }

    @Tool(description = "Create a booking for the signed-in user. Only call this after the user has "
            + "explicitly confirmed the facility, day and times. Fails if the slot is taken.")
    public Map<String, Object> createBooking(
            @ToolParam(description = "Facility id, taken from searchFacilities") Long facilityId,
            @ToolParam(description = "Start, local time in ISO format, for example 2026-10-14T15:00:00") String startTime,
            @ToolParam(description = "End, local time in ISO format, for example 2026-10-14T16:00:00") String endTime) {
        return invoke("createBooking", describe("facilityId", facilityId, "startTime", startTime, "endTime", endTime),
                () -> bookingService.create(userId,
                        new BookingRequest(facilityId, LocalDateTime.parse(startTime), LocalDateTime.parse(endTime))));
    }

    @Tool(description = "Cancel one of the signed-in user's own bookings. Only call this after the user has "
            + "explicitly confirmed which booking to cancel. Bookings that start in less than 24 hours cannot be cancelled.")
    public Map<String, Object> cancelBooking(
            @ToolParam(description = "Booking id, taken from myBookings") Long bookingId) {
        return invoke("cancelBooking", describe("bookingId", bookingId),
                () -> bookingService.cancel(userId, bookingId));
    }

    @Tool(description = "List the signed-in user's bookings, newest first (at most 20), including cancelled ones. "
            + "Each booking says whether it is currently cancellable.")
    public Map<String, Object> myBookings() {
        return invoke("myBookings", Map.of(), () -> bookingService.listForUser(userId).stream()
                .limit(MAX_BOOKINGS_LISTED)
                .toList());
    }

    private Map<String, Object> invoke(String tool, Map<String, Object> args, Supplier<Object> action) {
        if (calls.size() >= maxCalls) {
            log.warn("AI tool call limit reached: user={} tool={}", userId, tool);
            return error("Tool call limit reached for this turn. Answer the user with what you already know.");
        }
        try {
            Object data = action.get();
            record(tool, args, true);
            return Map.of("ok", true, "data", data);
        } catch (ResponseStatusException e) {
            record(tool, args, false);
            String reason = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
            return error(reason);
        } catch (DateTimeParseException e) {
            record(tool, args, false);
            return error("Invalid date or time. Use ISO format, for example 2026-10-14 or 2026-10-14T15:00:00.");
        }
    }

    private void record(String tool, Map<String, Object> args, boolean ok) {
        calls.add(new ToolCallRecord(tool, args, ok));
        log.info("AI tool call: user={} tool={} args={} ok={}", userId, tool, args, ok);
    }

    private static Map<String, Object> error(String message) {
        return Map.of("ok", false, "error", message);
    }

    /** Builds an ordered map from alternating names and values, skipping the ones the model left out. */
    private static Map<String, Object> describe(Object... namesAndValues) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (namesAndValues[i + 1] != null) {
                args.put(String.valueOf(namesAndValues[i]), namesAndValues[i + 1]);
            }
        }
        return args;
    }
}
