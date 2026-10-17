package com.ilia.pokedex.adapter.out.persistence.user;

import com.ilia.pokedex.application.port.out.UserRepository;
import com.ilia.pokedex.domain.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link UserRepository} on top of Spring Data JPA. The unique constraint on {@code username} is the final
 * arbiter for concurrent registrations: its violation is reported as the domain's "username taken" conflict.
 */
@Repository
@Transactional(readOnly = true)
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository repository;

    JpaUserRepositoryAdapter(SpringDataUserRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username).map(JpaUserRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return repository.existsByUsername(username);
    }

    @Override
    @Transactional
    public User save(User user) {
        try {
            // saveAndFlush so the constraint is checked (and the generated id known) before returning
            return toDomain(repository.saveAndFlush(toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            // the only unique/not-null constraint a validated user can break is the username
            throw User.usernameTaken(user.username());
        }
    }

    private static User toDomain(UserEntity e) {
        return new User(e.getId(), e.getUsername(), e.getPasswordHash(), e.getRole(), e.getCreatedAt());
    }

    private static UserEntity toEntity(User u) {
        return new UserEntity(u.id(), u.username(), u.passwordHash(), u.role(), u.createdAt());
    }
}
