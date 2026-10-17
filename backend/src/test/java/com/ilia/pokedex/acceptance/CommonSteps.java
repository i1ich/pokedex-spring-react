package com.ilia.pokedex.acceptance;

import com.ilia.pokedex.acceptance.support.FakePokeApi;
import com.ilia.pokedex.acceptance.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps shared by every story: the fake PokeAPI, and what a response looks like. */
public class CommonSteps {

    @Autowired
    private ScenarioContext context;

    private int catalogueSize;

    // --- PokeAPI ------------------------------------------------------------------------------------------------

    @Given("the Pokédex application is running")
    public void theApplicationIsRunning() {
        // the Spring context is started by the Cucumber/Spring integration before the scenario
    }

    @Given("PokeAPI knows the Pokémon {string}")
    public void pokeApiKnowsThePokemon(String name) {
        FakePokeApi.knowsPokemon(name);
    }

    @Given("PokeAPI catalogues {int} Pokémon")
    public void pokeApiCatalogues(int total) {
        this.catalogueSize = total;
    }

    @Given("PokeAPI lists these Pokémon at offset {int} with limit {int}:")
    public void pokeApiListsThesePokemon(int offset, int limit, DataTable names) {
        FakePokeApi.listsPokemon(catalogueSize, offset, limit, names.asList());
    }

    @Given("PokeAPI has never heard of {string}")
    public void pokeApiHasNeverHeardOf(String name) {
        FakePokeApi.doesNotKnowPokemon(name);
    }

    @Given("PokeAPI is failing with server errors")
    public void pokeApiIsFailing() {
        FakePokeApi.failEverythingWith(500);
    }

    @Then("PokeAPI received no request")
    public void pokeApiReceivedNoRequest() {
        assertThat(FakePokeApi.requestCount()).as("requests received by PokeAPI").isZero();
    }

    // --- responses ----------------------------------------------------------------------------------------------

    @When("anyone checks the health of the application")
    public void anyoneChecksTheHealth() {
        context.remember(context.request().get("/actuator/health"));
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertThat(context.response().statusCode()).isEqualTo(status);
    }

    @Then("the response is a problem with status {int} and title {string}")
    public void theResponseIsAProblem(int status, String title) {
        Response response = context.response();
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.jsonPath().getInt("status")).isEqualTo(status);
        assertThat(response.jsonPath().getString("title")).isEqualTo(title);
    }

    @Then("the problem names the field {string}")
    public void theProblemNamesTheField(String field) {
        List<String> fields = context.response().jsonPath().getList("errors.field", String.class);
        assertThat(fields).contains(field);
    }

    @Then("the problem explains {string}")
    public void theProblemExplains(String text) {
        assertThat(context.response().jsonPath().getString("detail")).contains(text);
    }
}
