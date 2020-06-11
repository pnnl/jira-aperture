package gov.pnnl.aperture.webwork.action;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.JiraUtils;
import gov.pnnl.aperture.ProjectServiceHandler;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Developer Central @ PNNL
 */
public class ApertureProjectServiceTools extends JiraWebActionSupport {

    /**
     * Reference to the current logger instance for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ApertureProjectServiceTools.class);
    /**
     * Reference to the current Aperture service in this server context.
     */
    private final Aperture aperture;
    /**
     * Reference to the current ApertureSettings service in this server context.
     */
    private final ApertureSettings apertureSettings;
    /**
     * Reference to the working project key this web action is operating on; can be 'invalid' if services are created.
     */
    private String projectKey = null;

    public ApertureProjectServiceTools(final Aperture aperture, final ApertureSettings apertureSettings) {

        Assert.notNull(aperture, "Invalid Aperture service reference.");
        Assert.notNull(apertureSettings, "Invalid Aperture Settings service reference.");
        this.aperture = aperture;
        this.apertureSettings = apertureSettings;
    }

    public String getProjectKey() {

        return this.projectKey;
    }

    public void setProjectKey(final String projectKey) {

        this.projectKey = projectKey;
    }

    /**
     * @return
     * @throws Exception
     */
    @Override
    public String doExecute() throws Exception {

        LOG.debug("doExecute();");
        final JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        context.setLoggedInUser(getLoggedInUser());

        final HttpServletRequest httpRequest = getHttpRequest();
        final IssueManager issueManager = ComponentAccessor.getIssueManager();
        final MutableIssue issue = issueManager.getIssueByKeyIgnoreCase(httpRequest.getParameter("issue"));
        final String serviceName = httpRequest.getParameter("service");
        final Boolean removeFirst = Boolean.valueOf(httpRequest.getParameter("remove"));

        final ApertureSettings.ProjectService service = ApertureSettings.ProjectService.valueOf(serviceName);
        LOG.debug(String.format("processing:doExecute(%s/%s):%s;", issue.getKey(), serviceName, removeFirst));

        final ProjectServiceHandler handler = aperture.getServiceHandler(service);
        final Map<String, Serializable> environment = new HashMap<>();
        final ErrorCollection errors = new SimpleErrorCollection();
        try {
            if (removeFirst) {
                final ErrorCollection removeErrors = handler.destroyService(projectKey, environment);
                if (removeErrors.hasAnyErrors()) {
                    for (final String errorMessage : removeErrors.getErrorMessages()) {
                        LOG.warn(String.format("destroyService(%s):%s - %s", projectKey, serviceName, errorMessage));
                    }
                }
            }

            final ErrorCollection serviceErrors = handler.createService(issue, environment);
            if (serviceErrors.hasAnyErrors()) {
                for (Map.Entry<String, String> entry : serviceErrors.getErrors().entrySet()) {
                    errors.addError(entry.getKey(), entry.getValue());
                }
            }
        } catch (final RuntimeException error) {
            final StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw, true));
            final String st = sw.toString();
            final String errorMessage = String.format("Failed to invoke service handler: *%s*; reason: _%s_\n{noformat}%s{noformat}", handler.getServiceType(), error, st);
            errors.addErrorMessage(errorMessage);
            LOG.fatal(String.format("Failed to invoke service handler:%s; reason:%s", serviceName, error), error);
            // need to remove it as it could be in an inconsistent or inaccessible state //
            final ErrorCollection serviceErrors = handler.destroyService(projectKey, environment);
            if (serviceErrors.hasAnyErrors()) {
                for (Map.Entry<String, String> entry : serviceErrors.getErrors().entrySet()) {
                    errors.addError(entry.getKey(), entry.getValue());
                }
            }
        }
        if (errors.hasAnyErrors()) {
            JiraUtils.addErrorCollectionAsComments(issue, String.format("%s (_single_)", serviceName), errors);
        }
        return getRedirect(String.format("/browse/%s", issue.getKey()));
    }

}
