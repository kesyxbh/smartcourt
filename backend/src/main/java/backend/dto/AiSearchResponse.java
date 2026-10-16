package backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param filters     the filter that was actually applied, after validation (unset fields are null)
 * @param explanation one sentence describing how the request was understood. It is built from the
 *                    validated filter, not written by the model, so it always matches what is applied
 * @param results     the first page of matching facilities, found with the ordinary search. The model
 *                    never produces or sees these; clients that page through results can re-query
 *                    GET /api/facilities with the same filter
 */
public record AiSearchResponse(FacilityFilter filters, String explanation, PageResponse<FacilityResponse> results) {

    /**
     * Same object as {@link #filters()}, serialized a second time under its earlier name so that a
     * client written against that name keeps working. New code should read {@code filters}.
     */
    @JsonProperty("filter")
    public FacilityFilter filter() {
        return filters;
    }
}
