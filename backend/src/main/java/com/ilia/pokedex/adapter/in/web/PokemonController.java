package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.adapter.in.web.dto.PageResponse;
import com.ilia.pokedex.adapter.in.web.dto.PokemonDetailsResponse;
import com.ilia.pokedex.adapter.in.web.dto.PokemonSummaryResponse;
import com.ilia.pokedex.adapter.in.web.docs.ApiDocs;
import com.ilia.pokedex.adapter.in.web.docs.ProblemResponses;
import com.ilia.pokedex.application.port.in.GetPokemonDetailsUseCase;
import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public read endpoints backed by PokeAPI (US01, US02). */
@RestController
@RequestMapping("/api/v1/pokemon")
@Tag(name = ApiDocs.TAG_POKEMON, description = "Public, read-only view of PokeAPI (cached)")
public class PokemonController {

    private final ListPokemonUseCase listPokemon;
    private final GetPokemonDetailsUseCase getPokemonDetails;

    public PokemonController(ListPokemonUseCase listPokemon, GetPokemonDetailsUseCase getPokemonDetails) {
        this.listPokemon = listPokemon;
        this.getPokemonDetails = getPokemonDetails;
    }

    @GetMapping
    @Operation(summary = "List Pokémon", description = "One page of the PokeAPI catalogue, in PokeAPI order.")
    @ApiResponse(responseCode = "200", description = "A page of Pokémon")
    @ProblemResponses.BadRequest
    @ProblemResponses.BadGateway
    public PageResponse<PokemonSummaryResponse> list(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to 50")
            @RequestParam(defaultValue = "" + ListPokemonUseCase.DEFAULT_PAGE_SIZE) int size) {
        return PageResponse.from(listPokemon.list(page, size), PokemonSummaryResponse::from);
    }

    @GetMapping("/{idOrName}")
    @Operation(summary = "Get Pokémon details",
            description = "Details, category and evolution chain, looked up by PokeAPI id or name.")
    @ApiResponse(responseCode = "200", description = "The Pokémon")
    @ProblemResponses.BadRequest
    @ProblemResponses.NotFound
    @ProblemResponses.BadGateway
    public PokemonDetailsResponse details(
            @Parameter(description = "PokeAPI id or lowercase name", example = "pikachu")
            @PathVariable String idOrName) {
        return PokemonDetailsResponse.from(getPokemonDetails.getDetails(idOrName));
    }
}
