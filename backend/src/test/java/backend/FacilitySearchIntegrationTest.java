package backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.entity.Facility;
import backend.repository.FacilityRepository;
import backend.service.FacilityService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Search, filter, sort and pagination. Every test uses a facility type that only it creates, so the
 * assertions are not affected by the seeded data or by other tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FacilitySearchIntegrationTest extends ApiTestSupport {

    @Autowired
    private FacilityRepository repository;

    private final String type = "T_" + tag().toUpperCase();

    private Facility create(String name, int capacity, String hourlyRate) {
        return repository.save(new Facility(name, type, "Test Hall", capacity, new BigDecimal(hourlyRate)));
    }

    private List<String> names(JsonNode page) {
        List<String> result = new ArrayList<>();
        page.get("content").forEach(item -> result.add(item.get("name").asText()));
        return result;
    }

    private JsonNode search(String query) throws Exception {
        return body(getAs("/api/facilities?type=" + type + query, null).andExpect(status().isOk()));
    }

    @Test
    void listingIsPublicAndPaginated() throws Exception {
        for (int i = 1; i <= 5; i++) {
            create("Alpha " + i, 4, "10.00");
        }

        JsonNode secondPage = search("&size=2&page=1");

        assertThat(secondPage.get("content")).hasSize(2);
        assertThat(secondPage.get("page").asInt()).isEqualTo(1);
        assertThat(secondPage.get("size").asInt()).isEqualTo(2);
        assertThat(secondPage.get("totalElements").asLong()).isEqualTo(5);
        assertThat(secondPage.get("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    void pagingParametersAreClampedToSaneValues() throws Exception {
        for (int i = 1; i <= 3; i++) {
            create("Clamp " + i, 4, "10.00");
        }

        JsonNode negativePage = search("&page=-5&size=2");
        assertThat(negativePage.get("page").asInt()).isZero();
        assertThat(negativePage.get("content")).hasSize(2);

        JsonNode zeroSize = search("&size=0");
        assertThat(zeroSize.get("size").asInt()).isEqualTo(1);

        JsonNode hugeSize = search("&size=100000");
        assertThat(hugeSize.get("size").asInt()).isEqualTo(FacilityService.MAX_PAGE_SIZE);
        assertThat(hugeSize.get("content")).hasSize(3);
    }

    @Test
    void filtersCanBeCombined() throws Exception {
        create("Small cheap", 4, "20.00");
        create("Big mid", 10, "30.00");
        create("Big pricey", 10, "45.00");

        assertThat(names(search("&minPrice=25"))).containsExactlyInAnyOrder("Big mid", "Big pricey");
        assertThat(names(search("&maxPrice=30"))).containsExactlyInAnyOrder("Small cheap", "Big mid");
        assertThat(names(search("&minPrice=25&maxPrice=40"))).containsExactly("Big mid");
        assertThat(names(search("&minCapacity=10"))).containsExactlyInAnyOrder("Big mid", "Big pricey");
        assertThat(names(search("&minCapacity=10&maxPrice=30"))).containsExactly("Big mid");
        assertThat(names(search("&minCapacity=11"))).isEmpty();
    }

    @Test
    void keywordSearchIgnoresCase() throws Exception {
        String marker = "Zebra" + tag();
        create(marker + " Arena", 4, "20.00");
        create("Plain court", 4, "20.00");

        assertThat(names(search("&keyword=" + marker.toLowerCase()))).containsExactly(marker + " Arena");
    }

    @Test
    void resultsCanBeSortedByPrice() throws Exception {
        create("Cheap", 4, "10.00");
        create("Middle", 4, "20.00");
        create("Pricey", 4, "30.00");

        assertThat(names(search("&sortBy=hourlyRate&direction=desc"))).containsExactly("Pricey", "Middle", "Cheap");
        assertThat(names(search("&sortBy=hourlyRate&direction=asc"))).containsExactly("Cheap", "Middle", "Pricey");
    }

    @Test
    void unknownSortFieldFallsBackToName() throws Exception {
        create("B court", 4, "10.00");
        create("A court", 4, "20.00");

        assertThat(names(search("&sortBy=passwordHash"))).containsExactly("A court", "B court");
    }

    @Test
    void typesEndpointListsEachTypeOnce() throws Exception {
        create("One", 4, "10.00");
        create("Two", 4, "10.00");

        JsonNode types = body(getAs("/api/facilities/types", null).andExpect(status().isOk()));

        List<String> found = new ArrayList<>();
        types.forEach(item -> found.add(item.asText()));
        assertThat(found).containsOnlyOnce(type);
    }

    @Test
    void detailAndAvailabilityHandleUnknownIds() throws Exception {
        Facility facility = create("Detail", 6, "12.50");

        getAs("/api/facilities/" + facility.getId(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Detail"))
                .andExpect(jsonPath("$.capacity").value(6))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        getAs("/api/facilities/999999999", null).andExpect(status().isNotFound());
        getAs("/api/facilities/999999999/availability?date=2030-01-01", null).andExpect(status().isNotFound());
        getAs("/api/facilities/" + facility.getId() + "/availability", null).andExpect(status().isBadRequest());
    }
}
