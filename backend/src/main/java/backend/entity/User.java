package backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
public class User {
    public User() {
    }

    public User(String username, String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.username = username;
        this.createdAt = LocalDateTime.now();
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public Long getId() {
        return id;
    }

    @Id
    @GeneratedValue(strategy =GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true,length = 50)
    private String username;

    @Column(nullable = false,name = "password_hash")
    private String passwordHash;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    //@Column(nullable = false)
    //private String tier;

    @Column(nullable = false,name = "created_at",updatable = false)
    private LocalDateTime  createdAt;
}
