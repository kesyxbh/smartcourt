package backend.ai;

import backend.dto.AiSearchRequest;
import backend.dto.AiSearchResponse;
import backend.dto.FacilityFilter;
import backend.dto.FacilityResponse;
import backend.dto.PageResponse;
import backend.service.FacilityService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Natural-language facility search. The model's only job is to turn a sentence into a
 * {@link FacilityFilter}. It never sees or returns search results: those come from the ordinary,
 * tested {@link FacilityService#search}. Because the model output is untrusted input, it is
 * validated here before it is used.
 */
@Service
public class AiSearchService {

    private static final Logger log = LoggerFactory.getLogger(AiSearchService.class);

    private static final int RESULT_SIZE = 20;

    private final ChatClient chatClient;
    private final AiAvailability availability;
    private final FacilityService facilityService;

    public AiSearchService(ChatClient.Builder chatClientBuilder, AiAvailability availability,
                           FacilityService facilityService) {
        this.chatClient = chatClientBuilder.build();
        this.availability = availability;
        this.facilityService = facilityService;
    }

    public AiSearchResponse search(AiSearchRequest request) {
        availability.requireConfigured();
        FacilityFilter extracted;
        try {
            extracted = chatClient.prompt()
                    .system(systemPrompt())
                    .user(request.query())
                    .call()
                    .entity(FacilityFilter.class);
        } catch (RuntimeException e) {
            log.error("LLM request failed while parsing a search query", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI provider request failed. Check the API key, base URL and model name.");
        }
        return toResponse(extracted);
    }

    /** Validates a filter and applies it. Public so that it can be tested without a language model. */
    public AiSearchResponse toResponse(FacilityFilter raw) {
        FacilityFilter filter = sanitize(raw);
        PageResponse<FacilityResponse> results = facilityService.search(
                filter.keyword(), filter.type(), filter.minPrice(), filter.maxPrice(), filter.minCapacity(),
                0, RESULT_SIZE, filter.sortBy(), filter.direction());
        return new AiSearchResponse(filter, explain(filter), results);
    }

    FacilityFilter sanitize(FacilityFilter raw) {
        if (raw == null) {
            return new FacilityFilter(null, null, null, null, null, null, null);
        }
        String sortBy = raw.sortBy() != null && FacilityService.SORTABLE_FIELDS.contains(raw.sortBy())
                ? raw.sortBy()
                : null;
        // A direction without a sort field means nothing, so it is dropped as well.
        String direction = sortBy == null ? null : ("desc".equalsIgnoreCase(raw.direction()) ? "desc" : "asc");
        return new FacilityFilter(
                blankToNull(raw.keyword()),
                knownType(raw.type()),
                nonNegative(raw.minPrice()),
                nonNegative(raw.maxPrice()),
                (raw.minCapacity() == null || raw.minCapacity() <= 0) ? null : raw.minCapacity(),
                sortBy,
                direction);
    }

    /** One sentence built from the validated filter, so it always matches what will be applied. */
    static String explain(FacilityFilter filter) {
        boolean anything = filter.keyword() != null || filter.type() != null || filter.minPrice() != null
                || filter.maxPrice() != null || filter.minCapacity() != null || filter.sortBy() != null;
        if (!anything) {
            return "No specific filters were recognised, so all facilities are shown.";
        }
        StringBuilder text = new StringBuilder("Showing ");
        text.append(filter.type() != null ? filter.type() : "all facility types");
        if (filter.keyword() != null) {
            text.append(" matching \"").append(filter.keyword()).append('"');
        }
        if (filter.minCapacity() != null) {
            text.append(", for at least ").append(filter.minCapacity()).append(" people");
        }
        if (filter.minPrice() != null && filter.maxPrice() != null) {
            text.append(", between ").append(filter.minPrice().toPlainString())
                    .append(" and ").append(filter.maxPrice().toPlainString()).append(" per hour");
        } else if (filter.maxPrice() != null) {
            text.append(", up to ").append(filter.maxPrice().toPlainString()).append(" per hour");
        } else if (filter.minPrice() != null) {
            text.append(", from ").append(filter.minPrice().toPlainString()).append(" per hour");
        }
        if (filter.sortBy() != null) {
            boolean ascending = "asc".equals(filter.direction());
            switch (filter.sortBy()) {
                case "hourlyRate" -> text.append(", sorted by price (")
                        .append(ascending ? "lowest first" : "highest first").append(')');
                case "capacity" -> text.append(", sorted by capacity (")
                        .append(ascending ? "smallest first" : "largest first").append(')');
                default -> text.append(", sorted by name (")
                        .append(ascending ? "A to Z" : "Z to A").append(')');
            }
        }
        return text.append('.').toString();
    }

    /** A type the model made up would silently match nothing, so unknown types are dropped. */
    private String knownType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return facilityService.listTypes().contains(normalized) ? normalized : null;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return (value == null || value.signum() < 0) ? null : value;
    }

    private String systemPrompt() {
        List<String> types = facilityService.listTypes();
        return """
                You convert a user's natural-language facility search into a JSON filter.
                Respond only with the filter.

                Fields (use null for anything the user did not mention):
                - keyword: free text matched against facility name, location and type
                - type: one of [%s]
                - minPrice, maxPrice: bounds on the hourly rate, as numbers
                - minCapacity: how many people the facility must hold
                - sortBy: one of name, hourlyRate, capacity. Only set it when the user asked for an order.
                - direction: asc or desc. Only set it together with sortBy.

                Rules:
                - "cheap" or "cheapest" means sortBy hourlyRate with direction asc.
                - "under 100" means maxPrice 100. "for 6 people" means minCapacity 6.
                - Do not repeat words in keyword that are already captured by another field.
                """.formatted(String.join(", ", types));
    }
}
