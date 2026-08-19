package com.blocki.blocki_backend.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
		name = "users",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_users_email", columnNames = "email")
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	private User(String passwordHash, String name, String email) {
		this.passwordHash = passwordHash;
		this.name = name;
		this.email = email;
	}

	public static User register(String passwordHash, String name, String email) {
		return new User(passwordHash, name, email);
	}

	@PrePersist
	private void onCreate() {
		this.createdAt = Instant.now();
	}
}
