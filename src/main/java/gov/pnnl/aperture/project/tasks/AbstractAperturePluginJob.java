package gov.pnnl.aperture.project.tasks;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.plugin.util.Assertions;
import com.atlassian.scheduler.JobRunner;
import com.opensymphony.workflow.loader.ActionDescriptor;
import gov.pnnl.aperture.JiraUtils;
import org.apache.log4j.Logger;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * Base class for background tasks that run in JIRA's {@link com.atlassian.scheduler.SchedulerService}.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public abstract class AbstractAperturePluginJob implements JobRunner {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(AbstractAperturePluginJob.class);

    /**
     * Get issue parameters when a background task is automating an existing JIRA workflow.
     * <p>
     *
     * @return new non-null instance of {@link IssueInputParameters}
     * @see IssueService#newIssueInputParameters()
     * @see #transitionIssue(ApplicationUser, MutableIssue, ActionDescriptor, IssueInputParameters)
     */
    IssueInputParameters getWorkflowParameters() {

        final IssueService issueService = ComponentAccessor.getIssueService();
        return issueService.newIssueInputParameters();
    }

    /**
     * Delivers a new email to the given address with a given body and content-body.
     * <p>
     *
     * @param parameters key-value pairs for substitution in the email content-body.
     * @param to         email address to deliver the email email to.
     * @param body       content-body of the message.
     * @param subject    of the email to be delivered.
     * @return <code>true</code> if the email message is successfully queued.
     * @throws IOException if there is an exception during delivery of the email.
     * @see JiraUtils#deliverEmail(Map, String, String, String)
     */
    boolean deliverEmail(final Map<String, Object> parameters, final String to, final String body, final String subject) throws IOException {

        if (StringUtils.hasText(to)) {
            LOG.debug(String.format("Sending email notification to:'%s'", to));
            return JiraUtils.deliverEmail(parameters, to, body, subject);
        }
        LOG.debug(String.format("Skipping email notification:'%s' due to an empty to: parameter", subject));
        return false;
    }

    /**
     * Utility method for transitioning a JIRA workflow.
     * <p>
     *
     * @param user              <em>who</em> is making this workflow transisition
     * @param issue             issue that is being transition in it's JIRA workflow.
     * @param workflowAction    the action to invoke to transition the issue to it's next state.
     * @param transitionDetails parameters for the transition.
     * @return <code>true</code> if the transition was successful.
     */
    boolean transitionIssue(final ApplicationUser user, final MutableIssue issue, final ActionDescriptor workflowAction, final IssueInputParameters transitionDetails) {

        Assertions.notNull("workflowAction", workflowAction);
        Assertions.notNull("transitionDetails", transitionDetails);
        Assertions.notNull("issue", issue);
        final IssueService issueService = ComponentAccessor.getIssueService();
        final int workflowActionId = workflowAction.getId();
        final IssueService.TransitionValidationResult tvr = issueService.validateTransition(user, issue.getId(), workflowActionId, transitionDetails);

        if (!tvr.isValid()) {
            LOG.error(String.format("Failed to transition issue [%s] using action [%s (%s)]", issue.getKey(), workflowAction.getName(), workflowActionId));
            final ErrorCollection errorCollection = tvr.getErrorCollection();
            for (final String error : errorCollection.getErrorMessages()) {
                LOG.error(String.format("Transition error -- %s", error));
            }
            return false;
        }
        LOG.debug(String.format("Transitioning issue for action [%s (%s)]", workflowAction.getName(), workflowActionId));
        issueService.transition(user, tvr);
        return true;
    }


    /**
     * Adds a collection of error messages as a comment to an existing JIRA issue.
     * <p>
     *
     * @param issue        non-null reference to an existing JIRA issue to add comments to.
     * @param errorContext the user-defined context value in which the error occurred.
     * @param errors       the collection of error messages to add to the issue.
     * @return the resulting comment that was added the issue provided.
     * @see JiraUtils#addErrorCollectionAsComments(MutableIssue, String, ErrorCollection)
     */
    Comment addErrorCollectionAsComments(final MutableIssue issue, final String errorContext, final ErrorCollection errors) {

        return JiraUtils.addErrorCollectionAsComments(issue, errorContext, errors);
    }
}
