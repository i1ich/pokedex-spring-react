package com.ilia.pokedex.acceptance;

import com.ilia.pokedex.acceptance.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * US03 (sync a Pokémon into my collection) and US04 (change or delete my copy). The step class keeps the version
 * the "client" last saw, because the optimistic-locking story is about acting on an outdated one.
 */
public class CollectionSteps {

    private static final String COLLECTION = "/api/v1/local-pokemon";

    @Autowired
    private ScenarioContext context;

    /** The version of the record as this client last saw it; sent back with every update. */
    private Long knownVersion;

    // --- US03: sync ---------------------------------------------------------------------------------------------

    @Given("I have synced {string}")
    public void iHaveSynced(String idOrName) {
        Response response = sync(idOrName);
        assertThat(response.statusCode()).as("sync of %s", idOrName).isIn(200, 201);
        knownVersion = response.jsonPath().getLong("version");
    }

    @When("I sync {string} from PokeAPI")
    public void iSync(String idOrName) {
        Response response = sync(idOrName);
        context.remember(response);
        if (response.statusCode() < 300) {
            knownVersion = response.jsonPath().getLong("version");
        }
    }

    @Then("the new record is located at {string}")
    public void theNewRecordIsLocatedAt(String path) {
        assertThat(context.response().header("Location")).endsWith(path);
    }

    @Then("no new record location is announced")
    public void noNewLocation() {
        assertThat(context.response().header("Location")).isNull();
    }

    @Then("the stored Pokémon is {string}, number {int}, a {string} weighing {double} kg")
    public void theStoredPokemonIs(String name, int id, String category, double weightKg) {
        JsonPath body = context.response().jsonPath();
        assertThat(body.getString("name")).isEqualTo(name);
        assertThat(body.getInt("id")).isEqualTo(id);
        assertThat(body.getString("category")).isEqualTo(category);
        assertThat(body.getDouble("weightKg")).isCloseTo(weightKg, within(0.001));
    }

    @Then("it has the abilities {string}")
    public void itHasTheAbilities(String abilities) {
        assertThat(context.response().jsonPath().getList("abilities", String.class))
                .containsExactlyElementsOf(commaSeparated(abilities));
    }

    @Then("it has no local name, region, notes or tags yet")
    public void itHasNoLocalFieldsYet() {
        JsonPath body = context.response().jsonPath();
        assertThat(body.getString("localName")).isNull();
        assertThat(body.getString("region")).isNull();
        assertThat(body.getString("notes")).isNull();
        assertThat(body.getList("tags")).isEmpty();
    }

