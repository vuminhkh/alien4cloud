Feature: Create cloud availability zones

  Background:
    Given I am authenticated with "ADMIN" role
    And I upload a plugin
    And I create a cloud with name "Mount doom cloud" and plugin id "alien4cloud-mock-paas-provider:1.0" and bean name "mock-paas-provider"
    And I enable the cloud "Mount doom cloud"
    And I upload the archive "tosca-normative-types"
    And I upload the archive "alien-base-types"
    And I upload the archive "alien-extended-storage-types"
    And There are these users in the system
      | sangoku |
    And I add a role "APPLICATIONS_MANAGER" to user "sangoku"
    And I add a role "CLOUD_DEPLOYER" to user "sangoku" on the resource type "CLOUD" named "Mount doom cloud"
    And I have already created a cloud image with name "Ubuntu Trusty", architecture "x86_64", type "linux", distribution "Ubuntu" and version "14.04.1"
    And I add the cloud image "Ubuntu Trusty" to the cloud "Mount doom cloud" and match it to paaS image "UBUNTU"
    And I add the flavor with name "medium", number of CPUs 4, disk size 64 and memory size 4096 to the cloud "Mount doom cloud" and match it to paaS flavor "3"
    And I add the availability zone with id "paris" and description "Data-center at Paris" to the cloud "Mount doom cloud"
    And I add the availability zone with id "toulouse" and description "Data-center at Toulouse" to the cloud "Mount doom cloud"
    And I match the availability zone with name "paris" of the cloud "Mount doom cloud" to the PaaS resource "paris-zone"
    And I match the availability zone with name "toulouse" of the cloud "Mount doom cloud" to the PaaS resource "toulouse-zone"
    And I am authenticated with user named "sangoku"
    And I have an application "ALIEN" with a topology containing a nodeTemplate "Compute1" related to "tosca.nodes.Compute:1.0.0.wd03-SNAPSHOT"
    And I add a node template "Compute2" related to the "tosca.nodes.Compute:1.0.0.wd03-SNAPSHOT" node type
    And I add a group with name "HA_group" whose members are "Compute1,Compute2"
    And I update the node template "Compute1"'s property "os_arch" to "x86_64"
    And I update the node template "Compute1"'s property "os_type" to "linux"
    And I update the node template "Compute2"'s property "os_arch" to "x86_64"
    And I update the node template "Compute2"'s property "os_type" to "linux"
    And I assign the cloud with name "Mount doom cloud" for the application

  Scenario: Match a topology for avz, no filter
    When I match for resources for my application on the cloud
    Then I should receive a match result with 2 availability zones for the group "HA_group":
      | paris    | Data-center at Paris    |
      | toulouse | Data-center at Toulouse |

  Scenario: Should be able to add and remove a avz
    When I match for resources for my application on the cloud
    Then I should receive a match result with 2 availability zones for the group "HA_group":
      | paris    | Data-center at Paris    |
      | toulouse | Data-center at Toulouse |
    And I am authenticated with "ADMIN" role
    And I add the availability zone with id "grenoble" and description "Data-center at Grenoble" to the cloud "Mount doom cloud"
    And I match the availability zone with name "grenoble" of the cloud "Mount doom cloud" to the PaaS resource "grenoble-zone"
    And I match for resources for my application on the cloud
    Then I should receive a match result with 3 availability zones for the group "HA_group":
      | paris    | Data-center at Paris    |
      | toulouse | Data-center at Toulouse |
      | grenoble | Data-center at Grenoble |
    Then I remove the availability zone with name "grenoble" from the cloud "Mount doom cloud"
    When I match for resources for my application on the cloud
    Then I should receive a match result with 2 availability zones for the group "HA_group":
      | paris    | Data-center at Paris    |
      | toulouse | Data-center at Toulouse |

  Scenario: Validate HA policy for a topology
    Given I check for the deployable status of the topology on the default environment
    Then I should receive a RestResponse with no error
    And the topology should be deployable
    And The topology should have no warnings

    # Add storages
    Given I add a node template "Storage1" related to the "alien.nodes.ConfigurableBlockStorage:1.0-SNAPSHOT" node type
    And I add a relationship of type "tosca.relationships.AttachTo" defined in archive "tosca-normative-types" version "1.0.0.wd03-SNAPSHOT" with source "Storage1" and target "Compute1" for requirement "attachment" of type "tosca.capabilities.Attachment" and target capability "attach"
    When I add a node template "Storage2" related to the "alien.nodes.ConfigurableBlockStorage:1.0-SNAPSHOT" node type
    And I add a relationship of type "tosca.relationships.AttachTo" defined in archive "tosca-normative-types" version "1.0.0.wd03-SNAPSHOT" with source "Storage2" and target "Compute2" for requirement "attachment" of type "tosca.capabilities.Attachment" and target capability "attach"
    When I check for the deployable status of the topology on the default environment
    Then I should receive a RestResponse with no error
    And the topology should be deployable
    And The topology should have no warnings

    # Existing storage
    Given I update the node template "Storage1"'s property "volume_id" to "paris-zone/id1"
    And I update the node template "Storage2"'s property "volume_id" to "toulouse-zone/id2"
    When I check for the deployable status of the topology on the default environment
    Then The topology should have no warnings

    # Storage's zone override allocation algorithm which led to invalid allocation
    Given I update the node template "Storage2"'s property "volume_id" to "paris-zone/id2"
    And I check for the deployable status of the topology on the default environment
    Then I should receive a RestResponse with no error
    And the topology should be deployable
    And The topology should have following warnings:
      | HA_group |  | ZONES_NOT_DISTRIBUTED_EQUALLY |

    # Put back as before, should remove the warning
    Given I update the node template "Storage2"'s property "volume_id" to "toulouse-zone/id2"
    When I check for the deployable status of the topology on the default environment
    Then I should receive a RestResponse with no error
    And the topology should be deployable
    And The topology should have no warnings

    # Remove a zone from the deployment set up should trigger an warning that the allocated zone is not present
    Given I de-select the availability zone with name "toulouse" for my group "HA_group"
    When I check for the deployable status of the topology on the default environment
    And The topology should have following warnings:
      | HA_group | Compute2 | NODE_HAS_ALLOCATED_ZONE_NOT_IN_DEPLOYMENT_SETUP |

    # Re-select the removed zone should remove the warning
    Given I select the availability zone with name "toulouse" for my group "HA_group"
    When I check for the deployable status of the topology on the default environment
    Then The topology should have no warnings

    # Select a zone which is not in volumes' zone
    Given I am authenticated with "ADMIN" role
    And I add the availability zone with id "grenoble" and description "Data-center at Grenoble" to the cloud "Mount doom cloud"
    And I match the availability zone with name "grenoble" of the cloud "Mount doom cloud" to the PaaS resource "grenoble-zone"
    And I am authenticated with user named "sangoku"
    And I select the availability zone with name "grenoble" for my group "HA_group"
    When I check for the deployable status of the topology on the default environment
    Then The topology should have no warnings

    # De-select every zones from deployment set up should trigger an warning about configuration error
    Given I de-select the availability zone with name "toulouse" for my group "HA_group"
    And I de-select the availability zone with name "paris" for my group "HA_group"
    And I de-select the availability zone with name "grenoble" for my group "HA_group"
    When I check for the deployable status of the topology on the default environment
    Then The topology should have following warnings:
      | HA_group |  | CONFIGURATION_ERROR |

    # Allocated zone not present in deployment setup
    Given I select the availability zone with name "grenoble" for my group "HA_group"
    When I check for the deployable status of the topology on the default environment
    Then The topology should have following warnings:
      | HA_group | Compute1 | NODE_HAS_ALLOCATED_ZONE_NOT_IN_DEPLOYMENT_SETUP |
      | HA_group | Compute2 | NODE_HAS_ALLOCATED_ZONE_NOT_IN_DEPLOYMENT_SETUP |

    # Volume with ids not containing availability zone should not trigger any error
    Given I select the availability zone with name "paris" for my group "HA_group"
    And I select the availability zone with name "toulouse" for my group "HA_group"
    And I de-select the availability zone with name "grenoble" for my group "HA_group"
    And I update the node template "Storage2"'s property "volume_id" to "id2"
    When I check for the deployable status of the topology on the default environment
    Then The topology should have no warnings

    Given I update the node template "Storage1"'s property "volume_id" to "id1"
    When I check for the deployable status of the topology on the default environment
    Then The topology should have no warnings
