Feature: US02 - See the details of a Pokémon
  As a visitor, without signing in,
  I want to open one Pokémon by its name or number,
  so that I can see its artwork, types, stats, description and how it evolves.

  Scenario: A visitor opens a Pokémon by name and sees everything about it
    Given PokeAPI knows the Pokémon "pikachu"
    When a visitor opens the details of "pikachu"
    Then the response status is 200
    And they see the details of "pikachu", number 25
    And they see the image "https://img.test/artwork/25.png"
    And they see the types "electric"
    And they see that it is 0.4 m tall and weighs 6.0 kg
    And they see these base stats:
      | stat   | value |
      | hp     | 35    |
      | attack | 55    |
      | speed  | 90    |
    And they read the description "When several of these POKéMON gather, their electricity could build and cause lightning storms."
    And they see the evolution "pichu → pikachu → raichu"

  Scenario: A visitor can look a Pokémon up by its number
    Given PokeAPI knows the Pokémon "pikachu"
    When a visitor opens the details of "25"
    Then the response status is 200
    And they see the details of "pikachu", number 25

  Scenario: A Pokémon with several evolutions shows every branch
    Given PokeAPI knows the Pokémon "eevee"
    When a visitor opens the details of "eevee"
    Then the response status is 200
    And they see the details of "eevee", number 133
    And they see the evolution "eevee → (vaporeon | jolteon | flareon | umbreon)"

  Scenario: A Pokémon that does not evolve shows only itself
    Given PokeAPI knows the Pokémon "ditto"
    When a visitor opens the details of "ditto"
    Then the response status is 200
    And they see the evolution "ditto"

  Scenario: The plain sprite stands in when PokeAPI has no official artwork
    Given PokeAPI knows the Pokémon "ditto"
    When a visitor opens the details of "ditto"
    Then they see the image "https://img.test/132.png"

  Scenario: A Pokémon whose form name differs from its species still shows its details
    Given PokeAPI knows the Pokémon "deoxys-normal"
    When a visitor opens the details of "deoxys-normal"
    Then the response status is 200
    And they see the details of "deoxys-normal", number 386

  Scenario Outline: A name or number that cannot be a Pokémon is refused before PokeAPI is asked
    When a visitor opens the details of "<idOrName>"
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "idOrName"
    And PokeAPI received no request

    Examples:
      | idOrName | reason             |
      | Pikachu  | upper case letters |
      | pika chu | a space            |
      | pika_chu | an underscore      |
      | pika.chu | a dot              |

  Scenario: A name longer than 100 characters is refused
    When a visitor opens the details of a name that is 101 letters long
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "idOrName"
    And PokeAPI received no request

  Scenario: A Pokémon PokeAPI has never heard of is not found
    Given PokeAPI has never heard of "missingno"
    When a visitor opens the details of "missingno"
    Then the response is a problem with status 404 and title "Resource not found"

  Scenario: The visitor is told when PokeAPI is unavailable
    Given PokeAPI is failing with server errors
    When a visitor opens the details of "pikachu"
    Then the response is a problem with status 502 and title "Upstream service unavailable"
