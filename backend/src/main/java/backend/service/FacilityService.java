package backend.service;

import backend.dto.FacilityResponse;
import backend.dto.PageResponse;
import backend.dto.SlotResponse;
import backend.entity.Facility;
import backend.repository.BookingRepository;
import backend.repository.FacilityRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FacilityService {

    /** Only these fields may be used for sorting; anything else falls back to "name". */
    public static final Set<String> SORTABLE_FIELDS = Set.of("name", "hourlyRate", "capacity");

    /** A client cannot ask for more than this many rows at once. */
    public static final int MAX_PAGE_SIZE = 100;

    private final FacilityRepository facilityRepository;
    private final BookingRepository bookingRepository;

    public FacilityService(FacilityRepository facilityRepository, BookingRepository bookingRepository) {
        this.facilityRepository = facilityRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<FacilityResponse> search(String keyword, String type, BigDecimal minPrice,
                                                 BigDecimal maxPrice, Integer minCapacity,
                                                 int page, int size, String sortBy, String direction) {
        String safeSortBy = (sortBy != null && SORTABLE_FIELDS.contains(sortBy)) ? sortBy : "name";
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        // PageRequest.of throws for a negative page or a size below 1, which would surface as a 500.
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(sortDirection, safeSortBy));

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedType = (type == null || type.isBlank()) ? null : type.trim();
        Page<Facility> result = facilityRepository.search(
                normalizedKeyword, normalizedType, minPrice, maxPrice, minCapacity, pageable);

        return PageResponse.of(result.map(FacilityResponse::from));
    }

    @Transactional(readOnly = true)
    public FacilityResponse get(Long id) {
        Facility facility = facilityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));
        return FacilityResponse.from(facility);
    }

    @Transactional(readOnly = true)
    public List<String> listTypes() {
        return facilityRepository.findDistinctTypes();
    }

    /** Confirmed bookings that touch the given day, so a client can grey out the taken slots. */
    @Transactional(readOnly = true)
    public List<SlotResponse> occupiedSlots(Long facilityId, LocalDate date) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found");
        }
        return bookingRepository.findConfirmedBetween(facilityId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream().map(SlotResponse::from).toList();
    }
}