    @Then("my collection holds {int} Pokémon")
    public void myCollectionHolds(int count) {
        Response list = context.request().get(COLLECTION);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.jsonPath().getInt("totalElements")).isEqualTo(count);
    }

    @Then("Pokémon number {int} is in my collection")
    public void pokemonIsInMyCollection(int id) {
        assertThat(context.request().get(COLLECTION + "/{id}", id).statusCode()).isEqualTo(200);
    }

    @Then("Pokémon number {int} is no longer in my collection")
    public void pokemonIsNotInMyCollection(int id) {
        assertThat(context.request().get(COLLECTION + "/{id}", id).statusCode()).isEqualTo(404);
    }

    // --- US04: update and delete --------------------------------------------------------------------------------

    @Given("I have personalised Pokémon number {int} with:")
    public void iHavePersonalised(int id, DataTable changes) {
        Response response = put(String.valueOf(id), bodyOf(changes, knownVersion));
        assertThat(response.statusCode()).as("personalising %d", id).isEqualTo(200);
        knownVersion = response.jsonPath().getLong("version");
    }

    @Given("I opened Pokémon number {int} to edit it")
    public void iOpenedToEdit(int id) {
        knownVersion = get(id).jsonPath().getLong("version");
    }

    @Given("someone else saved a change to Pokémon number {int} in the meantime")
    public void someoneElseSavedAChange(int id) {
        long theirVersion = get(id).jsonPath().getLong("version");
        Response response = put(String.valueOf(id), Map.of("localName", "Their change", "version", theirVersion));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @When("I save these changes to Pokémon number {word}:")
    public void iSaveTheseChanges(String id, DataTable changes) {
        Response response = put(id, bodyOf(changes, knownVersion));
        context.remember(response);
        if (response.statusCode() == 200) {
            knownVersion = response.jsonPath().getLong("version");
        }
    }

    /** The body is typed by hand; {@code {version}} stands for the version this client last saw. */
    @When("I send this update for Pokémon number {int}:")
    public void iSendThisUpdate(int id, String json) {
        context.remember(context.request()
                .contentType(ContentType.JSON)
                .body(json.replace("{version}", String.valueOf(knownVersion)))
                .put(COLLECTION + "/{id}", id));
    }

    @When("I delete Pokémon number {word}")
    public void iDelete(String id) {
        context.remember(context.request().delete(COLLECTION + "/{id}", id));
    }

    @Then("my copy of Pokémon number {int} now has:")
    public void myCopyNowHas(int id, DataTable expected) {
        assertThat(get(id).jsonPath().getMap("$")).containsAllEntriesOf(expectedFields(expected));
        // the response of the update itself says the same
        assertThat(context.response().jsonPath().getMap("$")).containsAllEntriesOf(expectedFields(expected));
    }

    @Then("its version went up by one")
    public void itsVersionWentUpByOne() {
        assertThat(context.response().jsonPath().getLong("version")).isEqualTo(versionBeforeLastUpdate + 1);
    }

    @Then("my copy of Pokémon number {int} has no local name")
    public void myCopyHasNoLocalName(int id) {
        assertThat(get(id).jsonPath().getString("localName")).isNull();
    }

    @Then("my copy of Pokémon number {int} has not been changed")
    public void myCopyHasNotBeenChanged(int id) {
        JsonPath stored = get(id).jsonPath();
        assertThat(stored.getLong("version")).isEqualTo(knownVersion);
        assertThat(stored.getString("localName")).isNull();
        assertThat(stored.getList("tags")).isEmpty();
    }

    @Then("my copy of Pokémon number {int} keeps the other person's local name {string}")
    public void myCopyKeepsTheOtherPersonsChange(int id, String localName) {
        assertThat(get(id).jsonPath().getString("localName")).isEqualTo(localName);
    }

    @Then("it is still the Pokémon {string}, number {int}")
    public void itIsStillThePokemon(String name, int id) {
        JsonPath body = context.response().jsonPath();
        assertThat(body.getString("name")).isEqualTo(name);
        assertThat(body.getInt("id")).isEqualTo(id);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private Long versionBeforeLastUpdate;

    private Response sync(String idOrName) {
        return context.request()
                .contentType(ContentType.JSON)
                .body(Map.of("idOrName", idOrName))
                .post(COLLECTION + "/sync");
    }

    private Response get(int id) {
        Response response = context.request().get(COLLECTION + "/{id}", id);
        assertThat(response.statusCode()).as("reading number %d", id).isEqualTo(200);
        return response;
    }

    private Response put(String id, Map<String, Object> body) {
        versionBeforeLastUpdate = knownVersion;
        return context.request().contentType(ContentType.JSON).body(body).put(COLLECTION + "/{id}", id);
    }

    /** The table gives field and value; tags are comma separated; the known version is added. */
    private static Map<String, Object> bodyOf(DataTable changes, Long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        changes.asMaps().forEach(row -> body.put(row.get("field"), valueOf(row.get("field"), row.get("value"))));
        body.put("version", version);
        return body;
    }

    private static Map<String, Object> expectedFields(DataTable expected) {
        Map<String, Object> fields = new LinkedHashMap<>();
        expected.asMaps().forEach(row -> fields.put(row.get("field"), valueOf(row.get("field"), row.get("value"))));
        return fields;
    }

    private static Object valueOf(String field, String value) {
        return "tags".equals(field) ? commaSeparated(value) : value;
    }

    private static List<String> commaSeparated(String text) {
        return text == null || text.isBlank()
                ? List.of()
                : Arrays.stream(text.split(",")).map(String::trim).toList();
    }
}
