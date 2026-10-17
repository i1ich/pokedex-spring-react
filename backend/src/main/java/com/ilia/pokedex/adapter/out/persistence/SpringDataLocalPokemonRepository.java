package com.ilia.pokedex.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code local_pokemon}; wrapped by {@link JpaLocalPokemonRepositoryAdapter}. */
interface SpringDataLocalPokemonRepository extends JpaRepository<LocalPokemonEntity, Integer> {
}
