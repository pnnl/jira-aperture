package gov.pnnl.aperture.project.tasks;

import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.mail.queue.MailQueue;
import com.atlassian.scheduler.JobRunnerRequest;
import com.atlassian.scheduler.JobRunnerResponse;
import com.atlassian.scheduler.config.JobConfig;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Developer Central @ PNNL
 */
public class ApertureProjectRemovalTask extends AbstractAperturePluginJob {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ApertureProjectRemovalTask.class);

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public JobRunnerResponse runJob(final JobRunnerRequest jobRunnerRequest) {

        final JobConfig jobConfig = jobRunnerRequest.getJobConfig();
        final Map<String, Serializable> environment = jobConfig.getParameters();

        final Aperture aperture = ComponentAccessor.getOSGiComponentInstanceOfType(Aperture.class);
        final String requestor = (String) environment.get("username");
        final String projectKey = (String) environment.get("project-key");
        LOG.info(String.format("Beginning project removal for key:[%s]; requested by:%s", projectKey, requestor));
        try {
            final UserManager userManager = ComponentAccessor.getUserManager();
            final JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
            context.setLoggedInUser(userManager.getUserByName("admin"));

            final Collection<ApplicationUser> projectMembers = aperture.getProjectMembers(projectKey, false);
            final ErrorCollection removeProject = aperture.removeProject(projectKey, environment);
            if (removeProject.hasAnyErrors()) {
                deliverRemovalErrors(userManager.getUserByName(requestor), projectKey, removeProject);
            }
            sendEmailNotification(environment, projectMembers);
            return JobRunnerResponse.success("");
        } catch (IOException ex) {
            LOG.error(ex);
            return JobRunnerResponse.failed(ex);
        } finally {
            LOG.info(String.format("Finished executing project removal:[%s]", projectKey));
            final MailQueue emailQueue = ComponentAccessor.getMailQueue();
            if (!emailQueue.isSending()) {
                emailQueue.sendBuffer();
            }
        }
    }

    private void sendEmailNotification(final Map<String, Serializable> environment, final Collection<ApplicationUser> projectMembers) throws IOException {

        final String projectKey = (String) environment.get("project-key");
        final UserManager userManager = ComponentAccessor.getUserManager();
        final ApplicationUser requestor = userManager.getUserByName((String) environment.get("username"));

        for (final ApplicationUser user : projectMembers) {
            final Map<String, Object> overrides = new HashMap<>();
            overrides.put("projectKey", projectKey);
            overrides.put("i18n", ComponentAccessor.getI18nHelperFactory().getInstance(user));
            overrides.put("recipient", user);
            final String initialDateString = (String) environment.get("initiatedOn");
            if (StringUtils.hasText(initialDateString)) {
                try {
                    overrides.put("initiatedOn", new Date(Long.valueOf(initialDateString)));
                } catch (NumberFormatException nfe) {
                    LOG.warn(String.format("Failed to put initiated date:'%s' in the email context", initialDateString), nfe);
                }
            }
            overrides.put("actionUser", requestor);
            final String subject = String.format("Confirmation of Project [%s] removal from Developer Central", overrides.get("projectKey"));
            deliverEmail(overrides, user.getEmailAddress(), "/gov/pnnl/aperture/templates/email/project-removed.vm.html", subject);
        }
    }

    private boolean deliverRemovalErrors(final ApplicationUser who, final String projectKey, final ErrorCollection errors) throws IOException {

        final JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        final String subject = String.format("[Aperture] Service removal errors for project:%s", projectKey);
        final Map<String, Object> overrides = new HashMap<>();
        final ApertureSettings settings = ComponentAccessor.getOSGiComponentInstanceOfType(ApertureSettings.class);
        final String emailAddress = settings.getApertureEmailAddress();

        overrides.put("projectKey", projectKey);
        overrides.put("i18n", ComponentAccessor.getI18nHelperFactory().getInstance(context.getLoggedInUser()));
        overrides.put("requester", who);
        overrides.put("errors", errors);
        deliverEmail(overrides, emailAddress, "/gov/pnnl/aperture/templates/email/project-removal-errors.vm.html", subject);
        return true;
    }


}
