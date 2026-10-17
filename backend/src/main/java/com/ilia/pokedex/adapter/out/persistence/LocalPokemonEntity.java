package com.ilia.pokedex.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JPA mapping of {@code local_pokemon} and its two collection tables (see {@code V1__init.sql}). Kept in
 * the adapter: the domain only knows {@code LocalPokemon}. {@code version} is a wrapper type so that a
 * {@code null} version marks a not-yet-stored entity, which is how Spring Data chooses persist over merge.
 */
@Entity
@Table(name = "local_pokemon")
class LocalPokemonEntity {

    private static final int BATCH = 50;

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(name = "weight_hg", nullable = false)
    private int weightHg;

    @Column(name = "height_dm", nullable = false)
    private int heightDm;

    @Column(name = "sprite_url")
    private String spriteUrl;

    @Column(name = "artwork_url")
    private String artworkUrl;

    @Column(name = "local_name")
    private String localName;

    private String region;

    private String notes;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @ElementCollection
    @CollectionTable(name = "local_pokemon_ability", joinColumns = @JoinColumn(name = "pokemon_id"))
    @OrderColumn(name = "position")
    @Column(name = "ability", nullable = false)
    @BatchSize(size = BATCH)
    private List<String> abilities = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "local_pokemon_tag", joinColumns = @JoinColumn(name = "pokemon_id"))
    @Column(name = "tag", nullable = false)
    @BatchSize(size = BATCH)
    private Set<String> tags = new HashSet<>();

    protected LocalPokemonEntity() {
        // required by JPA
    }

    LocalPokemonEntity(
            Integer id, String name, String category, int weightHg, int heightDm,
            String spriteUrl, String artworkUrl, List<String> abilities,
            String localName, String region, String notes, Set<String> tags,
            Instant syncedAt, Instant updatedAt, Long version) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.weightHg = weightHg;
        this.heightDm = heightDm;
        this.spriteUrl = spriteUrl;
        this.artworkUrl = artworkUrl;
        this.abilities = new ArrayList<>(abilities);
        this.localName = localName;
        this.region = region;
        this.notes = notes;
        this.tags = new HashSet<>(tags);
        this.syncedAt = syncedAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    Integer getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getCategory() {
        return category;
    }

    int getWeightHg() {
        return weightHg;
    }

    int getHeightDm() {
        return heightDm;
    }

    String getSpriteUrl() {
        return spriteUrl;
    }

    String getArtworkUrl() {
        return artworkUrl;
    }

    String getLocalName() {
        return localName;
    }

    String getRegion() {
        return region;
    }

    String getNotes() {
        return notes;
    }

    Instant getSyncedAt() {
        return syncedAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Long getVersion() {
        return version;
    }

    List<String> getAbilities() {
        return abilities;
    }

    Set<String> getTags() {
        return tags;
    }
}
