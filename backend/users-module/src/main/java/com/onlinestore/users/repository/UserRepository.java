package com.onlinestore.users.repository;

import com.onlinestore.users.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @EntityGraph(attributePaths = {"profile", "addresses"})
    Optional<User> findById(Long id);

    @EntityGraph(attributePaths = {"profile", "addresses"})
    Optional<User> findByKeycloakId(String keycloakId);
}
