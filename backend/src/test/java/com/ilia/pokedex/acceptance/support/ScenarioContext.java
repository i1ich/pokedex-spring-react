package com.ilia.pokedex.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/** State of the running scenario: who is calling, and the last response received. */
public class ScenarioContext {

    private final int port;
    private String token;
    private Response response;

    public ScenarioContext(int port) {
        this.port = port;
    }

    /** A request to the application under test, with the bearer token when the scenario has signed in. */
    public RequestSpecification request() {
        RequestSpecification spec = RestAssured.given().port(port);
        return token == null ? spec : spec.header("Authorization", "Bearer " + token);
    }

    /** A request without credentials, whatever the scenario did before. */
    public RequestSpecification anonymousRequest() {
        return RestAssured.given().port(port);
    }

    public void signOut() {
        this.token = null;
    }

    public void signInWith(String token) {
        this.token = token;
    }

    public boolean isSignedIn() {
        return token != null;
    }

    public void remember(Response response) {
        this.response = response;
    }

    public Response response() {
        if (response == null) {
            throw new IllegalStateException("The scenario has not made a request yet");
        }
        return response;
    }
}
