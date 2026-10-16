package backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.entity.Facility;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** The booking rules, exercised through the real HTTP layer. */
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest extends ApiTestSupport {

    /** Midnight three days from now: far enough ahead for the 24 hour cancellation rule. */
    private final LocalDateTime day = LocalDate.now().plusDays(3).atStartOfDay();

    private LocalDateTime at(int hour) {
        return day.plusHours(hour);
    }

    private Facility facility() {
        return newFacility("BADMINTON", 4, "20.00");
    }

    @Test
    void anonymousUsersCannotBook() throws Exception {
        Facility facility = facility();

        book(null, facility.getId(), at(10), at(11)).andExpect(status().isUnauthorized());
        getAs("/api/bookings/me", null).andExpect(status().isUnauthorized());
    }

    @Test
    void overlappingBookingsAreRejectedAndTouchingOnesAreAllowed() throws Exception {
        Facility facility = facility();
        Facility otherFacility = facility();
        Session user = registerAndLogin();

        book(user, facility.getId(), at(10), at(11))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.facilityName").value(facility.getName()))
                .andExpect(jsonPath("$.cancellable").value(true));

        // identical, partially overlapping, contained in, and enclosing the existing booking
        book(user, facility.getId(), at(10), at(11)).andExpect(status().isConflict());
        book(user, facility.getId(), at(10).plusMinutes(30), at(11).plusMinutes(30)).andExpect(status().isConflict());
        book(user, facility.getId(), at(10).plusMinutes(15), at(10).plusMinutes(45)).andExpect(status().isConflict());
        book(user, facility.getId(), at(9), at(12)).andExpect(status().isConflict());

        // a booking may start exactly when another ends, or end exactly when another starts
        book(user, facility.getId(), at(11), at(12)).andExpect(status().isCreated());
        book(user, facility.getId(), at(9), at(10)).andExpect(status().isCreated());

        // the same time on a different facility is independent
        book(user, otherFacility.getId(), at(10), at(11)).andExpect(status().isCreated());
    }

    @Test
    void occupiedSlotsAreListedInOrderAndDropCancelledBookings() throws Exception {
        Facility facility = facility();
        Session user = registerAndLogin();
        book(user, facility.getId(), at(14), at(15)).andExpect(status().isCreated());
        JsonNode morning = body(book(user, facility.getId(), at(10), at(11)).andExpect(status().isCreated()));
        String url = "/api/facilities/" + facility.getId() + "/availability?date=" + day.toLocalDate();

        JsonNode slots = body(getAs(url, null).andExpect(status().isOk()));
        assertThat(slots).hasSize(2);
        assertThat(LocalDateTime.parse(slots.get(0).get("startTime").asText())).isEqualTo(at(10));
        assertThat(LocalDateTime.parse(slots.get(1).get("startTime").asText())).isEqualTo(at(14));

        patchAs("/api/bookings/" + morning.get("id").asLong() + "/cancel", user).andExpect(status().isOk());

        slots = body(getAs(url, null).andExpect(status().isOk()));
        assertThat(slots).hasSize(1);
        assertThat(LocalDateTime.parse(slots.get(0).get("startTime").asText())).isEqualTo(at(14));
    }

    @Test
    void cancellingFreesTheSlotAndCannotBeRepeated() throws Exception {
        Facility facility = facility();
        Session user = registerAndLogin();
        long id = body(book(user, facility.getId(), at(10), at(11)).andExpect(status().isCreated()))
                .get("id").asLong();

        patchAs("/api/bookings/" + id + "/cancel", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellable").value(false));
        patchAs("/api/bookings/" + id + "/cancel", user).andExpect(status().isConflict());

        book(user, facility.getId(), at(10), at(11)).andExpect(status().isCreated());
    }

    @Test
    void onlyTheOwnerCanCancelAndUnknownBookingsAreNotFound() throws Exception {
        Facility facility = facility();
        Session owner = registerAndLogin();
        Session stranger = registerAndLogin();
        long id = body(book(owner, facility.getId(), at(10), at(11)).andExpect(status().isCreated()))
                .get("id").asLong();

        patchAs("/api/bookings/" + id + "/cancel", stranger).andExpect(status().isForbidden());
        patchAs("/api/bookings/999999999/cancel", owner).andExpect(status().isNotFound());
        patchAs("/api/bookings/" + id + "/cancel", null).andExpect(status().isUnauthorized());
    }

    @Test
    void bookingsStartingWithin24HoursCannotBeCancelled() throws Exception {
        Facility facility = facility();
        Session user = registerAndLogin();

        LocalDateTime soon = LocalDateTime.now().plusHours(2);
        long soonId = body(book(user, facility.getId(), soon, soon.plusHours(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cancellable").value(false)))
                .get("id").asLong();
        patchAs("/api/bookings/" + soonId + "/cancel", user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("24 hours")));

        LocalDateTime later = LocalDateTime.now().plusHours(25);
        long laterId = body(book(user, facility.getId(), later, later.plusHours(1)).andExpect(status().isCreated()))
                .get("id").asLong();
        patchAs("/api/bookings/" + laterId + "/cancel", user).andExpect(status().isOk());
    }

    @Test
    void invalidBookingRequestsAreRejected() throws Exception {
        Facility facility = facility();
        Session user = registerAndLogin();

        // end before start, and end equal to start
        book(user, facility.getId(), at(11), at(10)).andExpect(status().isBadRequest());
        book(user, facility.getId(), at(10), at(10)).andExpect(status().isBadRequest());
        // in the past
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        book(user, facility.getId(), past, past.plusHours(2)).andExpect(status().isBadRequest());
        // missing fields
        postJson("/api/bookings", Map.of("facilityId", facility.getId()), user).andExpect(status().isBadRequest());
        // unknown facility
        book(user, 999_999_999L, at(10), at(11)).andExpect(status().isNotFound());
        // facility under maintenance
        Facility closed = facility();
        jdbc.update("update facilities set status = 'MAINTENANCE' where id = ?", closed.getId());
        book(user, closed.getId(), at(10), at(11)).andExpect(status().isConflict());
    }

    @Test
    void myBookingsListsOnlyTheCallersBookingsNewestFirst() throws Exception {
        Facility facility = facility();
        Session alice = registerAndLogin();
        Session bob = registerAndLogin();
        book(alice, facility.getId(), at(9), at(10)).andExpect(status().isCreated());
        book(alice, facility.getId(), at(15), at(16)).andExpect(status().isCreated());
        book(bob, facility.getId(), at(12), at(13)).andExpect(status().isCreated());

        JsonNode mine = body(getAs("/api/bookings/me", alice).andExpect(status().isOk()));

        assertThat(mine).hasSize(2);
        assertThat(LocalDateTime.parse(mine.get(0).get("startTime").asText())).isEqualTo(at(15));
        assertThat(LocalDateTime.parse(mine.get(1).get("startTime").asText())).isEqualTo(at(9));
    }
}
