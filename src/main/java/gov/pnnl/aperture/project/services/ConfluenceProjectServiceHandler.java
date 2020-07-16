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
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.ResponseException;
import com.atlassian.velocity.VelocityManager;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.JiraUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Project service handler for creating and configuring confluence spaces as part of Aperture.
 * <p>
 * The following URL provides the latest REST API information https://docs.atlassian.com/atlassian-confluence/REST/latest-server
 *
 * @author Developer Central @ PNNL
 */
public class ConfluenceProjectServiceHandler extends AbstractRestfulProjectServiceHandler {

    /**
     * Collection of Confluence space permissions required to remove a user from accessing a space.
     * <p>
     * https://developer.atlassian.com/confdev/confluence-rest-api/confluence-xml-rpc-and-soap-apis/remote-confluence-methods
     */
    private static final String[] CONF_PERMISSIONS = {
        "VIEWSPACE",
        "EDITSPACE",
        "EXPORTPAGE",
        "SETPAGEPERMISSIONS",
        "REMOVEPAGE",
        "EDITBLOG",
        "REMOVEBLOG",
        "COMMENT",
        "REMOVECOMMENT",
        "CREATEATTACHMENT",
        "REMOVEATTACHMENT",
        "REMOVEMAIL",
        "EXPORTSPACE",
        "SETSPACEPERMISSIONS"
    };

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ConfluenceProjectServiceHandler.class);

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param aperture reference to the current aperture service.
     * @param settings reference the current implementation of the aperture settings service.
     * @throws IllegalArgumentException if either aperture or settings parameters are <code>null</code>.
     */
    public ConfluenceProjectServiceHandler(final Aperture aperture, final ApertureSettings settings) {

        super(aperture, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApertureSettings.ProjectService getServiceType() {

        return ApertureSettings.ProjectService.CONFLUENCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isServiceAvailable(final String projectKey, final ApplicationLinkRequestFactory factory) throws ResponseException, CredentialsRequiredException {

        LOG.info(String.format("Checking service availability for Confluence service for project [%s]", projectKey));
        final String restURL = String.format("/rest/api/space/%s", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.GET, restURL);
        request.addHeader("Content-Type", "application/json");
        final JsonNode response = request.execute(new JSONApplicationLinkResponder(true));
        LOG.debug(String.format("Confluence space response : [%s]", response));
        return response != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void rollbackService(final String projectKey, final ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, CredentialsRequiredException {

        LOG.info(String.format("Rolling back Confluence servuice for key [%s]", projectKey));
        final String restURL = String.format("/rest/api/space/%s", projectKey);
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.DELETE, restURL);
        request.addHeader("Content-Type", "application/json");
        final JsonNode response = request.execute(new JSONApplicationLinkResponder());
        LOG.info(String.format("Confluence response:rollbackService('%s')", response));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createService(final MutableIssue issue, final ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final ApertureSettings settings = getApertureSettings();
        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, "/rest/api/space");
        final String spaceKey = settings.getProjectKeyFor(issue);
        final String spaceEntity = createBasicSpace(spaceKey, issue);

        request.addHeader("Content-Type", "application/json");
        request.setRequestBody(spaceEntity);
        LOG.debug(String.format("Creating Confluence Space:'%s'", spaceEntity));

        final JsonNode response = request.execute(new JSONApplicationLinkResponder());
        LOG.debug(String.format("Confluence Space creation response:'%s'", spaceEntity));

        final String base = response.get("_links").get("base").asText();
        final String path = response.get("homepage").get("_links").get("tinyui").asText();

        final String categoryFor = settings.getCategoryFor(issue);
        if (StringUtils.hasText(categoryFor)) {
            assignProjectCategory(factory, spaceKey, categoryFor);
        }

        final UserManager userManager = ComponentAccessor.getUserManager();
        final Collection<ApplicationUser> owner = Collections.singleton(userManager.getUserByName(issue.getReporterId()));
        modifyUsers(spaceKey, Aperture.PermissionMode.ADD, Aperture.Role.ADMIN, owner, factory, errors);

        JiraUtils.addErrorCollectionAsComments(issue, "Confluence Remote Link", addServiceLinkTo(issue, "Confluence Space", String.format("%s/%s", base, path)));
        addComponentTo(issue);
    }

    private void assignProjectCategory(final ApplicationLinkRequestFactory factory, final String projectKey, final String category) throws CredentialsRequiredException, ResponseException {

        final String basePath = "/gov/pnnl/aperture/xmlrpc/confluence/";
        final Map<String, Object> context = new HashMap<>();
        final String sessionId = doXmlRpcLogin(factory, context);
        final VelocityManager velocityManager = ComponentAccessor.getVelocityManager();
        final ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
        final String baseUrl = applicationProperties.getString(APKeys.JIRA_BASEURL);
        final String webworkEncoding = applicationProperties.getString(APKeys.JIRA_WEBWORK_ENCODING);

        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, "/rpc/xmlrpc");
        request.addHeader("Content-Type", "text/xml");
        context.put("sessionId", sessionId);
        context.put("category", category);
        context.put("spaceKey", projectKey);

        request.setRequestBody(velocityManager.getEncodedBody(basePath, "addLabelByNameToSpace.vm.xml", baseUrl, webworkEncoding, context));
        final JsonNode response = request.execute(new XmlRpcJsonResponder());
        LOG.debug(String.format("assignProjectCategory:response => %s", response));
    }

    private String createBasicSpace(final String spaceKey, final MutableIssue issue) throws IOException {

        // https://docs.atlassian.com/atlassian-confluence/REST/latest-server/#space-createSpace //
        final ObjectMapper om = new ObjectMapper();
        final Map<String, Object> confluenceSpace = new HashMap<>();

        final Map<String, Object> plainDescription = new HashMap<>();
        plainDescription.put("value", issue.getDescription());
        plainDescription.put("representation", "plain");

        confluenceSpace.put("key", spaceKey);
        confluenceSpace.put("name", issue.getSummary());
        confluenceSpace.put("description", Collections.singletonMap("plain", plainDescription));
        confluenceSpace.put("metadata", Collections.emptyMap());

        return om.writeValueAsString(confluenceSpace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyGroups(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<Group> groupList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final String basePath = "/gov/pnnl/aperture/xmlrpc/confluence/";
        final Map<String, Object> context = new HashMap<>();
        final String sessionId = doXmlRpcLogin(factory, context);
        final VelocityManager velocityManager = ComponentAccessor.getVelocityManager();
        final ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
        final String baseUrl = applicationProperties.getString(APKeys.JIRA_BASEURL);
        final String webworkEncoding = applicationProperties.getString(APKeys.JIRA_WEBWORK_ENCODING);
        final String permissionFile;
        switch (role) {
            case ADMIN:
                permissionFile = "grantSpaceOwnership.vm.xml";
                break;
            default:
                permissionFile = "addPermissionsToSpace.vml.xml";
                break;
        }

        for (final Group group : groupList) {
            context.clear();
            switch (mode) {
                case ADD:
                case REPLACE:
                    final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, "/rpc/xmlrpc");
                    request.addHeader("Content-Type", "text/xml");
                    context.put("sessionId", sessionId);
                    context.put("entity", group);
                    context.put("spaceKey", projectKey);
                    request.setRequestBody(velocityManager.getEncodedBody(basePath, permissionFile, baseUrl, webworkEncoding, context));
                    final JsonNode response = request.execute(new XmlRpcJsonResponder());
                    LOG.debug(String.format("group:set-permissions => %s", response));
                    break;
                case REMOVE:
                    for (final String permission : CONF_PERMISSIONS) {
                        final ApplicationLinkRequest req = factory.createRequest(Request.MethodType.POST, "/rpc/xmlrpc");
                        req.addHeader("Content-Type", "text/xml");
                        context.put("sessionId", sessionId);
                        context.put("entity", group);
                        context.put("spaceKey", projectKey);
                        context.put("permissionValue", permission);
                        req.setRequestBody(velocityManager.getEncodedBody(basePath, "removePermissionFromSpace.vm.xml", baseUrl, webworkEncoding, context));
                        final JsonNode resp = req.execute(new XmlRpcJsonResponder());
                        LOG.debug(String.format("group:remove-permissions => %s", resp));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyUsers(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<ApplicationUser> userList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException {

        final String basePath = "/gov/pnnl/aperture/xmlrpc/confluence/";
        final Map<String, Object> context = new HashMap<>();
        final String sessionId = doXmlRpcLogin(factory, context);
        final VelocityManager velocityManager = ComponentAccessor.getVelocityManager();
        final ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
        final String baseUrl = applicationProperties.getString(APKeys.JIRA_BASEURL);
        final String webworkEncoding = applicationProperties.getString(APKeys.JIRA_WEBWORK_ENCODING);
        final String permissionFile;
        switch (role) {
            case ADMIN:
                permissionFile = "grantSpaceOwnership.vm.xml";
                break;
            default:
                permissionFile = "addPermissionsToSpace.vm.xml";
                break;
        }
        for (final ApplicationUser user : userList) {
            context.clear();
            switch (mode) {
                case ADD:
                case REPLACE:
                    final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, "/rpc/xmlrpc");
                    request.addHeader("Content-Type", "text/xml");
                    context.put("sessionId", sessionId);
                    context.put("entity", user);
                    context.put("spaceKey", projectKey);
                    request.setRequestBody(velocityManager.getEncodedBody(basePath, permissionFile, baseUrl, webworkEncoding, context));
                    final JsonNode response = request.execute(new XmlRpcJsonResponder());
                    LOG.debug(String.format("user:set-permissions => %s", response));
                    break;
                case REMOVE:
                    for (final String permission : CONF_PERMISSIONS) {
                        final ApplicationLinkRequest req = factory.createRequest(Request.MethodType.POST, "/rpc/xmlrpc");
                        req.addHeader("Content-Type", "text/xml");
                        context.put("sessionId", sessionId);
                        context.put("entity", user.getName());
                        context.put("spaceKey", projectKey);
                        context.put("permissionValue", permission);
                        req.setRequestBody(velocityManager.getEncodedBody(basePath, "removePermissionFromSpace.vm.xml", baseUrl, webworkEncoding, context));
                        final JsonNode resp = req.execute(new XmlRpcJsonResponder());
                        LOG.debug(String.format("user:remove-permissions => %s", resp));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private String doXmlRpcLogin(final ApplicationLinkRequestFactory factory, final Map<String, Object> context) throws ResponseException, CredentialsRequiredException {

        final ApplicationLinkRequest request = factory.createRequest(Request.MethodType.POST, "/rpc/xmlrpc");
        final VelocityManager velocityManager = ComponentAccessor.getVelocityManager();
        final ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
        final String baseUrl = applicationProperties.getString(APKeys.JIRA_BASEURL);
        final String webworkEncoding = applicationProperties.getString(APKeys.JIRA_WEBWORK_ENCODING);

        final String basePath = "/gov/pnnl/aperture/xmlrpc/confluence/";
        final ApertureSettings settings = getApertureSettings();
        final Map<String, Object> configuration = settings.getServiceConfiguration(ApertureSettings.ProjectService.CONFLUENCE, "");
        context.putAll(configuration);

        request.addHeader("Content-Type", "text/xml");
        request.setRequestBody(velocityManager.getEncodedBody(basePath, "login.vm.xml", baseUrl, webworkEncoding, context));
        final JsonNode response = request.execute(new XmlRpcJsonResponder());
        LOG.debug(String.format("login-response => %s", response));
        if (response.has("params")) {
            final JsonNode parameters = response.get("params");
            if (parameters.isArray()) {
                final JsonNode sessionIdNode = parameters.get(0);
                return sessionIdNode.asText();
            }
        }
        return null;
    }
}
