package com.ilia.pokedex.application.port.in;

import com.ilia.pokedex.domain.model.LocalPokemon;

import java.util.List;

/** US04: edit the proprietary fields of a locally stored Pokémon, with optimistic locking. */
public interface UpdateLocalPokemonUseCase {

    /**
     * Replaces every proprietary field ({@code localName}, {@code region}, {@code notes}, {@code tags}) with
     * the values of the command; the PokeAPI-owned fields are never touched. {@code null} clears a text
     * field, {@code null} tags mean no tags.
     *
     * @return the stored record, with its new version
     * @throws com.ilia.pokedex.domain.exception.InvalidRequestException   if a field breaks a rule
     * @throws com.ilia.pokedex.domain.exception.ResourceNotFoundException if the Pokémon was never synced
     * @throws com.ilia.pokedex.domain.exception.ConflictException         if {@code version} is not the stored one
     */
    LocalPokemon update(UpdateLocalPokemonCommand command);

    /**
     * @param id      PokeAPI id, at least 1
     * @param version the version the client last read; required
     */
    record UpdateLocalPokemonCommand(
            int id, String localName, String region, String notes, List<String> tags, Long version) {
    }
}
