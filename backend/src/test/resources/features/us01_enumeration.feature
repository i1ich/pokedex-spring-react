Feature: US01 - Browse the Pokédex
  As a visitor, without signing in,
  I want to page through the Pokémon that PokeAPI knows,
  so that I can see each one's name, sprite, category, weight and abilities at a glance.

  Background:
    Given PokeAPI catalogues 1302 Pokémon
    And PokeAPI lists these Pokémon at offset 0 with limit 2:
      | bulbasaur     |
      | deoxys-normal |

  Scenario: A visitor sees the first page with the essentials of each Pokémon
    When a visitor opens page 0 of the Pokémon list with 2 Pokémon per page
    Then the response status is 200
    And the page shows these Pokémon:
      | id  | name          | category     | weightKg | abilities             |
      | 1   | bulbasaur     | Seed Pokémon | 6.9      | overgrow, chlorophyll |
      | 386 | deoxys-normal | DNA Pokémon  | 60.8     | pressure              |
    And every Pokémon on the page has a sprite
    And the list is page 0 of 651 with 1302 Pokémon in total

  Scenario: A visitor finds Pokémon by part of their name
    Given PokeAPI lists these Pokémon at offset 0 with limit 100000:
      | bulbasaur     |
      | deoxys-normal |
      | pikachu       |
      | eevee         |
    When a visitor searches the Pokémon list for "OXY"
    Then the response status is 200
    And the page shows these Pokémon:
      | id  | name          | category    | weightKg | abilities |
      | 386 | deoxys-normal | DNA Pokémon | 60.8     | pressure  |
    And the list is page 0 of 1 with 1 Pokémon in total

  Scenario: A search that matches nothing returns an empty page
    Given PokeAPI lists these Pokémon at offset 0 with limit 100000:
      | bulbasaur |
    When a visitor searches the Pokémon list for "zzz"
    Then the response status is 200
    And the list is page 0 of 0 with 0 Pokémon in total

  Scenario: A search text longer than 50 characters is refused
    When a visitor searches the Pokémon list for a text that is 51 letters long
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "q"
    And PokeAPI received no request

  Scenario: A visitor moves on to the next page
    Given PokeAPI lists these Pokémon at offset 2 with limit 2:
      | pikachu |
      | eevee   |
    When a visitor opens page 1 of the Pokémon list with 2 Pokémon per page
    Then the response status is 200
    And the page shows these Pokémon:
      | id  | name    | category | weightKg | abilities |
      | 25  | pikachu |          | 6.0      |           |
      | 133 | eevee   |          | 6.5      |           |
    And the list is page 1 of 651 with 1302 Pokémon in total

  Scenario: A visitor who does not choose a page gets the first one with the default size
    Given PokeAPI lists these Pokémon at offset 0 with limit 20:
      | bulbasaur |
    When a visitor opens the Pokémon list without choosing a page
    Then the response status is 200
    And the list uses pages of 20 Pokémon
    And the list is page 0 of 66 with 1302 Pokémon in total

  Scenario Outline: A page size outside 1 to 50 or a negative page is refused
    When a visitor opens page <page> of the Pokémon list with <size> Pokémon per page
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "<field>"
    And PokeAPI received no request

    Examples:
      | page  | size | field | reason                    |
      | 0     | 0    | size  | smaller than the minimum  |
      | 0     | 51   | size  | one more than the maximum |
      | 0     | -5   | size  | negative size             |
      | 0     | many | size  | not a number              |
      | -1    | 20   | page  | negative page             |
      | first | 20   | page  | page is not a number      |

  Scenario: The largest allowed page size is accepted
    Given PokeAPI lists these Pokémon at offset 0 with limit 50:
      | bulbasaur |
    When a visitor opens page 0 of the Pokémon list with 50 Pokémon per page
    Then the response status is 200
    And the list uses pages of 50 Pokémon

  Scenario: The visitor is told when PokeAPI is unavailable
    Given PokeAPI is failing with server errors
    When a visitor opens page 3 of the Pokémon list with 10 Pokémon per page
    Then the response is a problem with status 502 and title "Upstream service unavailable"
