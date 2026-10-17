package com.ilia.pokedex.acceptance;

import com.ilia.pokedex.acceptance.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps shared by every story. */
public class CommonSteps {

    @Autowired
    private ScenarioContext context;

    @Given("the Pokédex application is running")
    public void theApplicationIsRunning() {
        // the Spring context is started by the Cucumber/Spring integration before the scenario
    }

    @When("anyone checks the health of the application")
    public void anyoneChecksTheHealth() {
        context.remember(context.request().get("/actuator/health"));
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertThat(context.response().statusCode()).isEqualTo(status);
    }
}
