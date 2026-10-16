package backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.entity.Facility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs the whole chat path (HTTP endpoint, conversation memory, Spring AI client, tool calling,
 * services, database) against a fake OpenAI-compatible server instead of a real model. The fake
 * model is scripted: a message containing "book" makes it call createBooking, a tool result makes
 * it answer "Booked it.", and anything else gets a plain reply.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiChatFlowIntegrationTest extends ApiTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Every request body the fake model received, in order. */
    private static final List<JsonNode> RECEIVED = new CopyOnWriteArrayList<>();
    private static volatile String toolArguments = "{}";
    private static HttpServer fakeModel;

    @BeforeAll
    static void startFakeModel() throws IOException {
        fakeModel = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeModel.createContext("/v1/chat/completions", AiChatFlowIntegrationTest::answer);
        fakeModel.start();
    }

    @AfterAll
    static void stopFakeModel() {
        fakeModel.stop(0);
    }

    @DynamicPropertySource
    static void pointTheClientAtTheFakeModel(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + fakeModel.getAddress().getPort());
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    private static void answer(HttpExchange exchange) throws IOException {
        JsonNode request = MAPPER.readTree(exchange.getRequestBody());
        RECEIVED.add(request);
        JsonNode messages = request.get("messages");
        JsonNode last = messages.get(messages.size() - 1);
        String lastText = last.path("content").toString();

        if (lastText.contains("explode")) {
            reply(exchange, 401, Map.of("error", Map.of("message", "invalid api key")));
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        String finishReason;
        if ("tool".equals(last.path("role").asText())) {
            message.put("content", "Booked it.");
            finishReason = "stop";
        } else if (lastText.contains("book")) {
            message.put("content", null);
            message.put("tool_calls", List.of(Map.of(
                    "id", "call_1",
                    "type", "function",
                    "function", Map.of("name", "createBooking", "arguments", toolArguments))));
            finishReason = "tool_calls";
        } else {
            message.put("content", "Anything else?");
            finishReason = "stop";
        }
        reply(exchange, 200, Map.of(
                "id", "chatcmpl-test",
                "object", "chat.completion",
                "created", 1,
                "model", "fake-model",
                "choices", List.of(Map.of("index", 0, "message", message, "finish_reason", finishReason)),
                "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)));
    }

    private static void reply(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private JsonNode chat(Session session, String conversationId, String message) throws Exception {
        return body(postJson("/api/ai/chat",
                Map.of("conversationId", conversationId, "message", message), session)
                .andExpect(status().isOk()));
    }

    @Test
    void theAssistantBooksForTheSignedInUserThroughATool() throws Exception {
        RECEIVED.clear();
        Facility facility = newFacility("BADMINTON", 4, "20.00");
        Session user = registerAndLogin();
        LocalDateTime start = LocalDate.now().plusDays(3).atTime(10, 0);
        toolArguments = json.writeValueAsString(Map.of(
                "facilityId", facility.getId(),
                "startTime", start.toString(),
                "endTime", start.plusHours(1).toString()));

        JsonNode reply = chat(user, "conversation-" + tag(), "please book the court");

        assertThat(reply.get("reply").asText()).isEqualTo("Booked it.");
        assertThat(reply.get("toolCalls")).hasSize(1);
        assertThat(reply.at("/toolCalls/0/name").asText()).isEqualTo("createBooking");
        assertThat(reply.at("/toolCalls/0/ok").asBoolean()).isTrue();
        assertThat(reply.at("/toolCalls/0/args/facilityId").asLong()).isEqualTo(facility.getId());

        // the booking really exists, and it belongs to the signed-in user
        JsonNode mine = body(getAs("/api/bookings/me", user).andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("facilityId").asLong()).isEqualTo(facility.getId());

        // the model was offered exactly the five tools, and none of them accepts a user id
        JsonNode firstRequest = RECEIVED.get(0);
        List<String> toolNames = new ArrayList<>();
        firstRequest.get("tools").forEach(tool -> toolNames.add(tool.at("/function/name").asText()));
        assertThat(toolNames).containsExactlyInAnyOrder(
                "searchFacilities", "getOccupiedSlots", "createBooking", "cancelBooking", "myBookings");
        assertThat(firstRequest.get("tools").toString()).doesNotContain("userId");

        // the system prompt carries today's date, so "tomorrow" can be resolved
        JsonNode system = firstRequest.get("messages").get(0);
        assertThat(system.get("role").asText()).isEqualTo("system");
        assertThat(system.get("content").asText()).contains(LocalDate.now().toString());
    }

    @Test
    void aFailedToolCallIsReportedToTheModelAndToTheClient() throws Exception {
        RECEIVED.clear();
        Facility facility = newFacility("BADMINTON", 4, "20.00");
        Session first = registerAndLogin();
        Session second = registerAndLogin();
        LocalDateTime start = LocalDate.now().plusDays(3).atTime(10, 0);
        toolArguments = json.writeValueAsString(Map.of(
                "facilityId", facility.getId(),
                "startTime", start.toString(),
                "endTime", start.plusHours(1).toString()));
        chat(first, "c-" + tag(), "book it");

        JsonNode reply = chat(second, "c-" + tag(), "book it too");

        assertThat(reply.at("/toolCalls/0/ok").asBoolean()).isFalse();
        // the error text was sent back to the model as the tool result
        JsonNode lastRequest = RECEIVED.get(RECEIVED.size() - 1);
        JsonNode toolMessage = lastRequest.get("messages").get(lastRequest.get("messages").size() - 1);
        assertThat(toolMessage.get("role").asText()).isEqualTo("tool");
        assertThat(toolMessage.get("content").asText()).contains("already booked");
    }

    @Test
    void conversationHistoryIsSentBackAndIsPrivateToTheUser() throws Exception {
        RECEIVED.clear();
        Session alice = registerAndLogin();
        Session bob = registerAndLogin();
        String sharedConversationId = "shared-" + tag();

        chat(alice, sharedConversationId, "hello there");
        chat(alice, sharedConversationId, "thanks");
        JsonNode aliceSecondRequest = RECEIVED.get(1);
        assertThat(aliceSecondRequest.get("messages").toString()).contains("hello there");

        // the same conversation id used by another user starts with an empty history
        chat(bob, sharedConversationId, "hi");
        JsonNode bobRequest = RECEIVED.get(2);
        assertThat(bobRequest.get("messages").toString()).doesNotContain("hello there");
    }

    @Test
    void aProviderFailureBecomesA502WithoutLeakingProviderDetails() throws Exception {
        Session user = registerAndLogin();

        postJson("/api/ai/chat", Map.of("conversationId", "c-" + tag(), "message", "explode"), user)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The AI provider request failed. "
                        + "Check the API key, base URL and model name."));
    }

    @Test
    void chatWithoutAConversationIdStartsANewOne() throws Exception {
        Session user = registerAndLogin();

        JsonNode reply = body(postJson("/api/ai/chat", Map.of("message", "hello"), user)
                .andExpect(status().isOk()));

        assertThat(reply.get("conversationId").asText()).isNotBlank();
        assertThat(reply.get("reply").asText()).isEqualTo("Anything else?");
    }
}
