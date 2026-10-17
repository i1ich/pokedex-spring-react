package com.ilia.pokedex.adapter.out.persistence;

import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PageQuery;
import com.ilia.pokedex.domain.model.PageResult;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** {@link LocalPokemonRepository} on top of Spring Data JPA. */
@Repository
public class JpaLocalPokemonRepositoryAdapter implements LocalPokemonRepository {

    @Override
    public Optional<LocalPokemon> findById(int id) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public PageResult<LocalPokemon> findAll(PageQuery query) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public LocalPokemon save(LocalPokemon pokemon) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
