package backend.dto;

import java.math.BigDecimal;

/**
 * The structured form of a facility search. The language model fills this in from a natural-language
 * sentence. A null field means "do not filter on this", and it is sent to clients as JSON null.
 */
public record FacilityFilter(
        String keyword,
        String type,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minCapacity,
        String sortBy,
        String direction) {
}
