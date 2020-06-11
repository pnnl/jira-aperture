package gov.pnnl.aperture.project.tasks;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.collect.CollectionUtil;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.mail.queue.MailQueue;
import com.atlassian.scheduler.JobRunnerRequest;
import com.atlassian.scheduler.JobRunnerResponse;
import com.atlassian.scheduler.config.JobConfig;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.JiraUtils;
import gov.pnnl.aperture.WorkflowConfiguration;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Background task that creates a new project in JIRA as well as other connected services.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public class ApertureProjectCreatorTask extends AbstractAperturePluginJob {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ApertureProjectCreatorTask.class);
    /**
     * Reference to the installed OSGI instance of {@link ApertureSettings} in this JIRA instance.
     */
    private final ApertureSettings apertureSettings;
    /**
     * Reference to the installed OSGI instance of {@link Aperture} in this JIRA instance.
     */
    private final Aperture aperture;

    @Inject
    public ApertureProjectCreatorTask(@NotNull final Aperture aperture, @NotNull final ApertureSettings apertureSettings) {

        this.apertureSettings = apertureSettings;
        this.aperture = aperture;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobRunnerResponse runJob(@NotNull final JobRunnerRequest jobRunnerRequest) {

        final JobConfig jobConfig = jobRunnerRequest.getJobConfig();
        final Map<String, Serializable> environment = jobConfig.getParameters();
        LOG.info("Running Aperture Project Creator Task.");
        LOG.debug(String.format("Project Creator Task Environment: %s", new JSONObject(environment)));

        final String issueKey = (String) environment.get("issue-key");
        try {
            if (StringUtils.hasText(issueKey)) {
                createApertureProject(issueKey, environment);
                return JobRunnerResponse.success(String.format("Successfully created project from JIRA-Issue:'%s'", issueKey));
            }
            LOG.error("Failed to create project no issue-key reference in environment.");
            return JobRunnerResponse.failed("Failed to create project no issue-key reference in environment.");
        } catch (RuntimeException ex) {
            LOG.error(String.format("Failed to create project from JIRA-Issue:'%s'", issueKey), ex);
            return JobRunnerResponse.failed(ex);
        } finally {
            LOG.info("Finished executing ApertureProjectCreatorTask");
            final MailQueue emailQueue = ComponentAccessor.getMailQueue();
            if (!emailQueue.isSending()) {
                emailQueue.sendBuffer();
            }
        }
    }

    private boolean createApertureProject(final String issueKey, final Map<String, Serializable> environment) throws DataAccessException {
        final JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        final IssueManager issueManager = ComponentAccessor.getIssueManager();
        final MutableIssue projectIssue = issueManager.getIssueByKeyIgnoreCase(issueKey);
        final String projectKey = apertureSettings.getProjectKeyFor(projectIssue);
        final WorkflowConfiguration wfConfig = apertureSettings.getWorkflowConfiguration();

        context.setLoggedInUser(apertureSettings.getApertureUser());
        JiraUtils.assignIssue(apertureSettings.getApertureUser(), projectIssue);

        LOG.info(String.format("Creating project by issue-key:[%s] by:'%s'", issueKey, context.getLoggedInUser()));
        IssueInputParameters inputParameters = getWorkflowParameters();
        inputParameters.setComment(String.format("Starting work on creating software project resources for %s", projectKey));
        transitionIssue(apertureSettings.getApertureUser(), projectIssue, wfConfig.getStartAction(), inputParameters);

        ErrorCollection actionErrors;
        actionErrors = aperture.createProject(projectIssue, environment);
        if (handleActionErrors(actionErrors, projectIssue, projectKey)) {
            LOG.warn(String.format("Failed to provision project resources for project:%s ", projectKey));
            return false;
        }

        final Collection<ApplicationUser> users = apertureSettings.getUsersFor(projectIssue);
        actionErrors = aperture.modifyProjectUserPermissions(projectKey, Aperture.PermissionMode.ADD, Aperture.Role.DEVELOPER, users);
        if (handleActionErrors(actionErrors, projectIssue, projectKey)) {
            LOG.warn(String.format("Failed to modify user permissions for project:%s ", projectKey));
            return false;
        }

        final Collection<Group> groups = apertureSettings.getGroupsFor(projectIssue);
        actionErrors = aperture.modifyProjectGroupPermissions(projectKey, Aperture.PermissionMode.ADD, Aperture.Role.DEVELOPER, groups);
        if (handleActionErrors(actionErrors, projectIssue, projectKey)) {
            LOG.warn(String.format("Failed to modify group permissions for project:%s ", projectKey));
            return false;
        }

        LOG.info(String.format("finishing project provisioning on [%s/%s]", issueKey, projectKey));
        inputParameters = getWorkflowParameters();
        inputParameters.setComment(String.format("All software resources have been successfully provisioned for %s.", projectKey));
        inputParameters.setResolutionId(getResolutionStatus(wfConfig));
        inputParameters.setSkipScreenCheck(true);
        transitionIssue(apertureSettings.getApertureUser(), projectIssue, wfConfig.getFinishAction(), inputParameters);
        return true;
    }

    private boolean handleActionErrors(ErrorCollection actionErrors, MutableIssue projectDetails, String projectKey) {

        if (actionErrors.hasAnyErrors()) {
            final IssueInputParameters inputParameters = getWorkflowParameters();
            final WorkflowConfiguration wfConfig = apertureSettings.getWorkflowConfiguration();
            addErrorCollectionAsComments(projectDetails, projectKey, actionErrors);
            inputParameters.setSkipScreenCheck(true);
            inputParameters.setSkipLicenceCheck(true);
            inputParameters.setComment("An error has occurred while creating the software project resources and cannot be automatically completed. This issue has been triaged to support for manual intervention.");

            // auto-triage if the error collection has a 'field' error message which we will treat as which service
            // failed.
            final Map<String, String> serviceErrors = actionErrors.getErrors();
            final String failedServiceKey = CollectionUtil.first(serviceErrors.keySet());
            if (StringUtils.hasText(failedServiceKey)) {
                final ApertureSettings.ProjectService serviceEnum = ApertureSettings.ProjectService.valueOf(failedServiceKey);
                final ApplicationUser serviceLead = JiraUtils.getServiceLead(projectDetails, serviceEnum);
                if (Objects.nonNull(serviceLead)) {
                    inputParameters.setAssigneeId(serviceLead.getName());
                } else {
                    LOG.warn(String.format("Cannot auto-assign issue due to invalid component (no lead most likely?) for service %s", failedServiceKey));
                }
            }

            transitionIssue(apertureSettings.getApertureUser(), projectDetails, wfConfig.getTriageAction(), inputParameters);
            try {
                deliverCreationErrors(projectDetails, projectKey, actionErrors);
            } catch (IOException error) {
                LOG.warn("Failed to deliver project creation errors", error);
            }
            return true;
        }
        return false;
    }

    private boolean deliverCreationErrors(final Issue issue, final String projectKey, final ErrorCollection errors) throws IOException {

        final JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        final String subject = String.format("[Aperture] %s - provisioning failed for project : %s", issue.getKey(), projectKey);
        final Map<String, Object> overrides = new HashMap<>();
        final ApertureSettings settings = ComponentAccessor.getOSGiComponentInstanceOfType(ApertureSettings.class);
        final String emailAddress = settings.getApertureEmailAddress();
        if (StringUtils.hasText(emailAddress)) {
            overrides.put("projectKey", projectKey);
            overrides.put("i18n", ComponentAccessor.getI18nHelperFactory().getInstance(context.getLoggedInUser()));
            overrides.put("requester", issue.getReporter());
            overrides.put("errors", errors);
            overrides.put("issue", issue);
            deliverEmail(overrides, emailAddress, "/gov/pnnl/aperture/templates/email/project-creation-failed.vm.html", subject);
            return true;
        }
        return false;
    }

    private String getResolutionStatus(final WorkflowConfiguration wfConfig) {

        final Resolution finishStatus = wfConfig.getFinishStatus();
        if (finishStatus != null) {
            return finishStatus.getId();
        }
        final ResolutionManager resolutionManager = ComponentAccessor.getComponent(ResolutionManager.class);
        final Resolution defaultResolution = resolutionManager.getDefaultResolution();
        if (Objects.isNull(defaultResolution)) {
            LOG.warn("Default resolution for Aperture workflow is not configured. Please configure it via the ");
            return "";
        }
        return defaultResolution.getId();
    }
}
