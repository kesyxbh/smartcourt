package backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
@Entity
@Table(name = "facilities")
public class Facility {
    protected Facility() {
        // Required by JPA, which instantiates entities through reflection. Application code must not call it.
    }

    public Facility(String name,String type,String location,Integer capacity,BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
        this.capacity = capacity;
        status = FacilityStatus.ACTIVE;
        this.location = location;
        this.type = type;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public FacilityStatus getStatus() {
        return status;
    }

    @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false)
   private String name;

   @Column(nullable = false)
   private String type;

   @Column
   private String location;

   @Column(nullable = false)
   private Integer capacity;

   @Column(nullable = false,name = "hourly_rate")
   private BigDecimal hourlyRate;
   @Enumerated(EnumType.STRING)
   @Column(nullable = false,length = 20)
   private FacilityStatus status;

}


