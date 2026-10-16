package backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** Registration, login and token handling, exercised through the real HTTP layer and security filters. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends ApiTestSupport {

    @Test
    void registrationReturnsPublicFieldsOnly() throws Exception {
        String tag = tag();
        postJson("/api/auth/register",
                Map.of("username", "u_" + tag, "email", "u_" + tag + "@example.com", "password", PASSWORD), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("u_" + tag))
                .andExpect(jsonPath("$.email").value("u_" + tag + "@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailOrUsernameIsRejected() throws Exception {
        Session existing = registerAndLogin();

        postJson("/api/auth/register",
                Map.of("username", "other_" + tag(), "email", existing.email(), "password", PASSWORD), null)
                .andExpect(status().isConflict());

        postJson("/api/auth/register",
                Map.of("username", existing.username(), "email", "other_" + tag() + "@example.com",
                        "password", PASSWORD), null)
                .andExpect(status().isConflict());
    }

    @Test
    void emailAddressesAreCaseInsensitive() throws Exception {
        String tag = tag();
        postJson("/api/auth/register",
                Map.of("username", "mixed_" + tag, "email", "Mixed_" + tag + "@Example.COM", "password", PASSWORD), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("mixed_" + tag + "@example.com"));

        // the same address in another case is the same account: it can log in and cannot register again
        postJson("/api/auth/login", Map.of("email", "MIXED_" + tag + "@example.com", "password", PASSWORD), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        postJson("/api/auth/register",
                Map.of("username", "second_" + tag, "email", "mixed_" + tag + "@EXAMPLE.com", "password", PASSWORD), null)
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationIsRejected() throws Exception {
        String tag = tag();
        // password shorter than 8 characters
        postJson("/api/auth/register",
                Map.of("username", "u_" + tag, "email", "u_" + tag + "@example.com", "password", "short"), null)
                .andExpect(status().isBadRequest());
        // not an email address
        postJson("/api/auth/register",
                Map.of("username", "u_" + tag, "email", "not-an-email", "password", PASSWORD), null)
                .andExpect(status().isBadRequest());
        // blank username
        postJson("/api/auth/register",
                Map.of("username", "  ", "email", "u_" + tag + "@example.com", "password", PASSWORD), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginFailuresDoNotRevealWhichPartWasWrong() throws Exception {
        Session user = registerAndLogin();

        postJson("/api/auth/login", Map.of("email", user.email(), "password", "wrong-password"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        postJson("/api/auth/login", Map.of("email", "nobody_" + tag() + "@example.com", "password", PASSWORD), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void validTokenIdentifiesTheCaller() throws Exception {
        Session user = registerAndLogin();

        getAs("/api/auth/me", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.userId()))
                .andExpect(jsonPath("$.username").value(user.username()));
    }

    @Test
    void missingMalformedOrTamperedTokensAreUnauthorized() throws Exception {
        Session user = registerAndLogin();
        String token = user.token();
        int middleOfSignature = token.length() - 10;
        char original = token.charAt(middleOfSignature);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, middleOfSignature) + replacement + token.substring(middleOfSignature + 1);

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
        assertThat(tampered).isNotEqualTo(token);
    }

    @Test
    void publicEndpointsIgnoreAnInvalidToken() throws Exception {
        // An invalid token is treated as "anonymous", which is allowed for public endpoints.
        mvc.perform(get("/api/facilities").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }
}
