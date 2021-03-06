/* global element, by */

'use strict';

var authentication = require('../../authentication/authentication');
var common = require('../../common/common');
var navigation = require('../../common/navigation');
var rolesCommon = require('../../common/roles_common.js');
var applications = require('../../applications/applications');
var users = require('../../admin/users');

var toggleRole = function(appOrEnv, app, user, role) {
  applications.goToApplicationDetailPage(app);
  navigation.go('applications', 'users');
  if (appOrEnv === 'app') {
    rolesCommon.editUserRole(user, role);
  } else {
    rolesCommon.editUserRoleForAnEnv(user, role);
  }
};
var addRole = toggleRole;
var removeRole = toggleRole;

var goToApplicationRoleManagementTab = function() {
  navigation.go('applications', 'users');
};

var goToApplicationGroupRoleManagementTab = function() {
  element(by.id('groups-tab')).element(by.tagName('a')).click();
};

var goToApplicationGroupRoleManagementTabForApp = function(app) {
  applications.goToApplicationDetailPage(app);
  navigation.go('applications', 'users');
  goToApplicationGroupRoleManagementTab();
};

describe('Security management on applications', function() {

  beforeEach(function() {
    common.before();

    // create user
    authentication.login('admin');
    users.navigationUsers();
    users.createUser(authentication.users.sauron);

    // create group
    users.navigationGroups();
    users.createGroup(users.groups.mordor);
  });

  afterEach(function() {
    authentication.logout();
  });

  it('Authenticated users should only view applications they are authorized to (with specific rights given to user)', function() {
    console.log('################# Authenticated users should only view applications they are authorized to - user');

    authentication.reLogin('applicationManager');
    applications.createApplication('Alien', 'Great Application');
    applications.createApplication('Alien_1', 'Great Application 1');
    applications.createApplication('Alien_2', 'Great Application 2');
    applications.createApplication('Alien_3', 'Great Application 3');

    addRole('app', 'Alien', 'sauron', rolesCommon.appRoles.appManager);
    addRole('env', 'Alien_2', 'sauron', rolesCommon.envRoles.deploymentManager);

    authentication.reLogin('sauron');
    navigation.go('main', 'applications');
    expect(element.all(by.repeater('application in searchResult.data.data')).count()).toEqual(2);
    expect(browser.isElementPresent(by.id('app_Alien'))).toBe(true);
    expect(browser.isElementPresent(by.id('app_Alien_2'))).toBe(true);
    applications.goToApplicationDetailPage('Alien');
    applications.goToApplicationDetailPage('Alien_2');

    authentication.reLogin('applicationManager');
    removeRole('env', 'Alien_2', 'sauron', rolesCommon.envRoles.deploymentManager);
    authentication.reLogin('sauron');
    navigation.go('main', 'applications');
    expect(element.all(by.repeater('application in searchResult.data.data')).count()).toEqual(2);
    expect(browser.isElementPresent(by.id('app_Alien'))).toBe(true);
  });

  it('Authenticated users should only view applications they are authorized to (with specific rights given to group)', function() {
    console.log('################# Authenticated users should only view applications they are authorized to - group');

    authentication.reLogin('applicationManager');
    applications.createApplication('Alien', 'Great Application');
    applications.createApplication('Alien_1', 'Great Application 1');
    applications.createApplication('Alien_2', 'Great Application 2');
    applications.createApplication('Alien_3', 'Great Application 3');

    goToApplicationGroupRoleManagementTabForApp('Alien');
    rolesCommon.editGroupRoleForAnApp(users.groups.mordor.name, rolesCommon.appRoles.appManager);
    goToApplicationGroupRoleManagementTabForApp('Alien_2');
    rolesCommon.editGroupRoleForAnEnv(users.groups.mordor.name, rolesCommon.envRoles.deploymentManager);
    // Roles are given to the group not the user --> do not have rights
    authentication.reLogin('sauron');
    navigation.go('main', 'applications');
    expect(element.all(by.repeater('application in searchResult.data.data')).count()).toEqual(0);

    // Add the user to the group
    authentication.reLogin('admin');
    users.navigationUsers();
    rolesCommon.addUserToGroup('sauron', users.groups.mordor.name);

    // Now he has the rights on the application
    authentication.reLogin('sauron');
    navigation.go('main', 'applications');
    expect(element.all(by.repeater('application in searchResult.data.data')).count()).toEqual(2);
    expect(browser.isElementPresent(by.id('app_Alien'))).toBe(true);
    expect(browser.isElementPresent(by.id('app_Alien_2'))).toBe(true);
    applications.goToApplicationDetailPage('Alien');
    applications.goToApplicationDetailPage('Alien_2');

    // Remove the user from the group
    authentication.reLogin('admin');
    users.navigationGroups();
    users.deleteGroup(users.groups.mordor.name);

    // Now he has lost the rights
    authentication.reLogin('sauron');
    navigation.go('main', 'applications');
    expect(element.all(by.repeater('application in searchResult.data.data')).count()).toEqual(0);
  });

  it('should be able to manage users authorizations on the application', function() {
    console.log('################# should be able to manage users authorizations on the application');

    // create an application
    authentication.reLogin('applicationManager');
    navigation.go('main', 'applications');
    applications.createApplication('Alien', 'Great Application');

    // Go to the app details page / user roles management
    navigation.go('applications', 'users');

    // give application_user role to 'user' user
    rolesCommon.editUserRoleForAnEnv('sauron', rolesCommon.envRoles.envUser);
    rolesCommon.assertUserHasRolesForAnEnv('sauron', rolesCommon.envRoles.envUser);

    // give application_devops role to applicationManager user
    rolesCommon.editUserRole('sauron', rolesCommon.appRoles.appDevops);
    rolesCommon.assertUserHasRoles('sauron', rolesCommon.appRoles.appDevops);
  });

  it('should be able to manage group authorizations on the application', function() {
    console.log('################# should be able to manage group authorizations on the application');

    // create an application
    authentication.reLogin('applicationManager');
    navigation.go('main', 'applications');
    applications.createApplication('Alien', 'Great Application');

    // Go to the app details page / user roles management
    applications.goToApplicationDetailPage('Alien');
    goToApplicationRoleManagementTab();
    goToApplicationGroupRoleManagementTab();

    // give application_user role to 'user' user
    rolesCommon.editGroupRoleForAnEnv('mordor', rolesCommon.envRoles.envUser);
    rolesCommon.assertGroupHasRoles('env', 'mordor', rolesCommon.envRoles.envUser);

    // give application_devops role to applicationManager user
    rolesCommon.editGroupRole('mordor', rolesCommon.appRoles.appDevops);
    rolesCommon.assertGroupHasRoles('app', 'mordor', rolesCommon.appRoles.appDevops);
  });

  it('Authenticated users even without any roles should see applications with ALL_USERS group rights on it', function() {
    console.log('################# Authenticated users even without any roles should see applications with ALL_USERS group rights on it');

    // create 4 applications
    authentication.reLogin('applicationManager');
    applications.createApplication('Application', 'Great great app...');
    applications.createApplication('Alien_1', 'Great Application 1');
    applications.createApplication('Alien_2', 'Great Application 2');

    // Go to the app details page / user roles management
    applications.goToApplicationDetailPage('Alien_2');
    goToApplicationRoleManagementTab();
    goToApplicationGroupRoleManagementTab();

    // give appUser role to group ALL_USERS
    rolesCommon.editGroupRoleForAnEnv('ALL_USERS', rolesCommon.envRoles.envUser);
    rolesCommon.assertGroupHasRoles('env', 'ALL_USERS', rolesCommon.envRoles.envUser);

    // Log as sauron who has no roles on application Alien_2
    authentication.reLogin('sauron');
    applications.goToApplicationListPage();
    // only 1 app (Alien_2) is visible for sauron user
    var applicationsList = element.all(by.repeater('application in searchResult.data.data'));
    expect(applicationsList.count()).toEqual(1);
    expect(applicationsList.first().getText()).toContain('Alien_2');

    // log as admin and give right appManager to ALL_USERS
    authentication.reLogin('admin');
    applications.goToApplicationDetailPage('Application');
    goToApplicationRoleManagementTab();
    goToApplicationGroupRoleManagementTab();

    // give appDevops role to group ALL_USERS
    rolesCommon.editGroupRole('ALL_USERS', rolesCommon.appRoles.appManager);
    rolesCommon.assertGroupHasRoles('app', 'ALL_USERS', rolesCommon.appRoles.appManager);

    // now any user as sauron should have at least 2 applications in the list
    authentication.reLogin('sauron');
    applications.goToApplicationListPage();
    expect(applicationsList.count()).toEqual(2);
  });

});
