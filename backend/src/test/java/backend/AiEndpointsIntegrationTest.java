package backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.ai.AiSearchService;
import backend.ai.BookingAssistantTools;
import backend.dto.AiSearchResponse;
import backend.dto.BookingResponse;
import backend.dto.FacilityFilter;
import backend.dto.ToolCallRecord;
import backend.entity.Facility;
import backend.service.BookingService;
import backend.service.FacilityService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Everything about the AI layer that can be verified without calling a language model: the HTTP
 * contract when no key is configured, and the tools and the filter validation, which hold all the
 * logic that matters (the model only decides which tool to call and with which arguments).
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=not-configured")
@AutoConfigureMockMvc
class AiEndpointsIntegrationTest extends ApiTestSupport {

    @Autowired
    private FacilityService facilityService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private AiSearchService aiSearchService;

    private final LocalDateTime day = LocalDate.now().plusDays(3).atStartOfDay();

    private LocalDateTime at(int hour) {
        return day.plusHours(hour);
    }

    private BookingAssistantTools toolsFor(Session session, int maxCalls) {
        return new BookingAssistantTools(session.userId(), facilityService, bookingService, maxCalls);
    }

    // ----------------------------------------------------------------- HTTP contract

    @Test
    void aiEndpointsRequireAuthentication() throws Exception {
        postJson("/api/ai/chat", Map.of("message", "hello"), null).andExpect(status().isUnauthorized());
        postJson("/api/ai/search", Map.of("query", "cheap court"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void aiEndpointsAnswer503WhenNoKeyIsConfigured() throws Exception {
        Session user = registerAndLogin();

        postJson("/api/ai/chat", Map.of("message", "hello"), user)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("not configured")));
        postJson("/api/ai/search", Map.of("query", "cheap court"), user)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("not configured")));
    }

    @Test
    void blankInputIsRejectedBeforeAnythingElseHappens() throws Exception {
        Session user = registerAndLogin();

        postJson("/api/ai/chat", Map.of("message", "   "), user).andExpect(status().isBadRequest());
        postJson("/api/ai/search", Map.of("query", ""), user).andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------- assistant tools

    @Test
    void toolsCreateAndCancelBookingsForTheBoundUser() throws Exception {
        Facility facility = newFacility("BADMINTON", 4, "20.00");
        Session user = registerAndLogin();
        BookingAssistantTools tools = toolsFor(user, 5);

        Map<String, Object> created = tools.createBooking(facility.getId(), at(10).toString(), at(11).toString());
        assertThat(created).containsEntry("ok", true);

        Map<String, Object> mine = tools.myBookings();
        assertThat(mine).containsEntry("ok", true);
        assertThat((List<?>) mine.get("data")).hasSize(1);

        assertThat(tools.calls()).extracting(ToolCallRecord::name).containsExactly("createBooking", "myBookings");
        assertThat(tools.calls()).allMatch(ToolCallRecord::ok);
        assertThat(tools.calls().get(0).args()).containsEntry("facilityId", facility.getId());
        assertThat(tools.calls().get(1).args()).isEmpty();
    }

    @Test
    void toolsReportBusinessFailuresAsDataInsteadOfThrowing() throws Exception {
        Facility facility = newFacility("BADMINTON", 4, "20.00");
        Session user = registerAndLogin();
        BookingAssistantTools tools = toolsFor(user, 5);
        tools.createBooking(facility.getId(), at(10).toString(), at(11).toString());

        Map<String, Object> conflict = tools.createBooking(facility.getId(), at(10).toString(), at(11).toString());
        Map<String, Object> badTime = tools.createBooking(facility.getId(), "tomorrow at three", at(11).toString());
        Map<String, Object> unknown = tools.createBooking(999_999_999L, at(10).toString(), at(11).toString());

        assertThat(conflict).containsEntry("ok", false);
        assertThat((String) conflict.get("error")).contains("already booked");
        assertThat(badTime).containsEntry("ok", false);
        assertThat((String) badTime.get("error")).contains("Invalid date");
        assertThat(unknown).containsEntry("ok", false);
        assertThat((String) unknown.get("error")).contains("not found");
        assertThat(tools.calls()).extracting(ToolCallRecord::ok).containsExactly(true, false, false, false);
    }

    @Test
    void toolsCannotCancelAnotherUsersBooking() throws Exception {
        Facility facility = newFacility("BADMINTON", 4, "20.00");
        Session owner = registerAndLogin();
        Session stranger = registerAndLogin();
        Map<String, Object> created = toolsFor(owner, 5)
                .createBooking(facility.getId(), at(10).toString(), at(11).toString());
        long bookingId = ((BookingResponse) created.get("data")).id();

        Map<String, Object> result = toolsFor(stranger, 5).cancelBooking(bookingId);

        assertThat(result).containsEntry("ok", false);
        assertThat((String) result.get("error")).contains("own bookings");
    }

    @Test
    void cancellationRuleAppliesToTools() throws Exception {
        Facility facility = newFacility("BADMINTON", 4, "20.00");
        Session user = registerAndLogin();
        BookingAssistantTools tools = toolsFor(user, 5);
        LocalDateTime soon = LocalDateTime.now().plusHours(2);
        Map<String, Object> created = tools.createBooking(facility.getId(), soon.toString(), soon.plusHours(1).toString());
        long bookingId = ((BookingResponse) created.get("data")).id();

        Map<String, Object> result = tools.cancelBooking(bookingId);

        assertThat(result).containsEntry("ok", false);
        assertThat((String) result.get("error")).contains("24 hours");
    }

    @Test
    void searchToolFiltersFacilities() throws Exception {
        String type = "AI_" + tag().toUpperCase();
        facilities.save(new Facility("Match me", type, "Test Hall", 8, new BigDecimal("15.00")));
        facilities.save(new Facility("Too small", type, "Test Hall", 2, new BigDecimal("15.00")));
        BookingAssistantTools tools = toolsFor(registerAndLogin(), 5);

        Map<String, Object> result = tools.searchFacilities(null, type, null, null, 6);

        assertThat(result).containsEntry("ok", true);
        List<?> data = (List<?>) result.get("data");
        assertThat(data).hasSize(1);
        // arguments the model did not supply are not recorded
        assertThat(tools.calls().get(0).args()).containsOnlyKeys("type", "minCapacity");
    }

    @Test
    void toolCallsAreCappedPerTurn() throws Exception {
        BookingAssistantTools tools = toolsFor(registerAndLogin(), 2);

        tools.myBookings();
        tools.myBookings();
        Map<String, Object> third = tools.myBookings();

        assertThat(third).containsEntry("ok", false);
        assertThat((String) third.get("error")).contains("limit");
        assertThat(tools.calls()).hasSize(2);
    }

    // ----------------------------------------------------------------- natural-language search

    @Test
    void modelOutputIsValidatedBeforeItIsReturned() {
        String type = "AI_" + tag().toUpperCase();
        facilities.save(new Facility("Any", type, "Hall", 12, new BigDecimal("30.00")));

        // a type the model invented, a negative price, a non-positive capacity and an unknown sort field
        AiSearchResponse invalid = aiSearchService.toResponse(
                new FacilityFilter("  ", "SPACESHIP", new BigDecimal("-5"), null, 0, "passwordHash", "sideways"));

        assertThat(invalid.filters()).isEqualTo(new FacilityFilter(null, null, null, null, null, null, null));
        assertThat(invalid.explanation()).contains("No specific filters");

        // a real type written the way a person would write it is normalised
        AiSearchResponse valid = aiSearchService.toResponse(new FacilityFilter(
                " court ", type.toLowerCase().replace('_', ' '), null, new BigDecimal("40"), 10, "capacity", "DESC"));

        assertThat(valid.filters().type()).isEqualTo(type);
        assertThat(valid.filters().keyword()).isEqualTo("court");
        assertThat(valid.filters().sortBy()).isEqualTo("capacity");
        assertThat(valid.filters().direction()).isEqualTo("desc");
    }

    @Test
    void aDirectionWithoutASortFieldIsDropped() {
        AiSearchResponse response = aiSearchService.toResponse(
                new FacilityFilter(null, null, null, null, null, null, "desc"));

        assertThat(response.filters().direction()).isNull();
    }

    @Test
    void explanationDescribesTheAppliedFilter() {
        String type = "AI_" + tag().toUpperCase();
        facilities.save(new Facility("Any", type, "Hall", 12, new BigDecimal("30.00")));

        AiSearchResponse response = aiSearchService.toResponse(new FacilityFilter(
                "arena", type, null, new BigDecimal("25"), 6, "hourlyRate", "asc"));

        assertThat(response.explanation())
                .isEqualTo("Showing " + type + " matching \"arena\", for at least 6 people, "
                        + "up to 25 per hour, sorted by price (lowest first).");
    }

    @Test
    void searchResponseHasTheShapeTheFrontendReads() throws Exception {
        String type = "AI_" + tag().toUpperCase();
        facilities.save(new Facility("Cheap one", type, "Hall", 12, new BigDecimal("10.00")));
        facilities.save(new Facility("Pricey one", type, "Hall", 12, new BigDecimal("90.00")));

        AiSearchResponse response = aiSearchService.toResponse(
                new FacilityFilter(null, type, null, new BigDecimal("40"), null, null, null));
        JsonNode body = json.readTree(json.writeValueAsString(response));

        // unset filter fields are JSON null (not missing); results are the first page of the normal search
        assertThat(body.get("filters").get("type").asText()).isEqualTo(type);
        assertThat(body.get("filters").get("keyword").isNull()).isTrue();
        // the earlier name stays available, with identical content
        assertThat(body.get("filter")).isEqualTo(body.get("filters"));
        assertThat(body.get("explanation").asText()).contains("up to 40 per hour");
        assertThat(body.at("/results/totalElements").asLong()).isEqualTo(1);
        assertThat(body.at("/results/content/0/name").asText()).isEqualTo("Cheap one");
    }

    @Test
    void aMissingFilterFallsBackToNoFilters() {
        AiSearchResponse response = aiSearchService.toResponse(null);

        assertThat(response.filters()).isEqualTo(new FacilityFilter(null, null, null, null, null, null, null));
    }
}
