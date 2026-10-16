package backend.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings",
indexes = @Index(name = "idx_bookings_facility_time",columnList = "facility_id," +
        "start_time,end_time"
        ))
public class Booking {

    /** A booking can only be cancelled at least this long before it starts. */
    public static final Duration CANCELLATION_NOTICE = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id",nullable = false)
    private Facility facility;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,length = 20)
    private BookingStatus status;

    @Column(name = "created_at",nullable = false,updatable = false)
    private LocalDateTime createdAt;

    protected Booking() {

    }
    public Booking(Facility facility, User user, LocalDateTime startTime, LocalDateTime endTime) {
        this.facility = facility;
        this.user = user;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = LocalDateTime.now();
    }
    public void cancel(){
        this.status = BookingStatus.CANCELLED;
    }

    /** True while the booking is confirmed and still starts at least CANCELLATION_NOTICE from {@code now}. */
    public boolean isCancellableAt(LocalDateTime now) {
        return status == BookingStatus.CONFIRMED && !startTime.isBefore(now.plus(CANCELLATION_NOTICE));
    }

    public Long getId() {
        return id;
    }

    public Facility getFacility() {
        return facility;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
