package com.ilia.pokedex.adapter.out.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data access to {@code app_user}; wrapped by {@link JpaUserRepositoryAdapter}. */
interface SpringDataUserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
