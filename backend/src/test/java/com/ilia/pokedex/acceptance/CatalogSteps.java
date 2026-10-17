package com.ilia.pokedex.acceptance;

import com.ilia.pokedex.acceptance.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.path.json.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** US01 (browsing the catalogue) and US02 (the detail view): both are public and read from PokeAPI. */
public class CatalogSteps {

    @Autowired
    private ScenarioContext context;

    // --- US01 ---------------------------------------------------------------------------------------------------

    @When("a visitor opens page {word} of the Pokémon list with {word} Pokémon per page")
    public void aVisitorOpensAPage(String page, String size) {
        context.remember(context.request()
                .queryParam("page", page)
                .queryParam("size", size)
                .get("/api/v1/pokemon"));
    }

    @When("a visitor opens the Pokémon list without choosing a page")
    public void aVisitorOpensTheListWithoutChoosingAPage() {
        context.remember(context.request().get("/api/v1/pokemon"));
    }

    @Then("the page shows these Pokémon:")
    public void thePageShowsThesePokemon(DataTable expected) {
        JsonPath body = context.response().jsonPath();
        List<Map<String, String>> rows = expected.asMaps();
        assertThat(body.getList("items")).as("number of Pokémon on the page").hasSize(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String item = "items[" + i + "].";
            assertThat(body.getInt(item + "id")).as("id of item %d", i).isEqualTo(Integer.parseInt(row.get("id")));
            assertThat(body.getString(item + "name")).as("name of item %d", i).isEqualTo(row.get("name"));
            assertThat(body.getString(item + "category")).as("category of %s", row.get("name"))
                    .isEqualTo(row.get("category"));
            assertThat(body.getDouble(item + "weightKg")).as("weight of %s", row.get("name"))
                    .isCloseTo(Double.parseDouble(row.get("weightKg")), within(0.001));
            assertThat(body.getList(item + "abilities", String.class)).as("abilities of %s", row.get("name"))
                    .containsExactlyElementsOf(commaSeparated(row.get("abilities")));
        }
    }

    @Then("every Pokémon on the page has a sprite")
    public void everyPokemonHasASprite() {
        List<String> sprites = context.response().jsonPath().getList("items.spriteUrl", String.class);
        assertThat(sprites).isNotEmpty().allSatisfy(sprite -> assertThat(sprite).startsWith("https://"));
    }

    @Then("the list is page {int} of {int} with {int} Pokémon in total")
    public void theListIsPage(int page, int totalPages, int totalElements) {
        JsonPath body = context.response().jsonPath();
        assertThat(body.getInt("page")).as("page").isEqualTo(page);
        assertThat(body.getInt("totalPages")).as("totalPages").isEqualTo(totalPages);
        assertThat(body.getInt("totalElements")).as("totalElements").isEqualTo(totalElements);
    }

    @Then("the list uses pages of {int} Pokémon")
    public void theListUsesPagesOf(int size) {
        assertThat(context.response().jsonPath().getInt("size")).isEqualTo(size);
    }

    // --- US02 ---------------------------------------------------------------------------------------------------

    @When("a visitor opens the details of {string}")
    public void aVisitorOpensTheDetailsOf(String idOrName) {
        context.remember(context.request().get("/api/v1/pokemon/{idOrName}", idOrName));
    }

    @When("a visitor opens the details of a name that is {int} letters long")
    public void aVisitorOpensTheDetailsOfALongName(int length) {
        aVisitorOpensTheDetailsOf("a".repeat(length));
    }

    @Then("they see the details of {string}, number {int}")
    public void theySeeTheDetailsOf(String name, int id) {
        JsonPath body = context.response().jsonPath();
        assertThat(body.getString("name")).isEqualTo(name);
        assertThat(body.getInt("id")).isEqualTo(id);
    }

    @Then("they see the image {string}")
    public void theySeeTheImage(String url) {
        assertThat(context.response().jsonPath().getString("imageUrl")).isEqualTo(url);
    }

    @Then("they see the types {string}")
    public void theySeeTheTypes(String types) {
        assertThat(context.response().jsonPath().getList("types", String.class))
                .containsExactlyElementsOf(commaSeparated(types));
    }

    @Then("they see that it is {double} m tall and weighs {double} kg")
    public void theySeeHeightAndWeight(double heightM, double weightKg) {
        JsonPath body = context.response().jsonPath();
        assertThat(body.getDouble("heightM")).isCloseTo(heightM, within(0.001));
        assertThat(body.getDouble("weightKg")).isCloseTo(weightKg, within(0.001));
    }

    @Then("they see these base stats:")
    public void theySeeTheseBaseStats(DataTable expected) {
        JsonPath body = context.response().jsonPath();
        for (Map<String, String> row : expected.asMaps()) {
            List<Integer> values = body.getList("stats.findAll { it.name == '" + row.get("stat") + "' }.baseStat");
            assertThat(values).as("stat %s", row.get("stat")).containsExactly(Integer.parseInt(row.get("value")));
        }
    }

    @Then("they read the description {string}")
    public void theyReadTheDescription(String description) {
        assertThat(context.response().jsonPath().getString("description")).isEqualTo(description);
    }

    @Then("they see the evolution {string}")
    public void theySeeTheEvolution(String chain) {
        assertThat(describe(context.response().jsonPath().getMap("evolutionChain"))).isEqualTo(chain);
    }

    /** "pichu → pikachu → raichu" for a straight line; a branch reads "eevee → (a | b | c)". */
    @SuppressWarnings("unchecked")
    private static String describe(Map<String, Object> node) {
        String name = (String) node.get("name");
        List<Map<String, Object>> next = (List<Map<String, Object>>) node.get("evolvesTo");
        if (next == null || next.isEmpty()) {
            return name;
        }
        if (next.size() == 1) {
            return name + " → " + describe(next.get(0));
        }
        List<String> branches = new ArrayList<>();
        next.forEach(child -> branches.add(describe(child)));
        return name + " → (" + String.join(" | ", branches) + ")";
    }

    private static List<String> commaSeparated(String text) {
        return text == null || text.isBlank()
                ? List.of()
                : Arrays.stream(text.split(",")).map(String::trim).toList();
    }
}
