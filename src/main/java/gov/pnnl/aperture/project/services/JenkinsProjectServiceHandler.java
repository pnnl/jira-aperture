package gov.pnnl.aperture.project.services;

import com.atlassian.applinks.api.ApplicationLinkRequest;
import com.atlassian.applinks.api.ApplicationLinkRequestFactory;
import com.atlassian.applinks.api.CredentialsRequiredException;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleActors;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.ResponseException;
import com.atlassian.streams.api.common.uri.UriBuilder;
import com.atlassian.velocity.VelocityManager;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Project service handler for creating and configuring Jenkins projects as part of Aperture.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public class JenkinsProjectServiceHandler extends AbstractRestfulProjectServiceHandler {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(JenkinsProjectServiceHandler.class);

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param aperture reference to the current aperture service.
     * @param settings reference the current implementation of the aperture settings service.
     * @throws IllegalArgumentException if either aperture or settings parameters are <code>null</code>.
     */
    public JenkinsProjectServiceHandler(final Aperture aperture, final ApertureSettings settings) {

        super(aperture, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApertureSettings.ProjectService getServiceType() {

        return ApertureSettings.ProjectService.JENKINS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isServiceAvailable(final String projectKey, final ApplicationLinkRequestFactory factory) throws ResponseException, CredentialsRequiredException {

        return getProjectData(factory, projectKey) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void rollbackService(final String projectKey, final ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, CredentialsRequiredException {

        LOG.info(String.format("Rolling back Jenkins service for issue [%s]", projectKey));
        final String serviceURI = String.format("/job/%s/doDelete", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, serviceURI);
        request.addHeader("Content-Type", "text/xml");
        final String response = request.execute();
        LOG.debug(String.format("rollbackService() => [%s]", response));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createService(final MutableIssue issue, final ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        LOG.info(String.format("Creating Jenkins service for issue [%s]", issue.getKey()));
        final String basePath = "/gov/pnnl/aperture/xmlrpc/jenkins/";
        final ApertureSettings settings = getApertureSettings();
        final Map<String, Object> context = new HashMap<>();
        final VelocityManager velocityManager = ComponentAccessor.getVelocityManager();
        final ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
        final String baseUrl = applicationProperties.getString(APKeys.JIRA_BASEURL);
        final String webworkEncoding = applicationProperties.getString(APKeys.JIRA_WEBWORK_ENCODING);
        context.put("projectLead", issue.getReporter());
        context.put("projectMembers", settings.getProjectMembersFor(issue));
        context.put("projectDescription", issue.getDescription());
        context.put("projectName", issue.getSummary());

        final String projectKey = settings.getProjectKeyFor(issue);
        final String serviceURI = String.format("/createItem?name=%s", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, serviceURI);
        request.addHeader("Content-Type", "text/xml");
        request.setRequestBody(velocityManager.getEncodedBody(basePath, "folder.config.vm.xml", baseUrl, webworkEncoding, context));
        final String response = request.execute();
        LOG.debug(String.format("createService(%s) => [%s]", projectKey, response));

        final JsonNode folderData = getProjectData(factory, projectKey);
        addServiceLinkTo(issue, "Jenkins Job Folder", folderData.get("url").asText());
        addComponentTo(issue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyGroups(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<Group> groupList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final Map<String, List<String>> requestParameters = new HashMap<>();
        final String jenkinsRole = resolveRole(role);
        if (StringUtils.hasText(jenkinsRole)) {
            for (final Group projectGroup : groupList) {
                requestParameters.clear();
                requestParameters.put("name", Collections.singletonList(projectGroup.getName()));
                final String paramString = UriBuilder.joinParameters(requestParameters);
                final String restURI;
                switch (mode) {
                    case ADD:
                    case REPLACE:
                        restURI = String.format("/job/%s/groups/%s/addMember?%s", projectKey, jenkinsRole, paramString);
                        break;
                    case REMOVE:
                        restURI = String.format("/job/%s/groups/%s/removeMember?%s", projectKey, jenkinsRole, paramString);
                        break;
                    default:
                        continue;
                }
                final ApplicationLinkRequest linkRequest = factory.createRequest(Request.MethodType.POST, restURI);
                linkRequest.addHeader("Content-Type", "application/json");
                final String permissionResponse = linkRequest.execute();
                LOG.debug(String.format("modifyGroups(%s, %s)", projectGroup.getName(), permissionResponse));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyUsers(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<ApplicationUser> userList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final Map<String, List<String>> requestParameters = new HashMap<>();
        final String jenkinsRole = resolveRole(role);
        if (StringUtils.hasText(jenkinsRole)) {
            for (final ApplicationUser projectMember : userList) {
                requestParameters.clear();
                requestParameters.put("name", Collections.singletonList(projectMember.getName()));
                final String paramString = UriBuilder.joinParameters(requestParameters);
                final String restURI;
                switch (mode) {
                    case ADD:
                    case REPLACE:
                        restURI = String.format("/job/%s/groups/%s/addMember?%s", projectKey, jenkinsRole, paramString);
                        break;
                    case REMOVE:
                        restURI = String.format("/job/%s/groups/%s/removeMember?%s", projectKey, jenkinsRole, paramString);
                        break;
                    default:
                        continue;
                }
                final ApplicationLinkRequest linkRequest = factory.createRequest(Request.MethodType.POST, restURI);
                linkRequest.addHeader("Content-Type", "application/json");
                final String permissionResponse = linkRequest.execute();
                LOG.debug(String.format("modifyUsers(%s, %s)", projectMember.getName(), permissionResponse));
            }
        }
    }

    protected JsonNode getProjectData(final ApplicationLinkRequestFactory factory, final String projectKey) throws ResponseException, CredentialsRequiredException {

        final String serviceURI = String.format("/job/%s/api/json", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, serviceURI);
        return request.execute(new JSONApplicationLinkResponder(true));
    }

    protected JsonNode getProjectAdminData(final ApplicationLinkRequestFactory factory, final String projectKey) throws ResponseException, CredentialsRequiredException {

        final String serviceURI = String.format("/job/%s/groups/Local Admin/api/json", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, serviceURI);
        return request.execute(new JSONApplicationLinkResponder());
    }

    protected JsonNode getProjectDevelopersData(final ApplicationLinkRequestFactory factory, final String projectKey) throws ResponseException, CredentialsRequiredException {

        final String serviceURI = String.format("/job/%s/groups/Developers/api/json", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, serviceURI);
        return request.execute(new JSONApplicationLinkResponder());
    }

    public Collection<ApplicationUser> getProjectMembers(final Project project, final String roleName) {

        final ProjectRoleManager manager = ComponentAccessor.getComponentOfType(ProjectRoleManager.class);
        final Collection<ApplicationUser> projectMembers = new HashSet<>();
        for (final ProjectRole projectRole : manager.getProjectRoles()) {
            if (projectRole.getName().equalsIgnoreCase(roleName)) {
                final ProjectRoleActors actors = manager.getProjectRoleActors(projectRole, project);
                projectMembers.addAll(actors.getApplicationUsers());
            }
        }
        return projectMembers;
    }

    private String resolveRole(final Aperture.Role role) {
        final String jenkinsRole;
        switch (role) {
            case ADMIN:
                jenkinsRole = "Local Admin";
                break;
            case QUALITY_ASSURANCE:
            case DEVELOPER:
                jenkinsRole = "Developers";
                break;
            case USER:
            case MANAGER:
                jenkinsRole = "Readers";
                break;
            default:
                jenkinsRole = null;
        }
        return jenkinsRole;
    }
}
