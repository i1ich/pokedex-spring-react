package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.ListLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Locally stored Pokémon: sync (US03) and read endpoints. */
@RestController
@RequestMapping("/api/v1/local-pokemon")
public class LocalPokemonController {

    private final SyncPokemonUseCase syncPokemon;
    private final ListLocalPokemonUseCase listLocalPokemon;
    private final GetLocalPokemonUseCase getLocalPokemon;

    public LocalPokemonController(
            SyncPokemonUseCase syncPokemon,
            ListLocalPokemonUseCase listLocalPokemon,
            GetLocalPokemonUseCase getLocalPokemon) {
        this.syncPokemon = syncPokemon;
        this.listLocalPokemon = listLocalPokemon;
        this.getLocalPokemon = getLocalPokemon;
    }
}
