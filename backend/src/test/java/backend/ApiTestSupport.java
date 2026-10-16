package backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.entity.Facility;
import backend.repository.FacilityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Helpers shared by the HTTP-level integration tests. Every test creates its own users and
 * facilities with random names, because all test classes share one in-memory database.
 */
abstract class ApiTestSupport {

    protected static final String PASSWORD = "password123";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected FacilityRepository facilities;
    @Autowired
    protected JdbcTemplate jdbc;

    /** A registered, logged-in user. */
    protected record Session(long userId, String username, String email, String token) {
    }

    protected static String tag() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    protected Session registerAndLogin() throws Exception {
        String username = "user_" + tag();
        String email = username + "@example.com";
        postJson("/api/auth/register",
                Map.of("username", username, "email", email, "password", PASSWORD), null)
                .andExpect(status().isCreated());
        JsonNode login = body(postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD), null)
                .andExpect(status().isOk()));
        return new Session(login.at("/user/id").asLong(), username, email, login.get("token").asText());
    }

    protected Facility newFacility(String type, int capacity, String hourlyRate) {
        return facilities.save(new Facility("Court " + tag(), type, "Test Hall", capacity, new BigDecimal(hourlyRate)));
    }

    protected ResultActions postJson(String url, Object requestBody, Session session) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(requestBody));
        if (session != null) {
            request.header("Authorization", bearer(session.token()));
        }
        return mvc.perform(request);
    }

    protected ResultActions getAs(String url, Session session) throws Exception {
        var request = get(url);
        if (session != null) {
            request.header("Authorization", bearer(session.token()));
        }
        return mvc.perform(request);
    }

    protected ResultActions patchAs(String url, Session session) throws Exception {
        var request = patch(url);
        if (session != null) {
            request.header("Authorization", bearer(session.token()));
        }
        return mvc.perform(request);
    }

    protected ResultActions book(Session session, long facilityId, LocalDateTime start, LocalDateTime end)
            throws Exception {
        return postJson("/api/bookings", Map.of(
                "facilityId", facilityId,
                "startTime", start.toString(),
                "endTime", end.toString()), session);
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
