package gov.pnnl.aperture.project.services;

import aQute.bnd.build.Run;
import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationLinkRequest;
import com.atlassian.applinks.api.ApplicationLinkRequestFactory;
import com.atlassian.applinks.api.CredentialsRequiredException;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.ResponseException;
import com.atlassian.streams.api.common.uri.UriBuilder;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.updates.AvatarImageProvider;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Project service handler for creating and configuring Bitbucket projects as part of Aperture.
 * <p>
 * Information about the REST API provided by Atlassian Bitbucket can be found here
 * https://developer.atlassian.com/static/rest/bitbucket-server/4.14.3/bitbucket-rest.html
 *
 * @author Developer Central @ PNNL
 */
public class BitbucketProjectServiceHandler extends AbstractRestfulProjectServiceHandler {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(BitbucketProjectServiceHandler.class);

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param aperture reference to the current aperture service.
     * @param settings reference the current implementation of the aperture settings service.
     * @throws IllegalArgumentException if either aperture or settings parameters are <code>null</code>.
     */
    public BitbucketProjectServiceHandler(final Aperture aperture, final ApertureSettings settings) {

        super(aperture, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApertureSettings.ProjectService getServiceType() {

        return ApertureSettings.ProjectService.BITBUCKET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isServiceAvailable(final String projectKey, final ApplicationLinkRequestFactory factory) throws ResponseException, CredentialsRequiredException {

        LOG.info(String.format("Checking Bitbucket Server project availability for %s", projectKey));
        final String uri = String.format("/rest/api/1.0/projects/%s", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, uri);
        request.addHeader("Content-Type", "application/json");
        final JsonNode response = request.execute(new JSONApplicationLinkResponder(true));
        LOG.debug(String.format("Bitbucket server project response:%s", response));
        return response != null && !response.has("errors");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void rollbackService(final String projectKey, final ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, CredentialsRequiredException {

        LOG.info(String.format("Rolling back Bitbucket service for issue [%s]", projectKey));
        final String reposURL = String.format("/rest/api/1.0/projects/%s/repos", projectKey);
        boolean done = false;
        while (!done) {
            final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, reposURL);
            request.addHeader("Content-Type", "application/json");
            final JsonNode response = request.execute(new JSONApplicationLinkResponder());
            LOG.debug(String.format("Removing Bitbucket Repositories:%s", response));
            int count = 0;
            if (response.has("values")) {
                final JsonNode repositoriesValues = response.get("values");
                if (repositoriesValues.isArray()) {
                    for (final JsonNode jsonRepository : repositoriesValues) {
                        final String slug = jsonRepository.get("slug").asText();
                        LOG.info(String.format("Removing Bitbucket repository:[%s/%s]", projectKey, slug));
                        final String slugURL = String.format("/rest/api/1.0/projects/%s/repos/%s", projectKey, slug);
                        final ApplicationLinkRequest slugRequest = factory.createRequest(Request.MethodType.DELETE, slugURL);
                        request.addHeader("Content-Type", "application/json");
                        final JsonNode result = slugRequest.execute(new JSONApplicationLinkResponder());
                        if (result.has("errors")) {
                            LOG.warn(String.format("Failed to remove repository:[%s/%s]", projectKey, slug));
                            return;
                        }
                        removeFisheyeRepositoryLink(projectKey, slug);
                        count++;
                    }
                }
            }
            done = count == 0;
        }

        final String restURL = String.format("/rest/api/1.0/projects/%s", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.DELETE, restURL);
        request.addHeader("Content-Type", "application/json");
        final String response = request.execute();
        LOG.debug(String.format("Removed Bitbucket project:%s => %s", projectKey, response));
        if (StringUtils.hasText(response)) {
            if (response.contains("errors")) {
                LOG.warn(String.format("Failed to remove Bitbucket service:[%s]", response));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createService(final MutableIssue issue, final ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        LOG.info(String.format("Creating Bitbucket service for issue [%s]", issue.getKey()));
        final ApertureSettings settings = getApertureSettings();
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, "/rest/api/1.0/projects");
        final String projectKey = settings.getProjectKeyFor(issue);
        final String bbProjectModel = createBitbucketProject(projectKey, issue);
        LOG.debug(String.format("createService:request:'%s'", bbProjectModel));

        request.addHeader("Content-Type", "application/json");
        request.setRequestBody(bbProjectModel);
        final JsonNode response = request.execute(new JSONApplicationLinkResponder());
        LOG.debug(String.format("createService:response:'%s'", response));

        if (response != null) {
            if (response.get("errors") == null) {
                final JsonNode linkAttribute = response.get("links");
                final JsonNode selfAttribute = linkAttribute.get("self");
                final Iterator<JsonNode> linkAttributes = selfAttribute.getElements();
                if (linkAttributes.hasNext()) {
                    final JsonNode firstLink = linkAttributes.next();
                    final JsonNode bbURL = firstLink.get("href");
                    if (projectKey.equals(response.get("key").asText())) {
                        addServiceLinkTo(issue, "Bitbucket Server Project", bbURL.asText());
                        addComponentTo(issue);
                        createInitialRepository(issue, factory);
                        setAdminPermission(issue, factory);
                    }
                }
                return;
            }
            throw new ResponseException(String.format("Failed to create Bitbucket project due to REST errors:'%s'", response));
        }
        throw new IOException("/rest/api/1.0/projects:POST returned a null response.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyGroups(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<Group> groupList, ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final Map<String, List<String>> requestParameters = new HashMap<>();
        final String permission = resolvePermission(role);
        if (StringUtils.hasText(permission)) {
            for (final Group projectGroup : groupList) {
                requestParameters.clear();
                requestParameters.put("name", Collections.singletonList(projectGroup.getName()));
                final String paramString = UriBuilder.joinParameters(requestParameters);
                final String restURI = String.format("/rest/api/1.0/projects/%s/permissions/groups?%s", projectKey, paramString);
                final ApplicationLinkRequest linkRequest = getApplicationLink(restURI, permission, mode, factory, requestParameters);
                linkRequest.addHeader("Content-Type", "application/json");
                final String permissionResponse = linkRequest.execute();
                LOG.debug(String.format("modifyGroups(%s, %s) => %s", projectGroup.getName(), permission, permissionResponse));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyUsers(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<ApplicationUser> userList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final Map<String, List<String>> requestParameters = new HashMap<>();
        final String permission = resolvePermission(role);
        if (StringUtils.hasText(permission)) {
            for (final ApplicationUser projectMember : userList) {
                requestParameters.clear();
                requestParameters.put("name", Collections.singletonList(projectMember.getName()));

                final String paramString = UriBuilder.joinParameters(requestParameters);
                final String restURI = String.format("/rest/api/1.0/projects/%s/permissions/users?%s", projectKey, paramString);
                final ApplicationLinkRequest linkRequest = getApplicationLink(restURI, permission, mode, factory, requestParameters);
                linkRequest.addHeader("Content-Type", "application/json");
                final String permissionResponse = linkRequest.execute();
                LOG.debug(String.format("modifyUsers(%s, %s) => %s", projectMember.getName(), permission, permissionResponse));
            }
        }
    }

    private ApplicationLinkRequest getApplicationLink(final String restURI, final String permission, final Aperture.PermissionMode mode, final ApplicationLinkRequestFactory factory, final Map<String, List<String>> requestParameters) throws CredentialsRequiredException {
        final Request.MethodType restMethod;
        switch (mode) {
            case ADD:
            case REPLACE:
                requestParameters.put("permission", Collections.singletonList(permission));
                restMethod = Request.MethodType.PUT;
                break;
            case REMOVE:
                restMethod = Request.MethodType.DELETE;
                break;
            default:
                restMethod = Request.MethodType.GET;
                break;
        }
        return factory.createRequest(restMethod, restURI);
    }

    private String resolvePermission(final Aperture.Role role) {
        final String permission;
        switch (role) {
            case ADMIN:
                permission = "PROJECT_ADMIN";
                break;
            case QUALITY_ASSURANCE:
            case DEVELOPER:
                permission = "PROJECT_WRITE";
                break;
            case USER:
            case MANAGER:
                permission = "PROJECT_READ";
                break;
            default:
                permission = null;
        }
        return permission;
    }

    private void setAdminPermission(final MutableIssue issue, final ApplicationLinkRequestFactory factory) throws ResponseException, CredentialsRequiredException {

        final ApertureSettings settings = getApertureSettings();
        final ApplicationUser projectLead = issue.getReporter();
        final Map<String, List<String>> requestParameters = new HashMap<>();
        requestParameters.put("permission", Collections.singletonList("PROJECT_ADMIN"));
        requestParameters.put("name", Collections.singletonList(projectLead.getName()));
        final String queryString = UriBuilder.joinParameters(requestParameters);
        final String projectKey = settings.getProjectKeyFor(issue);
        final String uri = String.format("/rest/api/1.0/projects/%s/permissions/users?%s", projectKey, queryString);

        ApplicationLinkRequest request = factory.createRequest(Request.MethodType.PUT, uri);
        request.addHeader("Content-Type", "application/json");
        final String response = request.execute();
        LOG.debug(String.format("setUserPermissions(%s, %s):%s", projectLead.getName(), response, uri));
    }

    private void createInitialRepository(final MutableIssue issue, final ApplicationLinkRequestFactory factory) throws ResponseException, IOException, CredentialsRequiredException {

        final ApertureSettings settings = getApertureSettings();
        final String projectKey = settings.getProjectKeyFor(issue);
        final String uri = String.format("/rest/api/1.0/projects/%s/repos", projectKey);
        ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, uri);
        request.addHeader("Content-Type", "application/json");
        request.setRequestBody(createRepository(issue));

        final JsonNode result = request.execute(new JSONApplicationLinkResponder());
        try {
            final JsonNode repositoryLinks = result.get("links");
            final JsonNode gitWebView = repositoryLinks.get("self");
            final String gitURL = gitWebView.get(0).get("href").asText();
            addServiceLinkTo(issue, "Git Repository", gitURL);
        } catch (RuntimeException rte){
            LOG.warn(String.format("failed to extract git URL from response: %s", result), rte);
        }
    }

    private String createBitbucketProject(final String spaceKey, final MutableIssue issue) throws IOException {

        final ObjectMapper om = new ObjectMapper();
        final Map<String, Object> bbProjectModel = new HashMap<>();
        bbProjectModel.put("key", spaceKey);
        bbProjectModel.put("name", issue.getSummary());
        bbProjectModel.put("description", issue.getDescription());

        final ByteArrayOutputStream b64os = new ByteArrayOutputStream();
        new AvatarImageProvider("/gov/pnnl/aperture/images/new-project-avatar.png").storeImage(Avatar.Size.SMALL, b64os);
        final String avatarData = Base64.getEncoder().encodeToString(b64os.toByteArray());
        bbProjectModel.put("avatar", String.format("data:image/png;base64,%s", avatarData));
        return om.writeValueAsString(bbProjectModel);
    }

    private String createRepository(final MutableIssue issue) throws IOException {

        final ObjectMapper om = new ObjectMapper();
        final Map<String, Object> gitRepo = new HashMap<>();
        gitRepo.put("scmId", "git");
        gitRepo.put("name", "default");
        return om.writeValueAsString(gitRepo);
    }

    /**
     * Removes the FishEye repository scanning when a Bitbucket project is removed with all of it's repositories as well.
     * <p>
     * Repositories must first be stopped in fish-eye and then removed; they cannot be removed if they are 'running'
     * <ul>
     * <li>
     * https://docs.atlassian.com/fisheye-crucible/latest/wadl/fecru.html#rest-service-fecru:admin:repositories:name:stop
     * </li>
     * <li>
     * https://docs.atlassian.com/fisheye-crucible/latest/wadl/fecru.html#rest-service-fecru:admin:repositories:name
     * </li>
     * </ul>
     *
     * @param repository the project key to get the default repository SSL url for.
     * @return the HTTP response of the REST call.
     */
    private String removeFisheyeRepositoryLink(final String projectKey, final String repository) throws CredentialsRequiredException, ResponseException {

        final ApertureSettings settings = getApertureSettings();
        final ApplicationLink link = settings.getApplicationLink(ApertureSettings.ProjectService.CRUCIBLE);
        if (link == null) {
            LOG.debug("Crucible application link is not currently enabled.");
            return null;
        }

        final ApplicationLinkRequestFactory factory = link.createAuthenticatedRequestFactory();
        String url = String.format("/rest-service-fecru/admin/repositories/%s-%s/stop", projectKey.toLowerCase(), repository);
        ApplicationLinkRequest request = factory.createRequest(Request.MethodType.PUT, url);
        request.addHeader("Content-Type", "application/json");
        LOG.info(String.format("Stopping FishEye repository:[%s-%s]", projectKey, repository));
        final String response = request.execute();
        LOG.info(String.format("Stopped FishEye repository:[%s-%s] %s", projectKey, repository, response));

        url = String.format("/rest-service-fecru/admin/repositories/%s-%s", projectKey.toLowerCase(), repository);
        request = factory.createRequest(Request.MethodType.DELETE, url);
        request.addHeader("Content-Type", "application/json");
        LOG.info(String.format("Removing FishEye repository:[%s-%s]", projectKey, repository));
        return request.execute();
    }

}
