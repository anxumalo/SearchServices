package org.alfresco.rest.people;

import org.alfresco.rest.RestTest;
import org.alfresco.rest.body.SiteMembershipRequest;
import org.alfresco.rest.exception.JsonToModelConversionException;
import org.alfresco.rest.requests.RestPeopleApi;
import org.alfresco.utility.constants.UserRole;
import org.alfresco.utility.data.DataSite;
import org.alfresco.utility.data.DataUser;
import org.alfresco.utility.data.DataUser.ListUserWithRoles;
import org.alfresco.utility.exception.DataPreparationException;
import org.alfresco.utility.model.SiteModel;
import org.alfresco.utility.model.UserModel;
import org.alfresco.utility.report.Bug;
import org.alfresco.utility.testrail.ExecutionType;
import org.alfresco.utility.testrail.annotation.TestRail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test(groups = { "rest-api", "people", "sanity" })
public class AddSiteMembershipRequestSanityTests extends RestTest
{
    @Autowired
    RestPeopleApi peopleApi;

    @Autowired
    DataUser dataUser;

    @Autowired
    DataSite dataSite;

    private SiteModel siteModel;

    private ListUserWithRoles usersWithRoles;

    private UserModel adminUser;

    @BeforeClass(alwaysRun=true)
    public void dataPreparation() throws DataPreparationException
    {
        adminUser = dataUser.getAdminUser();
        siteModel = dataSite.usingUser(adminUser).createPublicRandomSite();
        usersWithRoles = dataUser.addUsersWithRolesToSite(siteModel,UserRole.SiteManager, UserRole.SiteCollaborator, UserRole.SiteConsumer, UserRole.SiteContributor);

        peopleApi.useRestClient(restClient);
    }

    @TestRail(section = { "rest-api", "people" }, 
                executionType = ExecutionType.SANITY, description = "Verify site manager is able to create new site membership request")    
    @Bug(id="MNT-16557")    
    public void siteManagerCanCreateSiteMembershipRequest() throws JsonToModelConversionException, Exception
    {
        UserModel newMember = dataUser.createRandomTestUser();
        SiteMembershipRequest siteMembership = new SiteMembershipRequest("Please accept me", siteModel.getId(), "New request");

        restClient.authenticateUser(usersWithRoles.getOneUserWithRole(UserRole.SiteManager));
        peopleApi.addSiteMembershipRequest(newMember, siteMembership);
        peopleApi.usingRestWrapper()
            .assertStatusCodeIs(HttpStatus.CREATED);
    }
    
    @TestRail(section = { "rest-api", "people" }, 
                executionType = ExecutionType.SANITY, description = "Verify site collaborator is able to create new site membership request")
    @Bug(id = "MNT-16557")
    public void siteCollaboatorCanCreateSiteMembershipRequest() throws JsonToModelConversionException, Exception
    {
        UserModel newMember = dataUser.createRandomTestUser();
        SiteMembershipRequest siteMembership = new SiteMembershipRequest("Please accept me", siteModel.getId(), "New request");

        restClient.authenticateUser(usersWithRoles.getOneUserWithRole(UserRole.SiteCollaborator));
        peopleApi.addSiteMembershipRequest(newMember, siteMembership);
        peopleApi.usingRestWrapper()
            .assertStatusCodeIs(HttpStatus.CREATED);
}

}