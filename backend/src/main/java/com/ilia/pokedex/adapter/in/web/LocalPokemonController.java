package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.adapter.in.web.dto.LocalPokemonResponse;
import com.ilia.pokedex.adapter.in.web.dto.PageResponse;
import com.ilia.pokedex.adapter.in.web.dto.SyncPokemonRequest;
import com.ilia.pokedex.adapter.in.web.dto.UpdateLocalPokemonRequest;
import com.ilia.pokedex.application.port.in.DeleteLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.ListLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase.SyncResult;
import com.ilia.pokedex.application.port.in.UpdateLocalPokemonUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Locally stored Pokémon: sync (US03), read, update (US04) and delete endpoints. */
@RestController
@RequestMapping(LocalPokemonController.BASE_PATH)
public class LocalPokemonController {

    static final String BASE_PATH = "/api/v1/local-pokemon";

    private final SyncPokemonUseCase syncPokemon;
    private final ListLocalPokemonUseCase listLocalPokemon;
    private final GetLocalPokemonUseCase getLocalPokemon;
    private final UpdateLocalPokemonUseCase updateLocalPokemon;
    private final DeleteLocalPokemonUseCase deleteLocalPokemon;

    public LocalPokemonController(
            SyncPokemonUseCase syncPokemon,
            ListLocalPokemonUseCase listLocalPokemon,
            GetLocalPokemonUseCase getLocalPokemon,
            UpdateLocalPokemonUseCase updateLocalPokemon,
            DeleteLocalPokemonUseCase deleteLocalPokemon) {
        this.syncPokemon = syncPokemon;
        this.listLocalPokemon = listLocalPokemon;
        this.getLocalPokemon = getLocalPokemon;
        this.updateLocalPokemon = updateLocalPokemon;
        this.deleteLocalPokemon = deleteLocalPokemon;
    }

    /** 201 + {@code Location} when the record is new, 200 when an existing record was refreshed. */
    @PostMapping("/sync")
    public ResponseEntity<LocalPokemonResponse> sync(@Valid @RequestBody SyncPokemonRequest request) {
        SyncResult result = syncPokemon.sync(request.idOrName());
        LocalPokemonResponse body = LocalPokemonResponse.from(result.pokemon());
        if (result.created()) {
            return ResponseEntity.created(URI.create(BASE_PATH + "/" + body.id())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public PageResponse<LocalPokemonResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ListLocalPokemonUseCase.DEFAULT_PAGE_SIZE) int size) {
        return PageResponse.from(listLocalPokemon.list(page, size), LocalPokemonResponse::from);
    }

    @GetMapping("/{id}")
    public LocalPokemonResponse get(@PathVariable int id) {
        return LocalPokemonResponse.from(getLocalPokemon.get(id));
    }

    /** Replaces the proprietary fields; {@code version} must be the one last read (409 otherwise). */
    @PutMapping("/{id}")
    public LocalPokemonResponse update(
            @PathVariable int id, @Valid @RequestBody UpdateLocalPokemonRequest request) {
        return LocalPokemonResponse.from(updateLocalPokemon.update(request.toCommand(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        deleteLocalPokemon.delete(id);
    }
}
