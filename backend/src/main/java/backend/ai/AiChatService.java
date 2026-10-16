package backend.ai;

import backend.dto.AiChatRequest;
import backend.dto.AiChatResponse;
import backend.service.BookingService;
import backend.service.FacilityService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The booking assistant: a chat endpoint where the model can search facilities and create or cancel
 * the signed-in user's bookings through {@link BookingAssistantTools}.
 *
 * <p>Conversation memory is a plain in-memory map. It is keyed by "userId:conversationId" so one
 * user can never read or continue another user's conversation, it keeps only the most recent
 * messages, and it is lost on restart. That is enough for a demo; a production system would persist it.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** Messages (user + assistant) kept per conversation. Even number: turns are trimmed in pairs. */
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_CONVERSATIONS = 500;
    /** Upper bound on tool calls in one turn, so a confused model cannot loop forever. */
    private static final int MAX_TOOL_CALLS_PER_TURN = 5;

    private final ChatClient chatClient;
    private final AiAvailability availability;
    private final FacilityService facilityService;
    private final BookingService bookingService;

    /** Least recently used conversations are dropped once there are more than MAX_CONVERSATIONS. */
    private final Map<String, List<Message>> conversations = Collections.synchronizedMap(
            new LinkedHashMap<String, List<Message>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                    return size() > MAX_CONVERSATIONS;
                }
            });

    public AiChatService(ChatClient.Builder chatClientBuilder, AiAvailability availability,
                         FacilityService facilityService, BookingService bookingService) {
        this.chatClient = chatClientBuilder.build();
        this.availability = availability;
        this.facilityService = facilityService;
        this.bookingService = bookingService;
    }

    public AiChatResponse chat(Long userId, AiChatRequest request) {
        availability.requireConfigured();

        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();
        List<Message> history = conversations.computeIfAbsent(userId + ":" + conversationId, key -> new ArrayList<>());
        List<Message> snapshot;
        synchronized (history) {
            snapshot = List.copyOf(history);
        }

        BookingAssistantTools tools =
                new BookingAssistantTools(userId, facilityService, bookingService, MAX_TOOL_CALLS_PER_TURN);

        String reply;
        try {
            reply = chatClient.prompt()
                    .system(systemPrompt())
                    .messages(snapshot)
                    .user(request.message())
                    .tools(tools)
                    .call()
                    .content();
        } catch (RuntimeException e) {
            log.error("LLM request failed for user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI provider request failed. Check the API key, base URL and model name.");
        }
        if (reply == null || reply.isBlank()) {
            reply = "The assistant returned an empty reply. Please try again.";
        }

        synchronized (history) {
            history.add(new UserMessage(request.message()));
            history.add(new AssistantMessage(reply));
            while (history.size() > MAX_HISTORY_MESSAGES) {
                history.remove(0);
            }
        }
        return new AiChatResponse(conversationId, reply, tools.calls());
    }

    private String systemPrompt() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<String> types = facilityService.listTypes();
        return """
                You are the booking assistant for SmartCourt, a service for booking sports courts and meeting rooms.
                Current local date and time: %s (%s). Resolve relative dates such as "tomorrow" or "next Friday" from this.
                Facility types that exist: %s.

                Rules:
                - Use the tools to look up facilities, availability and bookings. Never invent facility ids,
                  booking ids or availability.
                - Times are local times without a time zone. Use ISO format when calling tools,
                  for example 2026-10-14T15:00:00.
                - getOccupiedSlots returns the slots that are already taken. Any other time on that day is free,
                  provided the facility status is ACTIVE.
                - Prices (hourlyRate) are plain numbers per hour. The system has no currency, so write them as
                  numbers only: never add a currency symbol or a currency name such as euro or dollar.
                - Before calling createBooking or cancelBooking, state exactly what you are about to do
                  (facility name, day, start and end time, or which booking) and ask the user to confirm.
                  Call the tool only after the user has clearly confirmed.
                - You can only act for the signed-in user. Refuse to book or cancel for anyone else.
                - If a tool returns an error, explain it plainly and suggest an alternative.
                - Reply in the language the user writes in. Keep answers short.
                - Plain text only: the chat window does not render Markdown, so do not use asterisks, pound
                  signs or tables. Use short lines, or a list with a hyphen at the start of each line.
                """.formatted(now, now.getDayOfWeek(), types.isEmpty() ? "none yet" : String.join(", ", types));
    }
}
