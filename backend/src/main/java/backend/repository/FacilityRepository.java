package backend.repository;

import backend.entity.Facility;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    /**
     * Every filter is optional. A null argument means "do not filter on this field":
     * "(:param is null or ...)" is true for every row when the parameter is null.
     */
    @Query("""
            select f from Facility f
            where (:keyword is null
                   or lower(f.name) like lower(concat('%', :keyword, '%'))
                   or lower(f.location) like lower(concat('%', :keyword, '%'))
                   or lower(f.type) like lower(concat('%', :keyword, '%')))
              and (:type is null or f.type = :type)
              and (:minPrice is null or f.hourlyRate >= :minPrice)
              and (:maxPrice is null or f.hourlyRate <= :maxPrice)
              and (:minCapacity is null or f.capacity >= :minCapacity)
            """)
    Page<Facility> search(@Param("keyword") String keyword,
                          @Param("type") String type,
                          @Param("minPrice") BigDecimal minPrice,
                          @Param("maxPrice") BigDecimal maxPrice,
                          @Param("minCapacity") Integer minCapacity,
                          Pageable pageable);

    /** The facility types that currently exist, used by filter dropdowns and by the AI prompts. */
    @Query("select distinct f.type from Facility f order by f.type")
    List<String> findDistinctTypes();

    /**
     * Loads the facility row with SELECT ... FOR UPDATE. BookingService uses it as a per-facility
     * mutex, so concurrent bookings for the same facility run one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Facility f where f.id = :id")
    Optional<Facility> findByIdForUpdate(@Param("id") Long id);
}
