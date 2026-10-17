Feature: US03 - Sync a Pokémon into my collection
  As a signed-in trainer,
  I want to copy a Pokémon from PokeAPI into my own collection,
  so that I can keep it, personalise it, and still refresh it later without losing my edits.

  Background:
    Given PokeAPI knows the Pokémon "bulbasaur"

  Scenario: Syncing a Pokémon for the first time creates my copy
    Given I am signed in as the demo user
    When I sync "bulbasaur" from PokeAPI
    Then the response status is 201
    And the new record is located at "/api/v1/local-pokemon/1"
    And the stored Pokémon is "bulbasaur", number 1, a "Seed Pokémon" weighing 6.9 kg
    And it has the abilities "overgrow, chlorophyll"
    And it has no local name, region, notes or tags yet
    And Pokémon number 1 is in my collection

  Scenario: A Pokémon can be synced by its number
    Given I am signed in as the demo user
    When I sync "1" from PokeAPI
    Then the response status is 201
    And the stored Pokémon is "bulbasaur", number 1, a "Seed Pokémon" weighing 6.9 kg

  Scenario: Syncing again refreshes the PokeAPI data and keeps everything I wrote
    Given I am signed in as the demo user
    And I have synced "bulbasaur"
    And I have personalised Pokémon number 1 with:
      | field     | value             |
      | localName | Bulby             |
      | region    | Kanto             |
      | notes     | My very first one |
      | tags      | grass, starter    |
    When I sync "bulbasaur" from PokeAPI
    Then the response status is 200
    And no new record location is announced
    And the stored Pokémon is "bulbasaur", number 1, a "Seed Pokémon" weighing 6.9 kg
    And my copy of Pokémon number 1 now has:
      | field     | value             |
      | localName | Bulby             |
      | region    | Kanto             |
      | notes     | My very first one |
      | tags      | grass, starter    |
    And my collection holds 1 Pokémon

  Scenario: Syncing the same Pokémon twice never creates a duplicate
    Given I am signed in as the demo user
    And I have synced "bulbasaur"
    When I sync "1" from PokeAPI
    Then the response status is 200
    And my collection holds 1 Pokémon

  Scenario: A Pokémon PokeAPI has never heard of is not stored
    Given I am signed in as the demo user
    And PokeAPI has never heard of "missingno"
    When I sync "missingno" from PokeAPI
    Then the response is a problem with status 404 and title "Resource not found"
    And my collection holds 0 Pokémon

  Scenario: Nothing is stored when PokeAPI is unavailable
    Given I am signed in as the demo user
    And PokeAPI is failing with server errors
    When I sync "pikachu" from PokeAPI
    Then the response is a problem with status 502 and title "Upstream service unavailable"
    And my collection holds 0 Pokémon

  Scenario Outline: An identifier that cannot be a Pokémon is refused before PokeAPI is asked
    Given I am signed in as the demo user
    When I sync "<idOrName>" from PokeAPI
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "idOrName"
    And PokeAPI received no request

    Examples:
      | idOrName   | reason             |
      | Bulbasaur  | upper case letters |
      | bulba saur | a space            |
      | pika_chu   | an underscore      |
      |            | nothing at all     |

  Scenario: An anonymous visitor cannot sync
    Given I am not signed in
    When I sync "bulbasaur" from PokeAPI
    Then the response is a problem with status 401 and title "Unauthorized"
    And PokeAPI received no request

  Scenario: A token that is not valid cannot sync either
    Given I have a token that is not valid
    When I sync "bulbasaur" from PokeAPI
    Then the response is a problem with status 401 and title "Unauthorized"
    And PokeAPI received no request
