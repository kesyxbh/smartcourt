package backend.repository;

import backend.entity.Booking;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Query("""
           select count (b)>0 from Booking b
           where b.facility.id = :facilityId
                and b.status = backend.entity.BookingStatus.CONFIRMED
                and b.startTime<:end
                and b.endTime>:start
           """)
    boolean existsConfirmedOverlap(@Param("facilityId") Long facilityId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end
                                   );
    @Query("""
            select b from Booking b
            where b.facility.id = :facilityId
              and b.status = backend.entity.BookingStatus.CONFIRMED
              and b.startTime < :to
              and b.endTime > :from
            order by b.startTime
            """)
    List<Booking> findConfirmedBetween(@Param("facilityId") Long facilityId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = "facility")
    List<Booking> findByUserIdOrderByStartTimeDesc(Long userId);
}
