package dev.shubham.transcoder.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered user who owns jobs. Maps the {@code users} table. Created via {@link #create}.
 */
@Entity
@Table(name = "users")
public class User {

    // DB generates the id via the `gen_random_uuid()` default in V1__init.sql; Hibernate
    // excludes it from INSERT and reads the generated value back.
    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // for JPA
    }

    /** Create a new user. The id is generated on persist. */
    public static User create(String email) {
        User user = new User();
        user.email = email;
        return user;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
