Feature: Accounts and signing in
  Browsing the Pokédex is public; the collection belongs to a signed-in trainer.
  The demo trainer comes with the application; others can register.

  Scenario: The demo trainer logs in and is recognised
    When I log in as "demo" with the password "demo12345"
    Then I receive a token that expires later
    And the application knows me as "demo" with the role "USER"

  Scenario Outline: Wrong credentials are refused without saying which part was wrong
    When I log in as "<username>" with the password "<password>"
    Then the response is a problem with status 401 and title "Authentication failed"

    Examples:
      | username | password    |
      | demo     | not-my-pass |
      | nobody   | demo12345   |

  Scenario: A new trainer registers and can log in
    When I register as "ash.ketchum" with the password "pikachu-1234"
    Then the account "ash.ketchum" exists
    When I log in as "ash.ketchum" with the password "pikachu-1234"
    Then I receive a token that expires later
    And the application knows me as "ash.ketchum" with the role "USER"

  Scenario: A username that is taken cannot be registered again, whatever its case
    Given I register as "misty" with the password "starmie-1234"
    When I register as "MISTY" with the password "another-pass-1"
    Then the response is a problem with status 409 and title "Conflict"

  Scenario Outline: Registration rules are enforced
    When I register as "<username>" with the password "<password>"
    Then the response is a problem with status 400 and title "Invalid request"
    And the problem names the field "<field>"

    Examples:
      | username  | password    | field    |
      | ab        | long-enough | username |
      | has space | long-enough | username |
      | brock     | short       | password |

  Scenario: The collection is closed to anonymous visitors
    Given I am not signed in
    When I open my collection
    Then the response is a problem with status 401 and title "Unauthorized"

  Scenario: A signed-in trainer sees an empty collection to begin with
    Given I am signed in as the demo user
    When I open my collection
    Then the response status is 200
