package org.alfresco.rest.tags;

import org.alfresco.dataprep.CMISUtil;
import org.alfresco.rest.RestTest;
import org.alfresco.rest.exception.JsonToModelConversionException;
import org.alfresco.rest.model.RestTagModelsCollection;
import org.alfresco.utility.Utility;
import org.alfresco.utility.constants.UserRole;
import org.alfresco.utility.data.DataUser.ListUserWithRoles;
import org.alfresco.utility.data.RandomData;
import org.alfresco.utility.model.*;
import org.alfresco.utility.report.Bug;
import org.alfresco.utility.testrail.ExecutionType;
import org.alfresco.utility.testrail.annotation.TestRail;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test(groups = { TestGroup.REST_API, TestGroup.TAGS, TestGroup.SANITY })
public class GetTagsSanityTests extends RestTest
{
    private UserModel adminUserModel;
    private UserModel userModel;
    private SiteModel siteModel;
    private ListUserWithRoles usersWithRoles;
    
    private String tagValue;
    private String tagValue2;
    private FileModel document;
    
    @BeforeClass(alwaysRun=true)
    public void dataPreparation() throws Exception
    {
        adminUserModel = dataUser.getAdminUser();
        restClient.authenticateUser(adminUserModel);
        siteModel = dataSite.usingUser(adminUserModel).createPublicRandomSite();
        usersWithRoles = dataUser.addUsersWithRolesToSite(siteModel, UserRole.SiteManager, UserRole.SiteCollaborator, UserRole.SiteConsumer, UserRole.SiteContributor);
        document = dataContent.usingUser(adminUserModel).usingSite(siteModel).createContent(CMISUtil.DocumentType.TEXT_PLAIN);

        tagValue = RandomData.getRandomName("tag");
        tagValue2 = RandomData.getRandomName("tag");
        restClient.withCoreAPI().usingResource(document).addTags(tagValue, tagValue2);
        
        Utility.waitToLoopTime(60);
    }
    
    
    @TestRail(section = { TestGroup.REST_API, TestGroup.TAGS }, executionType = ExecutionType.SANITY, description = "Verify user with Manager role gets tags using REST API and status code is OK (200)")
    public void getTagsWithManagerRole() throws JsonToModelConversionException, Exception
    {
        restClient.authenticateUser(usersWithRoles.getOneUserWithRole(UserRole.SiteManager));
        RestTagModelsCollection returnedCollection = restClient.withParams("maxItems=500").withCoreAPI().getTags();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        returnedCollection.assertThat().entriesListIsNotEmpty()
            .and().entriesListContains("tag", tagValue.toLowerCase())
            .and().entriesListContains("tag", tagValue2.toLowerCase());    
    }
    
    @TestRail(section = { TestGroup.REST_API, TestGroup.TAGS }, executionType = ExecutionType.SANITY, description = "Verify user with Collaborator role gets tags using REST API and status code is OK (200)")
    public void getTagsWithCollaboratorRole() throws JsonToModelConversionException, Exception
    {
        restClient.authenticateUser(usersWithRoles.getOneUserWithRole(UserRole.SiteCollaborator));
        RestTagModelsCollection returnedCollection = restClient.withParams("maxItems=500").withCoreAPI().getTags();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        returnedCollection.assertThat().entriesListIsNotEmpty()
            .and().entriesListContains("tag", tagValue.toLowerCase())
            .and().entriesListContains("tag", tagValue2.toLowerCase());
    }
    
    @TestRail(section = { TestGroup.REST_API, TestGroup.TAGS }, executionType = ExecutionType.SANITY, description = "Verify user with Contributor role gets tags using REST API and status code is OK (200)")
    public void getTagsWithContributorRole() throws JsonToModelConversionException, Exception
    {
        restClient.authenticateUser(usersWithRoles.getOneUserWithRole(UserRole.SiteContributor));
        RestTagModelsCollection returnedCollection = restClient.withParams("maxItems=500").withCoreAPI().getTags();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        returnedCollection.assertThat().entriesListIsNotEmpty()
            .and().entriesListContains("tag", tagValue.toLowerCase())
            .and().entriesListContains("tag", tagValue2.toLowerCase());    
    }
    
    @TestRail(section = { TestGroup.REST_API, TestGroup.TAGS }, executionType = ExecutionType.SANITY, description = "Verify user with Consumer role gets tags using REST API and status code is OK (200)")
    public void getTagsWithConsumerRole() throws JsonToModelConversionException, Exception
    {
        restClient.authenticateUser(usersWithRoles.getOneUserWithRole(UserRole.SiteConsumer));
        RestTagModelsCollection returnedCollection = restClient.withParams("maxItems=500").withCoreAPI().getTags();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        returnedCollection.assertThat().entriesListIsNotEmpty()
            .and().entriesListContains("tag", tagValue.toLowerCase())
            .and().entriesListContains("tag", tagValue2.toLowerCase());    
    }
    
    @TestRail(section = { TestGroup.REST_API, TestGroup.TAGS }, executionType = ExecutionType.SANITY, description = "Verify Admin user gets tags using REST API and status code is OK (200)")
    public void getTagsWithAdminUser() throws JsonToModelConversionException, Exception
    {
        restClient.authenticateUser(adminUserModel);
        RestTagModelsCollection returnedCollection = restClient.withParams("maxItems=500").withCoreAPI().getTags();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        returnedCollection.assertThat().entriesListIsNotEmpty()
            .and().entriesListContains("tag", tagValue.toLowerCase())
            .and().entriesListContains("tag", tagValue2.toLowerCase());    
    }
    
    @TestRail(section = { TestGroup.REST_API, TestGroup.TAGS }, executionType = ExecutionType.SANITY, description = "Failed authentication get tags call returns status code 401 with Manager role")
    @Bug(id = "MNT-16904")
    public void failedAuthenticationReturnsUnauthorizedStatus() throws JsonToModelConversionException, Exception
    {
        restClient.authenticateUser(usersWithRoles.getOneUserWithRole(UserRole.SiteManager));
        userModel = dataUser.createRandomTestUser();
        userModel.setPassword("user wrong password");
        dataUser.addUserToSite(userModel, siteModel, UserRole.SiteManager);
        restClient.authenticateUser(userModel);
        restClient.withCoreAPI().getTags();
        restClient.assertStatusCodeIs(HttpStatus.UNAUTHORIZED).assertLastException().hasName(StatusModel.UNAUTHORIZED);
    }
}
