package gov.pnnl.aperture.project.services;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationLinkRequest;
import com.atlassian.applinks.api.ApplicationLinkRequestFactory;
import com.atlassian.applinks.api.CredentialsRequiredException;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.ResponseException;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Project service handler for creating and configuring Crucible projects as part of Aperture.
 * <p>
 * Information about the REST API for crucible can be found here
 * https://developer.atlassian.com/display/FECRUDEV/REST+API+Guide
 *
 * @author Developer Central @ PNNL
 */
public class CrucibleProjectServiceHandler extends AbstractRestfulProjectServiceHandler {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(CrucibleProjectServiceHandler.class);

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param aperture reference to the current aperture service.
     * @param settings reference the current implementation of the aperture settings service.
     * @throws IllegalArgumentException if either aperture or settings parameters are <code>null</code>.
     */
    public CrucibleProjectServiceHandler(final Aperture aperture, final ApertureSettings settings) {

        super(aperture, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApertureSettings.ProjectService getServiceType() {

        return ApertureSettings.ProjectService.CRUCIBLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isServiceAvailable(String projectKey, ApplicationLinkRequestFactory factory) throws ResponseException, CredentialsRequiredException {

        LOG.info(String.format("Checking Crucible project availability for %s", projectKey));
        final String uri = String.format("/rest-service-fecru/admin/projects/%s", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, uri);
        request.addHeader("Content-Type", "application/json");
        final JsonNode response = request.execute(new JSONApplicationLinkResponder(true));
        LOG.debug(String.format("Removed crucible project:%s", response));
        return response != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void rollbackService(final String projectKey, final ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, CredentialsRequiredException {

        LOG.info(String.format("Rolling back Crucible service for project [%s]", projectKey));
        final String uri = String.format("/rest-service-fecru/admin/projects/%s?deleteProjectReviews=true", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.DELETE, uri);
        request.addHeader("Content-Type", "application/json");
        final String response = request.execute();
        LOG.debug(String.format("Removed crucible project:%s", response));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createService(final MutableIssue issue, final ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        LOG.info(String.format("Creating Crucible service for issue [%s]", issue.getKey()));
        final String stashAppId = getStashLinkId(factory);
        final ApertureSettings settings = getApertureSettings();
        final String projectKey = settings.getProjectKeyFor(issue);
        LOG.debug(String.format("Got Stash Application ID from Crucible:%s", stashAppId));
        if (StringUtils.hasLength(stashAppId)) {
            createFishEyeRepository(projectKey, factory, stashAppId);
            final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, "/rest-service-fecru/admin/projects");
            final String crucibleProject = createCrucibleProject(projectKey, issue);
            request.addHeader("Content-Type", "application/json");
            request.setRequestBody(crucibleProject);
            final URI base = getApplicationLink().getRpcUrl();
            final String path = String.format("project/%s", projectKey);
            final JsonNode response = request.execute(new JSONApplicationLinkResponder());
            LOG.debug(String.format("Created crucible project:%s", response));
            addServiceLinkTo(issue, "Crucible Project", String.format("%s/%s", base, path));
            addComponentTo(issue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyGroups(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<Group> groupList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final ObjectMapper om = new ObjectMapper();
        for (final Group projectGroup : groupList) {
            final String restURI = String.format("/rest-service-fecru/admin/projects/%s/allowed-reviewer-groups", projectKey);
            final Request.MethodType restMethod;
            switch (mode) {
                case ADD:
                case REPLACE:
                    restMethod = Request.MethodType.PUT;
                    break;
                case REMOVE:
                    restMethod = Request.MethodType.DELETE;
                    break;
                default:
                    restMethod = Request.MethodType.GET;
                    break;
            }
            final Map<String, String> nameMap = Collections.singletonMap("name", projectGroup.getName());
            final ApplicationLinkRequest linkRequest = factory.createRequest(restMethod, restURI);
            linkRequest.addHeader("Content-Type", "application/json");
            linkRequest.setRequestBody(om.writeValueAsString(nameMap));
            final String permissionResponse = linkRequest.execute();
            LOG.debug(String.format("modifyGroups(%s, %s)", projectGroup.getName(), permissionResponse));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyUsers(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<ApplicationUser> userList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final ObjectMapper om = new ObjectMapper();
        for (final ApplicationUser user : userList) {
            final String restURI = String.format("/rest-service-fecru/admin/projects/%s/allowed-reviewer-users", projectKey);
            final Request.MethodType restMethod;
            switch (mode) {
                case ADD:
                case REPLACE:
                    restMethod = Request.MethodType.PUT;
                    break;
                case REMOVE:
                    restMethod = Request.MethodType.DELETE;
                    break;
                default:
                    restMethod = Request.MethodType.GET;
                    break;
            }
            final Map<String, String> nameMap = Collections.singletonMap("name", user.getName());
            final ApplicationLinkRequest linkRequest = factory.createRequest(restMethod, restURI);
            linkRequest.addHeader("Content-Type", "application/json");
            linkRequest.setRequestBody(om.writeValueAsString(nameMap));
            final String permissionResponse = linkRequest.execute();
            LOG.debug(String.format("modifyUsers(%s, %s)", user.getName(), permissionResponse));
        }
    }

    private String getStashLinkId(final ApplicationLinkRequestFactory factory) throws CredentialsRequiredException, ResponseException {

        // the .json extension apparently is important here as the FECRU Rest service doesn't read the Content-Type //
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, "/rest/applinks/1.0/applicationlink.json");
        request.addHeader("Content-Type", "application/json");
        final JsonNode response = request.execute(new JSONApplicationLinkResponder());
        LOG.debug(String.format("getStashLinkId():'%s'", response));
        final JsonNode applicationLinks = response.get("applicationLinks");
        if (applicationLinks != null && applicationLinks.isArray()) {
            for (final JsonNode link : applicationLinks) {
                if (link.has("typeId") && "stash".equals(link.get("typeId").asText())) {
                    return link.get("id").asText();
                }
            }
        }
        throw new ResponseException(String.format("Failed to get stash link ID due to REST error:'%s'", response));
    }

    /**
     * Constructs the JSON string according to
     * https://docs.atlassian.com/fisheye-crucible/latest/wadl/fecru.html#rest-service-fecru:admin:projects
     * <p>
     *
     * @param projectKey project key for the new crucible project to be made.
     * @param issue      the issue containing other project request properties.
     * @return JSON string for the new crucible project to be created.
     * @throws IOException if there is an error creating the JSON string.
     */
    private String createCrucibleProject(final String projectKey, final MutableIssue issue) throws IOException {

        final ObjectMapper om = new ObjectMapper();
        final Map<String, Object> crucible = new HashMap<>();
        crucible.put("key", projectKey);
        crucible.put("name", issue.getSummary());
        crucible.put("defaultRepositoryName", String.format("%s-default", projectKey));
        crucible.put("storeFileContentInReview", Boolean.TRUE);
        crucible.put("permissionSchemeName", "agile");
        crucible.put("moderatorEnabled", Boolean.FALSE);
        crucible.put("allowReviewersToJoin", Boolean.TRUE);
        crucible.put("defaultDurationInWeekDays", 10);
        crucible.put("defaultObjectives", "Ensure code is readable, maintainable, and written with best practices in mind.");
        return om.writeValueAsString(crucible);
    }

    /**
     * Calls the Stash integration plug-in REST call to have the Stash plugin add the repo 'nicely'
     * <p>
     * This uses the stash integration plug-in to essentially simulate the automatic adding of repositories via Stash
     * outlined here
     * https://confluence.atlassian.com/fisheye/using-fisheye/integrating-fisheye-with-atlassian-applications/integrating-fisheye-with-stash
     * Stash will automatically generate/use the private SSH key and set everything up.
     *
     * @param factory    link factory for creating requests.
     * @param projectKey the project key for the repository to be created.
     * @param stashId    the crucible/fisheye application id for Stash
     * @see #getStashLinkId(com.atlassian.applinks.api.ApplicationLinkRequestFactory)
     */
    private void createFishEyeRepository(final String projectKey, final ApplicationLinkRequestFactory factory, final String stashId) throws IOException, CredentialsRequiredException, ResponseException {

        final String uri = String.format("/rest/stash-integration-plugin/1.0/%s/repo", stashId);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.PUT, uri);
        final ObjectMapper om = new ObjectMapper();
        final Map<String, Object> fisheye = new HashMap<>();
        fisheye.put("projectKey", projectKey);
        fisheye.put("cloneUrl", getCloneURLFor(projectKey));
        fisheye.put("slug", "default");
        fisheye.put("fecruRepoName", String.format("%s-default", projectKey).toLowerCase());

        request.addHeader("Content-Type", "application/json");
        request.setRequestBody(om.writeValueAsString(fisheye));
        final JsonNode response = request.execute(new JSONApplicationLinkResponder());
        LOG.debug(String.format("Created fisheye repository:%s", response));
    }

    /**
     * Queries Atlassian Stash for the SSH clone URL for the given project key.
     * <p>
     * It's easier to go straight to Stash's public API then using crucible's private and unrefined API to get the clone
     * URL for a give project repository. So the Stash project is assumed to be created by the time this method is
     * called.
     *
     * @param projectKey the project key to get the default repository SSL url for.
     * @return the default SSH clone URL for the given project key; <code>null</code> if not found.
     */
    private String getCloneURLFor(final String projectKey) throws CredentialsRequiredException, ResponseException {

        final ApertureSettings settings = getApertureSettings();
        final ApplicationLink link = settings.getApplicationLink(ApertureSettings.ProjectService.BITBUCKET);
        final ApplicationLinkRequestFactory factory = link.createAuthenticatedRequestFactory();
        final String url = String.format("/rest/api/1.0/projects/%s/repos/default", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, url);
        request.addHeader("Content-Type", "application/json");

        final JsonNode response = request.execute(new JSONApplicationLinkResponder());
        final JsonNode links = response.get("links");
        final JsonNode clone = links.get("clone");
        if (clone.isArray()) {
            for (final JsonNode cloneLink : clone) {
                final String linkName = cloneLink.get("name").asText();
                if ("ssh".equals(linkName)) {
                    LOG.debug(String.format("Found cloneURL for:%s => %s", projectKey, cloneLink));
                    return cloneLink.get("href").asText();
                }
            }
        }
        return null;
    }
}
