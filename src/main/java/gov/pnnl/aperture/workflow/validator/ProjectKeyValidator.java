package gov.pnnl.aperture.workflow.validator;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationLinkRequest;
import com.atlassian.applinks.api.ApplicationLinkRequestFactory;
import com.atlassian.applinks.api.CredentialsRequiredException;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueKey;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.util.Assertions;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.atlassian.sal.api.net.ResponseStatusException;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.InvalidInputException;
import com.opensymphony.workflow.Validator;
import com.opensymphony.workflow.WorkflowException;
import gov.pnnl.aperture.ApertureSettings;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JIRA Work-flow validator for validating the {@link ApertureSettings.CustomField#PROJECT_KEY} custom field is valid.
 * <p>
 * A valid project key is first and foremost a valid JIRA project key. Secondly it's a project key that is not already
 * in use in the current JIRA installation.
 *
 * @author Developer Central @ PNNL
 */
public class ProjectKeyValidator implements Validator {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ProjectKeyValidator.class);
    /**
     * Reference to the current aperture settings component in this application context.
     */
    private final ApertureSettings apertureSettings;
    /**
     * Common regular expression pattern for allowable project keys across all Developer Central services.
     */
    private final Pattern keyPattern = Pattern.compile("[A-Za-z]+", Pattern.CASE_INSENSITIVE);

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param apertureSettings reference to the current Aperture component implementation in the current context.
     * @throws IllegalArgumentException if the aperture service provided is null.
     */
    public ProjectKeyValidator(final ApertureSettings apertureSettings) {

        Assertions.notNull("ApertureSettings", apertureSettings);
        this.apertureSettings = apertureSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate(final Map transientVars, final Map args, PropertySet ps) throws WorkflowException {

        final Issue issue = (Issue) transientVars.get("issue");
        final CustomField customField = apertureSettings.getCustomField(ApertureSettings.CustomField.PROJECT_KEY);
        final String projectKey = (String) issue.getCustomFieldValue(customField);

        final boolean validKey = IssueKey.isValidKey(IssueKey.format(projectKey, 1));
        if (!validKey) {
            final String msg = String.format("The project key '%s' is not a valid project key; please check the format.", projectKey);
            throw new InvalidInputException(msg);
        }

        final Matcher matcher = keyPattern.matcher(projectKey);
        if (matcher.matches()) {
            final ProjectManager projectManager = ComponentAccessor.getProjectManager();
            final Project projectObjByKey = projectManager.getProjectByCurrentKeyIgnoreCase(projectKey);
            if (projectObjByKey != null) {
                final ApplicationUser projectLead = projectObjByKey.getProjectLead();
                final Object[] mArgs = {
                    projectKey, projectObjByKey.getName(), projectLead.getEmailAddress(), projectLead.getName()
                };
                final String msg = String.format("The project key '%s' is already in use by project '%s' please contact project lead <a href=\"mailto:%s\">%s</a>.", mArgs);
                throw new InvalidInputException(msg);
            }
        } else {
            final String msg = String.format("The project key '%s' is not valid and must consist of letters only (A-Z, a-z)", projectKey);
            throw new InvalidInputException(msg);
        }
        if (isConfluenceKeyInUse(projectKey)) {
            final String msg = String.format("The project key '%s' is valid but in use in Conflunce", projectKey);
            throw new InvalidInputException(msg);
        }
    }

    /**
     * Verifies that the confluence space key for a project key is also not already in use.
     * <p>
     * Verifies that the confluence space key is also not already in use using the API call
     * https://docs.atlassian.com/confluence/REST/latest/#d3e143
     *
     * @param projectKey the project key to use as the confluence space key to check availability for.
     * @return <code>true</code> if the confluence key is in use and cannot be used for a new project.
     */
    private boolean isConfluenceKeyInUse(final String projectKey) {

        final ApplicationLink link = apertureSettings.getApplicationLink(ApertureSettings.ProjectService.CONFLUENCE);
        if (link == null) {
            LOG.warn("No confluence application link is configured; cannot verify key in use.");
            return false;
        }
        final ApplicationLinkRequestFactory factory = link.createAuthenticatedRequestFactory();
        String url = String.format("/rest/api/space/%s", projectKey);
        final ApplicationLinkRequest request;
        try {
            request = factory.createRequest(Request.MethodType.GET, url);
            request.addHeader("Content-Type", "application/json");
            LOG.info(String.format("Checking Confluence Space Key:[%s]", projectKey));
            final String response = request.execute();
            LOG.debug(String.format("Confluence space response:[%s] => %s", projectKey, response));
            if (StringUtils.hasText(response)) {
                return true;
            }
        } catch (ResponseStatusException ex) {
            final Response response = ex.getResponse();
            if (response.getStatusCode() == HttpServletResponse.SC_NOT_FOUND) {
                // the space doesn't exist or we don't have permissions to view the space //
                return false;
            }
            // if the response is something other than 200 or 404 i.e. error re-throw the error //
            LOG.warn(String.format("Response status exception while checking for existing space:'%s'", projectKey), ex);
            throw new IllegalStateException(ex);
        } catch (CredentialsRequiredException ex) {
            LOG.error("Invalid credentials while checking for existing confluence space.", ex);
            throw new IllegalStateException(ex);
        } catch (ResponseException ex) {
            LOG.error(String.format("General response error checking confluence space key:%s", projectKey), ex);
            throw new IllegalStateException(ex);
        }
        return false;
    }
}
