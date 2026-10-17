Feature: The Pokédex application is up
  So that the other scenarios can rely on a running system,
  the application starts against a real database and answers its health check.

  Scenario: The health check is public and reports the application up
    Given the Pokédex application is running
    When anyone checks the health of the application
    Then the response status is 200
