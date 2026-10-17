package com.ilia.pokedex.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * Entry point of the acceptance tests: runs every {@code .feature} file under {@code src/test/resources/features}
 * on the Cucumber engine. The name ends in {@code IT}, so Failsafe runs it in {@code mvn verify} (it needs Docker
 * for PostgreSQL) while Surefire ({@code mvn test}) leaves it alone.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.ilia.pokedex.acceptance")
public class CucumberAcceptanceIT {
}
