Feature: US04 - Personalise and manage my copy of a Pokémon
  As a signed-in trainer,
  I want to give my copy of a Pokémon a local name, region, notes and tags, or delete it,
  so that my collection says what I want, and two people editing at once never overwrite each other silently.

  Background:
    Given PokeAPI knows the Pokémon "pikachu"
    And I am signed in as the demo user
    And I have synced "pikachu"

  # --- update: the happy path ---------------------------------------------------------------------------------

  Scenario: I give my Pokémon a local name, region, notes and tags
    When I save these changes to Pokémon number 25:
      | field     | value                       |
      | localName | Sparky                      |
      | region    | Kanto                       |
      | notes     | Found it near Viridian      |
      | tags      | Electric, starter, electric |
    Then the response status is 200
    And its version went up by one
    And my copy of Pokémon number 25 now has:
      | field     | value                  |
      | localName | Sparky                 |
      | region    | Kanto                  |
      | notes     | Found it near Viridian |
      | tags      | electric, starter      |
    And it is still the Pokémon "pikachu", number 25

  Scenario: I can keep editing with the version of my last save
    Given I have personalised Pokémon number 25 with:
      | field     | value  |
      | localName | Sparky |
    When I save these changes to Pokémon number 25:
      | field     | value |
      | localName | Zappy |
    Then the response status is 200
    And its version went up by one
    And my copy of Pokémon number 25 now has:
      | field     | value |
      | localName | Zappy |

  Scenario: Whatever I leave out of a save is cleared
    Given I have personalised Pokémon number 25 with:
      | field     | value   |
      | localName | Sparky  |
      | tags      | starter |
    When I save these changes to Pokémon number 25:
      | field  | value |
      | region | Kanto |
    Then the response status is 200
    And my copy of Pokémon number 25 has no local name
    And my copy of Pokémon number 25 now has:
      | field  | value |
      | region | Kanto |
      | tags   |       |

  # --- update: what is refused ----------------------------------------------------------------------------------

  Scenario Outline: A change that breaks a rule is refused and nothing is saved
    When I send this update for Pokémon number 25:
      """
      <body>
      """
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "<field>"
    And my copy of Pokémon number 25 has not been changed

    Examples:
      | rule                           | body                                                                                                                                         | field     |
      | a blank local name             | {"localName": "   ", "version": {version}}                                                                                                   | localName |
      | a tag that is not kebab-case   | {"tags": ["Not Kebab"], "version": {version}}                                                                                                | tags[0]   |
      | more than ten tags             | {"tags": ["a","b","c","d","e","f","g","h","i","j","k"], "version": {version}}                                                                | tags      |
      | a local name over 100 chars    | {"localName": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "version": {version}} | localName |
      | a field I may not change       | {"name": "raichu", "version": {version}}                                                                                                     | name      |
      | another read-only field        | {"stats": [], "version": {version}}                                                                                                          | stats     |
      | no version                     | {"localName": "Sparky"}                                                                                                                      | version   |
      | a negative version             | {"localName": "Sparky", "version": -1}                                                                                                       | version   |
      | a version that is not a number | {"localName": "Sparky", "version": "abc"}                                                                                                    | version   |
      | a body that is not JSON        | {not json                                                                                                                                    | request   |

  Scenario Outline: A Pokémon number that cannot exist is refused
    When I save these changes to Pokémon number <id>:
      | field     | value  |
      | localName | Sparky |
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "id"

    Examples:
      | id  |
      | 0   |
      | -3  |
      | abc |

  Scenario: A Pokémon that is not in my collection cannot be updated
    When I save these changes to Pokémon number 26:
      | field     | value  |
      | localName | Sparky |
    Then the response is a problem with status 404 and title "Resource not found"

  Scenario: An update based on an outdated version is rejected, not merged
    Given I opened Pokémon number 25 to edit it
    And someone else saved a change to Pokémon number 25 in the meantime
    When I save these changes to Pokémon number 25:
      | field     | value      |
      | localName | My version |
    Then the response is a problem with status 409 and title "Conflict"
    And the problem explains "reload and try again"
    And my copy of Pokémon number 25 keeps the other person's local name "Their change"

  Scenario: An anonymous visitor cannot update
    Given I am not signed in
    When I save these changes to Pokémon number 25:
      | field     | value  |
      | localName | Sparky |
    Then the response is a problem with status 401 and title "Unauthorized"

  # --- delete ---------------------------------------------------------------------------------------------------

  Scenario: Deleting removes my copy
    When I delete Pokémon number 25
    Then the response status is 204
    And Pokémon number 25 is no longer in my collection
    And my collection holds 0 Pokémon

  Scenario: Deleting the same Pokémon twice is a not-found the second time
    Given I delete Pokémon number 25
    When I delete Pokémon number 25
    Then the response is a problem with status 404 and title "Resource not found"

  Scenario: A deleted Pokémon can be synced again as a fresh copy
    Given I have personalised Pokémon number 25 with:
      | field     | value  |
      | localName | Sparky |
    And I delete Pokémon number 25
    When I sync "pikachu" from PokeAPI
    Then the response status is 201
    And it has no local name, region, notes or tags yet

  Scenario: A Pokémon that is not in my collection cannot be deleted
    When I delete Pokémon number 26
    Then the response is a problem with status 404 and title "Resource not found"

  Scenario: A Pokémon number that cannot exist cannot be deleted
    When I delete Pokémon number 0
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "id"

  Scenario: An anonymous visitor cannot delete
    Given I am not signed in
    When I delete Pokémon number 25
    Then the response is a problem with status 401 and title "Unauthorized"
