package com.rental.auth.repository;

import com.rental.auth.entity.AppUser;
import com.rental.auth.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByGoogleSub(String googleSub);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findAllByRole(Role role);
    long countByRole(Role role);
}
