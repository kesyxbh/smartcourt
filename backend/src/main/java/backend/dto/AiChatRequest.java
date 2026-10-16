package backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId optional; omit it to start a new conversation and reuse the id from the response afterwards
 * @param message        what the user typed
 */
public record AiChatRequest(
        @Size(max = 64) String conversationId,
        @NotBlank @Size(max = 2000) String message) {
}
