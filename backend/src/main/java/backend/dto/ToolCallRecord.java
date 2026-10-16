package backend.dto;

import java.util.Map;

/**
 * One tool invocation made by the assistant.
 *
 * @param name tool name, for example "createBooking"
 * @param args the arguments the model supplied, by parameter name; parameters it left out are absent
 * @param ok   false when the tool refused or failed (for example a booking conflict)
 */
public record ToolCallRecord(String name, Map<String, Object> args, boolean ok) {
}
