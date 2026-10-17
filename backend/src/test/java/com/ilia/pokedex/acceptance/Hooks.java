package com.ilia.pokedex.acceptance;

import com.ilia.pokedex.acceptance.support.FakePokeApi;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

/** Makes scenarios independent although the Spring context, the database and the fake PokeAPI are shared. */
public class Hooks {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Before(order = 0)
    public void startFromAKnownState() {
        FakePokeApi.reset();
        // the PokeAPI client caches responses for hours: a stub changed by a scenario must really be called
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        // tags and abilities go with the row (ON DELETE CASCADE); users, including the demo user from V2, stay
        jdbc.update("DELETE FROM local_pokemon");
    }
}
