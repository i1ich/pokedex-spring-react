package com.ilia.pokedex.adapter.out.persistence.user;

import com.ilia.pokedex.application.port.out.UserRepository;
import com.ilia.pokedex.domain.model.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** STUB (red step). */
@Repository
public class JpaUserRepositoryAdapter implements UserRepository {

    @Override
    public Optional<User> findByUsername(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsByUsername(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public User save(User user) {
        throw new UnsupportedOperationException();
    }
}
