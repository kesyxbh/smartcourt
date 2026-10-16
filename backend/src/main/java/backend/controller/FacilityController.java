package backend.controller;

import backend.dto.FacilityResponse;
import backend.dto.PageResponse;
import backend.dto.SlotResponse;
import backend.service.FacilityService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping
    public PageResponse<FacilityResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return facilityService.search(keyword, type, minPrice, maxPrice, minCapacity, page, size, sortBy, direction);
    }

    /** Declared before "/{id}"; the literal path wins over the path variable anyway. */
    @GetMapping("/types")
    public List<String> types() {
        return facilityService.listTypes();
    }

    @GetMapping("/{id}")
    public FacilityResponse get(@PathVariable Long id) {
        return facilityService.get(id);
    }

    @GetMapping("/{id}/availability")
    public List<SlotResponse> availability(@PathVariable Long id, @RequestParam LocalDate date) {
        return facilityService.occupiedSlots(id, date);
    }
}
