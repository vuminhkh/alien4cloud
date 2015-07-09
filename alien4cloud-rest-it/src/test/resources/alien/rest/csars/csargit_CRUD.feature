Feature: Csargit crud feature

  Background: 
    Given I am authenticated with "ADMIN" role

  Scenario: Create a new csargit
    Given I have a csargit with the url "https://github.com/alien4cloud/samples" with username "admin" and password "admin"
    And I add locations to the csar
      | branchId | subPath |
      | master   |         |
    When I create a csargit
    Then I should receive a RestResponse with no error
    And I have a csargit created with url "https://github.com/alien4cloud/samples"

  Scenario: Create a new csargit which already exists
    Given I have a csargit with the url "https://github.com/alien4cloud/samples" with username "admin" and password "admin"
    And I add locations to the csar
      | branchId | subPath |
      | master   |         |
    When I create a csargit
    Then I should receive a RestResponse with an error code 501
    And I have no csargit created with url "https://github.com/alien4cloud/samples"

  Scenario: Create a new csargit with empty data
    Given I have a csargit with the url "" with username "" and password ""
    And I add locations to the csar
      | branchId | subPath |
      |          |         |
    When I create a csargit
    Then I should receive a RestResponse with an error code 501

  Scenario: Delete a csargit with url
    Given I have a csargit with the url "https://github.com/alien4cloud/tosca-normative-types" with username "admin" and password "admin"
    And I add locations to the csar
      | branchId | subPath |
      | master   |         |
    When I create a csargit
    And I delete a csargit with url "https://github.com/alien4cloud/tosca-normative-types"
    Then I should receive a RestResponse with no error

  Scenario: Delete a csargit with a wrong url
    Given I have a csargit with the url "https://github.com/alien4cloud/tosca-normative-types" with username "admin" and password "admin"
    And I add locations to the csar
      | branchId | subPath |
      | master   |         |
    When I create a csargit
    And I delete a csargit with url "https://github.com/tosca-normative-types"
    Then I should receive a RestResponse with an error code 504

  Scenario: Import a set of csargit by id
    Given I have a csargit with the url "https://github.com/alien4cloud/tosca-normative-types" with username "admin" and password "admin"
    And I add locations to the csar
      | branchId | subPath |
      | master   |         |
    When I create a csargit
    Given I have a csargit with the url "https://github.com/alien4cloud/samples" with username "admin" and password "admin"
    And I add locations to the csar
      | branchId | subPath |
      | master   |         |
    When I create a csargit
    When I trigger the import of the csars
    Then I should receive a RestResponse with no error


  Scenario: Import a csargit with a wrong url
    Given I have a csargit with the url "https://github.com/a" with username "admin" and password "admin"
    And I add locations to the csar
      | branchId | subPath |
      | master   |         |
    When I create a csargit
    And I trigger the import of a csar with url "https://github.com/a"
    Then I should receive a RestResponse with an error code 500

  Scenario: Delete a csargit
    Given I have a csargit with the url "https://github.com/alien4cloud/tosca-normative-types" with username "admin" and password "admin"
    And I add locations to the csar
      | branchId | subPath |
      | master   |         |
    When I create a csargit
    And I delete a csargit with id
    Then I should receive a RestResponse with no error
