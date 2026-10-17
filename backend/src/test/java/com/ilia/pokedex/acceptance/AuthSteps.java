package com.ilia.pokedex.acceptance;

import com.ilia.pokedex.acceptance.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Registration, login and the signed-in state shared with every protected story. */
public class AuthSteps {

    /** The account created by migration V2. */
    private static final String DEMO_USER = "demo";
    private static final String DEMO_PASSWORD = "demo12345";

    @Autowired
    private ScenarioContext context;

    @Given("I am signed in as the demo user")
    public void iAmSignedInAsTheDemoUser() {
        Response login = login(DEMO_USER, DEMO_PASSWORD);
        assertThat(login.statusCode()).as("demo login").isEqualTo(200);
        context.signInWith(login.jsonPath().getString("token"));
    }

    @Given("I am not signed in")
    public void iAmNotSignedIn() {
        context.signOut();
    }

    @Given("I have a token that is not valid")
    public void iHaveATokenThatIsNotValid() {
        context.signInWith("not.a.jwt");
    }

    @When("I register as {string} with the password {string}")
    public void iRegister(String username, String password) {
        context.remember(context.anonymousRequest()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .post("/api/v1/auth/register"));
    }

    @When("I log in as {string} with the password {string}")
    public void iLogIn(String username, String password) {
        context.remember(login(username, password));
    }

    @When("I ask who I am")
    public void iAskWhoIAm() {
        context.remember(context.request().get("/api/v1/auth/me"));
    }

    @When("I open my collection")
    public void iOpenMyCollection() {
        context.remember(context.request().get("/api/v1/local-pokemon"));
    }

    @Then("I receive a token that expires later")
    public void iReceiveAToken() {
        Response response = context.response();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("token")).isNotBlank();
        assertThat(response.jsonPath().getString("expiresAt")).isNotBlank();
        context.signInWith(response.jsonPath().getString("token"));
    }

    @Then("the application knows me as {string} with the role {string}")
    public void theApplicationKnowsMeAs(String username, String role) {
        Response me = context.request().get("/api/v1/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.jsonPath().getString("username")).isEqualTo(username);
        assertThat(me.jsonPath().getString("role")).isEqualTo(role);
    }

    @Then("the account {string} exists")
    public void theAccountExists(String username) {
        Response response = context.response();
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.jsonPath().getString("username")).isEqualTo(username);
        assertThat(response.asString()).doesNotContainIgnoringCase("password");
    }

    private Response login(String username, String password) {
        return context.anonymousRequest()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .post("/api/v1/auth/login");
    }
}
