package com.ilia.pokedex.adapter.out.persistence;

import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PageQuery;
import com.ilia.pokedex.domain.model.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link LocalPokemonRepository} on top of Spring Data JPA. Maps between the domain record and the JPA
 * entity in both directions; the entity never leaves this package. Optimistic locking is JPA's
 * {@code @Version}: saving a record whose version no longer matches the stored one fails with Spring's
 * {@code OptimisticLockingFailureException}. Collections are lazy, so every method that reads them maps to
 * the domain inside its transaction.
 */
@Repository
@Transactional(readOnly = true)
public class JpaLocalPokemonRepositoryAdapter implements LocalPokemonRepository {

    private final SpringDataLocalPokemonRepository repository;

    JpaLocalPokemonRepositoryAdapter(SpringDataLocalPokemonRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<LocalPokemon> findById(int id) {
        return repository.findById(id).map(JpaLocalPokemonRepositoryAdapter::toDomain);
    }

    @Override
    public PageResult<LocalPokemon> findAll(PageQuery query) {
        Page<LocalPokemonEntity> page = repository.findAll(
                PageRequest.of(query.page(), query.size(), Sort.by("id")));
        return new PageResult<>(
                page.getContent().stream().map(JpaLocalPokemonRepositoryAdapter::toDomain).toList(),
                query.page(),
                query.size(),
                page.getTotalElements());
    }

    @Override
    @Transactional
    public LocalPokemon save(LocalPokemon pokemon) {
        // saveAndFlush so the incremented version is visible in the returned state
        return toDomain(repository.saveAndFlush(toEntity(pokemon)));
    }

    @Override
    @Transactional
    public boolean deleteById(int id) {
        throw new UnsupportedOperationException("not implemented");
    }

    private static LocalPokemon toDomain(LocalPokemonEntity e) {
        return new LocalPokemon(
                e.getId(), e.getName(), e.getCategory(), e.getWeightHg(), e.getHeightDm(),
                e.getSpriteUrl(), e.getArtworkUrl(), e.getAbilities(),
                e.getLocalName(), e.getRegion(), e.getNotes(), e.getTags().stream().toList(),
                e.getSyncedAt(), e.getUpdatedAt(), e.getVersion());
    }

    private static LocalPokemonEntity toEntity(LocalPokemon p) {
        return new LocalPokemonEntity(
                p.id(), p.name(), p.category(), p.weightHectograms(), p.heightDecimetres(),
                p.spriteUrl(), p.artworkUrl(), p.abilities(),
                p.localName(), p.region(), p.notes(), new HashSet<>(p.tags()),
                p.syncedAt(), p.updatedAt(), p.version());
    }
}
