package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.UpdateLocalPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.exception.ConflictException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.LocalPokemon;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * US04: replaces the proprietary fields of a stored Pokémon. The business rules live here, in the order
 * they are checked (input first, so a bad request never reaches the store):
 *
 * <ul>
 *   <li>{@code id >= 1}; {@code version} present and not negative;</li>
 *   <li>{@code localName}: trimmed, at most 100 characters, and not blank when present;
 *       {@code region} (100) and {@code notes} (1000): trimmed, blank means cleared ({@code null});</li>
 *   <li>tags: {@code null} means none; each is trimmed and lowercased, then must be lowercase kebab
 *       ({@code [a-z0-9]+(-[a-z0-9]+)*}, 1 to 50 characters); duplicates collapse silently; at most 10
 *       remain;</li>
 *   <li>the record must exist (404) and {@code version} must equal the stored one (409).</li>
 * </ul>
 *
 * The store enforces the version again at write time, which closes the race between the check and the write.
 * The web DTO repeats the size limits as a cheap shape check on the raw values; whatever it lets through is
 * decided here.
 */
public class UpdateLocalPokemonService implements UpdateLocalPokemonUseCase {

    static final int LOCAL_NAME_MAX = 100;
    static final int REGION_MAX = 100;
    static final int NOTES_MAX = 1000;
    static final int TAG_MAX_LENGTH = 50;
    static final int MAX_TAGS = 10;
    private static final Pattern KEBAB = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final LocalPokemonRepository repository;
    private final Clock clock;

    public UpdateLocalPokemonService(LocalPokemonRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LocalPokemon update(UpdateLocalPokemonCommand command) {
        if (command.id() < 1) {
            throw new InvalidRequestException("id", "id must be greater than or equal to 1");
        }
        if (command.version() == null) {
            throw new InvalidRequestException("version", "version is required");
        }
        if (command.version() < 0) {
            throw new InvalidRequestException("version", "version must not be negative");
        }
        String localName = text("localName", command.localName(), LOCAL_NAME_MAX, false);
        String region = text("region", command.region(), REGION_MAX, true);
        String notes = text("notes", command.notes(), NOTES_MAX, true);
        List<String> tags = normaliseTags(command.tags());

        LocalPokemon stored = repository.findById(command.id())
                .orElseThrow(() -> new ResourceNotFoundException("Local Pokemon", command.id()));
        if (!command.version().equals(stored.version())) {
            throw new ConflictException("Pokémon " + stored.id() + " was modified by someone else (current version "
                    + stored.version() + "); reload and try again");
        }
        return repository.save(stored.withProprietaryFields(localName, region, notes, tags, clock.instant()));
    }

    /** Trims; {@code null} stays {@code null}; a blank value is {@code null} when allowed, an error otherwise. */
    private static String text(String field, String value, int maxLength, boolean blankMeansCleared) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            if (blankMeansCleared) {
                return null;
            }
            throw new InvalidRequestException(field, field + " must not be blank when present");
        }
        if (trimmed.length() > maxLength) {
            throw new InvalidRequestException(field, field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static List<String> normaliseTags(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String field = "tags[" + i + "]";
            String tag = raw.get(i);
            if (tag == null) {
                throw new InvalidRequestException(field, "a tag must not be null");
            }
            String normalised = tag.strip().toLowerCase(Locale.ROOT);
            if (normalised.isEmpty() || normalised.length() > TAG_MAX_LENGTH || !KEBAB.matcher(normalised).matches()) {
                throw new InvalidRequestException(field, "a tag must be lowercase kebab-case (letters, digits and "
                        + "single hyphens) of 1 to " + TAG_MAX_LENGTH + " characters");
            }
            tags.add(normalised);
        }
        if (tags.size() > MAX_TAGS) {
            throw new InvalidRequestException("tags", "at most " + MAX_TAGS + " distinct tags are allowed");
        }
        return List.copyOf(tags);
    }
}
