package com.ilia.pokedex.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code local_pokemon}; wrapped by {@link JpaLocalPokemonRepositoryAdapter}. */
interface SpringDataLocalPokemonRepository extends JpaRepository<LocalPokemonEntity, Integer> {

    /** Deletes by id regardless of the version; returns the number of rows removed (0 or 1). */
    @Modifying
    @Query("delete from LocalPokemonEntity e where e.id = :id")
    int deleteRowById(@Param("id") int id);
}
