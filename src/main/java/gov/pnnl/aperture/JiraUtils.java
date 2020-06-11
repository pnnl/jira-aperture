package gov.pnnl.aperture;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.bc.project.component.ProjectComponentManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.mail.Email;
import com.atlassian.jira.mail.builder.EmailBuilder;
import com.atlassian.jira.mail.util.MimeTypes;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.mail.queue.MailQueue;
import com.atlassian.mail.queue.MailQueueItem;
import com.atlassian.plugin.util.ClassLoaderUtils;
import gov.pnnl.aperture.project.tasks.AbstractAperturePluginJob;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Usual collection of high-level functions that are mildly re-usable throughout Aperture.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public class JiraUtils {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(JiraUtils.class);

    /**
     * Default utility class constructor.
     */
    private JiraUtils() {

    }

    /**
     * Utility method for delivering emails via JIRA {@link MailQueue} easily.
     * <p>
     * If the <code>body</code> or <code>subject</code> parameters start with '/' it will be presumed to be a Java
     * classpath resource reference and an attempt to read the content from there will be made. If there is any issue
     * reading those resources a respective {@link IOException} will be thrown.
     * <p>
     * Using the email builders, all content in the email body and subject can use velocity macros and substitution
     * of values from the given <code>parameters</code>
     *
     * @param parameters key-value substitutions for the email body.
     * @param to         the email address of who is to receive the email.
     * @param body       the content body of the email message.
     * @param subject    the subject of the email message.
     * @return <code>true</code> if the email is enqueued successfully.
     * @throws IOException if there is an issue delivering the email or reading input files.
     */
    public static boolean deliverEmail(final Map<String, Object> parameters, final String to, final String body, final String subject) throws IOException {

        final ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
        final String webworkEncoding = applicationProperties.getString(APKeys.JIRA_WEBWORK_ENCODING);
        final Email email = new Email(to);
        final EmailBuilder builder = new EmailBuilder(email, MimeTypes.Text.HTML, Locale.ENGLISH);
        final MailQueue emailQueue = ComponentAccessor.getMailQueue();

        email.setMimeType(MimeTypes.Text.HTML);
        email.setEncoding(webworkEncoding);
        email.setReplyTo("Developer Central Aperture <do-not-reply@pnnl.gov>");
        builder.addParameters(parameters);
        if (StringUtils.hasText(body) && body.startsWith("/")) {
            final InputStream is = ClassLoaderUtils.getResourceAsStream(body, AbstractAperturePluginJob.class);
            try {
                builder.withBody(IOUtils.toString(is));

            } finally {
                IOUtils.closeQuietly(is);
            }
        } else {
            builder.withBody(body);
        }
        if (StringUtils.hasText(subject) && subject.startsWith("/")) {
            final InputStream is = ClassLoaderUtils.getResourceAsStream(subject, AbstractAperturePluginJob.class);
            try {
                builder.withSubject(IOUtils.toString(is));
            } finally {
                IOUtils.closeQuietly(is);
            }
        } else {
            builder.withSubject(subject);
        }
        final MailQueueItem queueItem = builder.renderLater();
        emailQueue.addItem(queueItem);
        return true;
    }


    /**
     * Adds an error collection as a comment that is viewable only to 'jira-administrators'.
     * <p>
     * This method is intended to make it easier to get to error messages without having to grep log files for when
     * something bad happens in Aperture. The comments are restricted to 'jira-administrators' group as the exceptions
     * could potentially contain sensitive information.
     * <p>
     * The comment is also formatted properly using JIRA's built in wiki syntax. So the error message will render as a
     * unordered list of strings.
     *
     * @param issue        the issue where the errors will be added to as a comment.
     * @param errorContext the user-defined error context which this error occurred.
     * @param errors       the errors to print to the given <em>issue</em>.
     * @return the comment object that contains the formatted errors as a comment to the given issue.
     */
    public static Comment addErrorCollectionAsComments(final MutableIssue issue, final String errorContext, final ErrorCollection errors) {

        if (errors.hasAnyErrors()) {
            final JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext();
            final CommentManager commentManager = ComponentAccessor.getCommentManager();
            final StringBuilder commentBody = new StringBuilder();
            commentBody.append(String.format("Errors for execution context:%s\n", errorContext));
            for (String errorMessage : errors.getErrorMessages()) {
                commentBody.append(String.format("* %s\n", errorMessage));
            }
            final Map<String, String> fieldErrors = errors.getErrors();
            for (Map.Entry<String, String> error : fieldErrors.entrySet()) {
                commentBody.append(String.format("* [%s] - %s\n", error.getKey(), error.getValue()));
            }
            return commentManager.create(issue, authContext.getLoggedInUser(), commentBody.toString(), "jira-administrators", null, false);
        }
        return null;
    }

    /**
     * Utility method to create a mapping of service types to fully qualified URLs for a given JIRA project.
     * <p>
     *
     * @param params  collection of properties; this method will inject a series of new values into the map.
     * @param project JIRA project reference to extract the project key from.
     * @return mapping of service types to their respective URL location.
     * @see #getProjectLinks(Map, String)
     */
    public static Map<ApertureSettings.ProjectService, String> getProjectLinks(final Map<String, Object> params, final Project project) {

        return getProjectLinks(params, project.getKey());
    }


    /**
     * Utility method for assigning an issue to a given person with no comment.
     * <p>
     *
     * @param user  the user to assign the associated issue to.
     * @param issue the issue to update the assigned user to.
     * @return an error collection that may have resulted in the issue update.
     */
    public static ErrorCollection assignIssue(final ApplicationUser user, final MutableIssue issue) {
        return assignIssue(user, issue, null);
    }

    /**
     * Utility method for assigning an issue to a given person with a comment.
     * <p>
     *
     * @param user    the user to assign the associated issue to.
     * @param issue   the issue to update the assigned user to.
     * @param comment optional text for the comment describing the intent of this assignment.
     * @return an error collection that may have resulted in the issue update.
     */
    public static ErrorCollection assignIssue(final ApplicationUser user, final MutableIssue issue, final String comment) {

        final IssueService issueService = ComponentAccessor.getIssueService();
        final IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
        issueInputParameters.setAssigneeId(user.getName());
        if (org.springframework.util.StringUtils.hasText(comment)) {
            issueInputParameters.setComment(comment);
        }
        final IssueService.UpdateValidationResult validationResult = issueService.validateUpdate(user, issue.getId(), issueInputParameters);
        if (validationResult.isValid()) {
            LOG.debug(String.format("Assigning issue:'%s' to user:'%s (%s)'", issue.getKey(), user.getDisplayName(), user.getName()));
        } else {
            LOG.warn(String.format("Failed to assign issue:'%s' to user:'%s (%s)'", issue.getKey(), user.getDisplayName(), user.getName()));
            final ErrorCollection errorCollection = validationResult.getErrorCollection();
            for (final String error : errorCollection.getErrorMessages()) {
                LOG.warn(String.format("Assignment error -- %s", error));
            }
        }
        return validationResult.getErrorCollection();
    }

    /**
     * Utility method to reverse lookup a project component based on a service name.
     * <p>
     * This method facilitates the ability to get the component and therefore possible assignees when a particular
     * service fails in aperture. Also allows for automatic emails to the assignee as well.
     * <p>
     * By default Aperture will install these components via {@link Aperture#installApertureSchemesFor(Project)} so
     * they should exist in a normal usage; if they for whatever reason do not this method can return <code>null</code>.
     *
     * @param projectKey for the Aperture based project; <strong>NOT</strong> the project being modified by Aperture.
     * @param service    the logical service to resolve to a component.
     * @return project component with the same name as the service provided; can be <strong>null</strong> if not found.
     */
    public static ProjectComponent getComponentForService(final String projectKey, final ApertureSettings.ProjectService service) {

        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final ProjectComponentManager componentManager = ComponentAccessor.getProjectComponentManager();
        final Project project = projectManager.getProjectByCurrentKey(projectKey);

        for (ProjectComponent component : componentManager.findAllForProject(project.getId())) {
            if (component.getName().equalsIgnoreCase(service.name())) {
                return component;
            }
        }
        return null;
    }

    public static ApplicationUser getServiceLead(final String projectKey, final ApertureSettings.ProjectService service) {

        final ProjectComponent component = JiraUtils.getComponentForService(projectKey, service);
        if (Objects.isNull(component)) {
            return null;
        }
        return component.getComponentLead();
    }

    public static ApplicationUser getServiceLead(final Issue issue, final ApertureSettings.ProjectService service) {

        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final Project project = projectManager.getProjectObj(issue.getProjectId());
        return getServiceLead(project.getKey(), service);
    }

    /**
     * Utility method to create a mapping of service types to fully qualified URLs for a given JIRA project.
     * <p>
     *
     * @param params     collection of properties; this method will inject a series of new values into the map.
     * @param projectKey unique project key as it exists in other atlassian services.
     * @return mapping of service types to their respective URL location.
     * @see #getProjectLinks(Map, String)
     */
    public static Map<ApertureSettings.ProjectService, String> getProjectLinks(final Map<String, Object> params, final String projectKey) {

        final Map<ApertureSettings.ProjectService, String> linkList = new EnumMap<>(ApertureSettings.ProjectService.class);
        final ApertureSettings apertureSettings = ComponentAccessor.getOSGiComponentInstanceOfType(ApertureSettings.class);
        for (ApertureSettings.ProjectService service : ApertureSettings.ProjectService.values()) {
            final ApplicationLink applicationLink = apertureSettings.getApplicationLink(service);
            if (applicationLink != null) {
                // TODO not entirely sure why this is being done and might to be work around something but this is some
                // pseudo-voodoo going on here.
                params.put(String.format("%s_lnk", service.name()), applicationLink);
                switch (service) {
                    case CONFLUENCE:
                        linkList.put(service, String.format("%s/display/%s", applicationLink.getRpcUrl(), projectKey));
                        break;
                    case CRUCIBLE:
                        linkList.put(service, String.format("%s/project/%s", applicationLink.getRpcUrl(), projectKey));
                        break;
                    case JENKINS:
                        linkList.put(service, String.format("%s/job/%s", applicationLink.getRpcUrl(), projectKey));
                        break;
                    case JIRA:
                        linkList.put(service, String.format("%s/browse/%s", applicationLink.getRpcUrl(), projectKey));
                        break;
                    case BITBUCKET:
                        linkList.put(service, String.format("%s/projects/%s", applicationLink.getRpcUrl(), projectKey));
                        break;
                    default:
                        break;
                }
            }
        }
        return linkList;
    }
}
