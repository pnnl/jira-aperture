package gov.pnnl.aperture.workflow.function;

import com.atlassian.jira.bc.issue.link.RemoteIssueLinkService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.atlassian.plugin.util.Assertions;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.JiraUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a work-flow post-function that allows the notification for new project provisioning to all team members.
 * <p>
 * This function should be assigned when a new project services request via JIRA is completed and in the done stage.
 * This function should then be invoked regardless of being done via an automated or manual process.
 *
 * @author Developer Central @ PNNL
 */
public class ProjectCreationNotificationFunction extends AbstractJiraFunctionProvider {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ProjectCreationNotificationFunction.class);
    /**
     * Reference to the current instance of ApertureSettings in the current application context.
     */
    private final ApertureSettings apertureSettings;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param apertureSettings reference to the current ApertureSettings service implementation in the current context.
     * @throws IllegalArgumentException if either of the aperture settings is <code>null</code>.
     */
    public ProjectCreationNotificationFunction(final ApertureSettings apertureSettings) {

        Assertions.notNull("ApertureSettings", apertureSettings);
        this.apertureSettings = apertureSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final Map transientVars, final Map args, final PropertySet ps) throws WorkflowException {

        final MutableIssue issue = getIssue(transientVars);
        final Collection<ApplicationUser> projectMembers = apertureSettings.getProjectMembersFor(issue);

        for (final ApplicationUser user : projectMembers) {
            final RemoteIssueLinkService linkService = ComponentAccessor.getComponentOfType(RemoteIssueLinkService.class);
            final Map<String, Object> overrides = new HashMap<>();
            overrides.put("projectKey", apertureSettings.getProjectKeyFor(issue));
            overrides.put("i18n", ComponentAccessor.getI18nHelperFactory().getInstance(user));
            overrides.put("issueLinks", linkService.getRemoteIssueLinksForIssue(user, issue).getRemoteIssueLinks());
            overrides.put("issue", issue);
            overrides.put("recipient", user);
            overrides.put("actionUser", issue.getReporter());
            overrides.put("headerTitle", String.format("Welcome to Developer Central Project '%s' / %s", issue.getSummary(), overrides.get("projectKey")));
            overrides.put("projectMembers", apertureSettings.getProjectMembersFor(issue));
            final String subject = String.format("Welcome to the %s [%s] project from Developer Central", issue.getSummary(), overrides.get("projectKey"));
            try {
                LOG.debug(String.format("Delivering email to:%s <%s>", user.getDisplayName(), user.getEmailAddress()));
                JiraUtils.deliverEmail(overrides, user.getEmailAddress(), "/gov/pnnl/aperture/templates/email/welcome-project.vm.html", subject);
            } catch (IOException ex) {
                throw new WorkflowException(ex);
            }
        }
    }

}
