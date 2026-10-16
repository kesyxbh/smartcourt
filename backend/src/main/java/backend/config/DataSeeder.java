package backend.config;

import backend.entity.Facility;
import backend.repository.FacilityRepository;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final FacilityRepository facilityRepository;

    public DataSeeder(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    @Override
    public void run(String... args) {
        if (facilityRepository.count() > 0) {
            return;
        }
        facilityRepository.save(new Facility("Badminton Court A", "BADMINTON", "Building 1", 4, new BigDecimal("20.00")));
        facilityRepository.save(new Facility("Badminton Court B", "BADMINTON", "Building 1", 4, new BigDecimal("25.00")));
        facilityRepository.save(new Facility("Tennis Court A", "TENNIS", "Building 2", 4, new BigDecimal("40.00")));
        facilityRepository.save(new Facility("Meeting Room 101", "MEETING_ROOM", "Building 3", 10, new BigDecimal("15.00")));
        facilityRepository.save(new Facility("Basketball Court", "BASKETBALL", "Building 2", 10, new BigDecimal("30.00")));
        // A wider spread of types, sizes and prices, so that filters, sorting and the AI search have something to work with.
        facilityRepository.save(new Facility("Badminton Court C", "BADMINTON", "Building 4", 4, new BigDecimal("35.00")));
        facilityRepository.save(new Facility("Tennis Court B", "TENNIS", "Building 2", 4, new BigDecimal("55.00")));
        facilityRepository.save(new Facility("Half Basketball Court", "BASKETBALL", "Building 5", 6, new BigDecimal("18.00")));
        facilityRepository.save(new Facility("Table Tennis Room", "TABLE_TENNIS", "Building 3", 6, new BigDecimal("12.00")));
        facilityRepository.save(new Facility("Squash Court", "SQUASH", "Building 4", 2, new BigDecimal("45.00")));
        facilityRepository.save(new Facility("Meeting Room 202", "MEETING_ROOM", "Building 3", 20, new BigDecimal("28.00")));
        facilityRepository.save(new Facility("Conference Hall", "MEETING_ROOM", "Building 6", 60, new BigDecimal("120.00")));
    }
}
