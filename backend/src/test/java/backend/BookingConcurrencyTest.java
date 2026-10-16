package backend;

import static org.assertj.core.api.Assertions.assertThat;

import backend.dto.BookingRequest;
import backend.entity.Facility;
import backend.entity.User;
import backend.repository.FacilityRepository;
import backend.repository.UserRepository;
import backend.service.BookingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class BookingConcurrencyTest {

    private static final int CONTENDERS = 8;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private FacilityRepository facilities;
    @Autowired
    private UserRepository users;

    @Test
    void onlyOneOfManySimultaneousRequestsGetsTheSlot() throws Exception {
        Facility facility = facilities.save(
                new Facility("Race Court " + UUID.randomUUID(), "BADMINTON", "Test Hall", 4, new BigDecimal("20.00")));
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            String tag = UUID.randomUUID().toString().substring(0, 8);
            userIds.add(users.save(new User("racer_" + tag, tag + "@example.com", "not-a-real-hash")).getId());
        }

        LocalDateTime start = LocalDate.now().plusDays(2).atTime(10, 0);
        BookingRequest request = new BookingRequest(facility.getId(), start, start.plusHours(1));

        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        CyclicBarrier startTogether = new CyclicBarrier(CONTENDERS);
        List<Future<Integer>> outcomes = new ArrayList<>();
        try {
            for (Long userId : userIds) {
                outcomes.add(pool.submit(() -> {
                    startTogether.await(); // every thread waits here until all 8 have arrived, then they start together
                    try {
                        bookingService.create(userId, request);
                        return 201;
                    } catch (ResponseStatusException e) {
                        return e.getStatusCode().value();
                    }
                }));
            }

            int created = 0;
            int conflicts = 0;
            for (Future<Integer> outcome : outcomes) {
                int code = outcome.get();
                if (code == 201) created++;
                else if (code == 409) conflicts++;
            }
            assertThat(created).as("bookings created for the same slot").isEqualTo(1);
            assertThat(conflicts).as("requests rejected with 409").isEqualTo(CONTENDERS - 1);
        } finally {
            pool.shutdownNow();
        }
    }
}