package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.adapter.in.web.dto.PageResponse;
import com.ilia.pokedex.adapter.in.web.dto.PokemonDetailsResponse;
import com.ilia.pokedex.adapter.in.web.dto.PokemonSummaryResponse;
import com.ilia.pokedex.application.port.in.GetPokemonDetailsUseCase;
import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public read endpoints backed by PokeAPI (US01, US02). */
@RestController
@RequestMapping("/api/v1/pokemon")
public class PokemonController {

    private final ListPokemonUseCase listPokemon;
    private final GetPokemonDetailsUseCase getPokemonDetails;

    public PokemonController(ListPokemonUseCase listPokemon, GetPokemonDetailsUseCase getPokemonDetails) {
        this.listPokemon = listPokemon;
        this.getPokemonDetails = getPokemonDetails;
    }

    @GetMapping
    public PageResponse<PokemonSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ListPokemonUseCase.DEFAULT_PAGE_SIZE) int size) {
        return PageResponse.from(listPokemon.list(page, size), PokemonSummaryResponse::from);
    }

    @GetMapping("/{idOrName}")
    public PokemonDetailsResponse details(@PathVariable String idOrName) {
        return PokemonDetailsResponse.from(getPokemonDetails.getDetails(idOrName));
    }
}
