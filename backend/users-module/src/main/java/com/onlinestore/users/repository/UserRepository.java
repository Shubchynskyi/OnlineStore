package com.onlinestore.users.repository;

import com.onlinestore.users.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"profile", "addresses"})
    Optional<User> findByKeycloakId(String keycloakId);
}
