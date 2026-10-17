package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.adapter.in.web.docs.ApiDocs;
import com.ilia.pokedex.adapter.in.web.docs.ProblemResponses;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = ApiDocs.TAG_LOCAL_POKEMON, description = "Your own copy of Pokémon: sync, read, edit, delete")
@SecurityRequirement(name = ApiDocs.BEARER_AUTH)
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
    @Operation(summary = "Sync a Pokémon from PokeAPI",
            description = "Copies the Pokémon into the local store. Syncing again refreshes the PokeAPI fields "
                    + "and keeps your own name, region, notes and tags.")
    @ApiResponse(responseCode = "201", description = "First sync: the record was created",
            content = @Content(schema = @Schema(implementation = LocalPokemonResponse.class)))
    @ApiResponse(responseCode = "200", description = "Already stored: the PokeAPI fields were refreshed",
            content = @Content(schema = @Schema(implementation = LocalPokemonResponse.class)))
    @ProblemResponses.BadRequest
    @ProblemResponses.Unauthorized
    @ProblemResponses.NotFound
    @ProblemResponses.BadGateway
    public ResponseEntity<LocalPokemonResponse> sync(@Valid @RequestBody SyncPokemonRequest request) {
        SyncResult result = syncPokemon.sync(request.idOrName());
        LocalPokemonResponse body = LocalPokemonResponse.from(result.pokemon());
        if (result.created()) {
            return ResponseEntity.created(URI.create(BASE_PATH + "/" + body.id())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping
    @Operation(summary = "List local Pokémon", description = "One page of the stored Pokémon, ordered by id.")
    @ApiResponse(responseCode = "200", description = "A page of local Pokémon")
    @ProblemResponses.BadRequest
    @ProblemResponses.Unauthorized
    public PageResponse<LocalPokemonResponse> list(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to 50")
            @RequestParam(defaultValue = "" + ListLocalPokemonUseCase.DEFAULT_PAGE_SIZE) int size) {
        return PageResponse.from(listLocalPokemon.list(page, size), LocalPokemonResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a local Pokémon")
    @ApiResponse(responseCode = "200", description = "The local Pokémon")
    @ProblemResponses.BadRequest
    @ProblemResponses.Unauthorized
    @ProblemResponses.NotFound
    public LocalPokemonResponse get(@Parameter(description = "PokeAPI id", example = "25") @PathVariable int id) {
        return LocalPokemonResponse.from(getLocalPokemon.get(id));
    }

    /** Replaces the proprietary fields; {@code version} must be the one last read (409 otherwise). */
    @PutMapping("/{id}")
    @Operation(summary = "Update the proprietary fields",
            description = "Replaces local name, region, notes and tags. The body must carry the version last "
                    + "read; a stale version is rejected with 409.")
    @ApiResponse(responseCode = "200", description = "Updated; the response carries the new version")
    @ProblemResponses.BadRequest
    @ProblemResponses.Unauthorized
    @ProblemResponses.NotFound
    @ProblemResponses.Conflict
    public LocalPokemonResponse update(
            @Parameter(description = "PokeAPI id", example = "25") @PathVariable int id, @Valid @RequestBody UpdateLocalPokemonRequest request) {
        return LocalPokemonResponse.from(updateLocalPokemon.update(request.toCommand(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a local Pokémon", description = "Removes the local copy, including your edits.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ProblemResponses.BadRequest
    @ProblemResponses.Unauthorized
    @ProblemResponses.NotFound
    public void delete(@Parameter(description = "PokeAPI id", example = "25") @PathVariable int id) {
        deleteLocalPokemon.delete(id);
    }
}
