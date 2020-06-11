package gov.pnnl.aperture.project;

import com.atlassian.annotations.PublicSpi;
import com.atlassian.core.util.DateUtils;
import com.atlassian.core.util.InvalidDurationException;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.plugin.StateAware;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.scheduler.JobRunner;
import com.atlassian.scheduler.SchedulerService;
import com.atlassian.scheduler.SchedulerServiceException;
import com.atlassian.scheduler.config.*;
import com.atlassian.scheduler.status.JobDetails;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureScheduler;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.project.tasks.ApertureProjectCreatorTask;
import gov.pnnl.aperture.project.tasks.ApertureProjectRemovalTask;
import gov.pnnl.aperture.project.tasks.IdleProjectDetectorTask;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Developer Central @ PNNL
 */
@PublicSpi
@Named("Aperture Scheduling Component")
@ExportAsService({ApertureScheduler.class, LifecycleAware.class, StateAware.class})
public class PnnlApertureScheduler extends AbstractAperturePlugin implements ApertureScheduler {

    private static final transient Logger LOG = Logger.getLogger(PnnlApertureScheduler.class);
    private final ApertureSettings apertureSettings;
    private final Aperture aperture;
    private final SchedulerService schedulerService;

    @Inject
    public PnnlApertureScheduler(@ComponentImport final PluginSettingsFactory settingsFactory, @ComponentImport final SchedulerService schedulerService, final Aperture aperture, final ApertureSettings apertureSettings) {

        super(settingsFactory);
        Assert.notNull(aperture, "Aperture reference cannot be null.");
        Assert.notNull(apertureSettings, "ApertureSettings reference cannot be null.");
        Assert.notNull(schedulerService, "SchedulerService reference cannot be null.");
        this.aperture = aperture;
        this.apertureSettings = apertureSettings;
        this.schedulerService = schedulerService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart() {

        JobRunner jobRunner = new ApertureProjectCreatorTask(aperture, apertureSettings);
        schedulerService.registerJobRunner(JobRunnerKey.of(PROJECT_CREATOR_JOB_RUNNER_KEY), jobRunner);

        jobRunner = new ApertureProjectRemovalTask();
        schedulerService.registerJobRunner(JobRunnerKey.of(PROJECT_REMOVAL_JOB_RUNNER_KEY), jobRunner);

        jobRunner = new IdleProjectDetectorTask(aperture, apertureSettings);
        schedulerService.registerJobRunner(JobRunnerKey.of(PROJECT_IDLE_DETECTION_JOB_RUNNER_KEY), jobRunner);

        startIdleProjectDetection();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {

        schedulerService.unregisterJobRunner(JobRunnerKey.of(PROJECT_CREATOR_JOB_RUNNER_KEY));
        schedulerService.unregisterJobRunner(JobRunnerKey.of(PROJECT_REMOVAL_JOB_RUNNER_KEY));
        schedulerService.unregisterJobRunner(JobRunnerKey.of(PROJECT_IDLE_DETECTION_JOB_RUNNER_KEY));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void scheduleNewProject(@NotNull final Issue issue) {

        Assert.notNull(issue, "Cannot Schedule a new project creation task with a null JIRA issue.");
        final JobRunnerKey jrk = JobRunnerKey.of(PROJECT_CREATOR_JOB_RUNNER_KEY);
        final Map<String, Serializable> environment = new HashMap<>();
        environment.put("issue-key", issue.getKey());

        JobConfig jobConfig = JobConfig.forJobRunnerKey(jrk);
        jobConfig = jobConfig.withParameters(environment);
        jobConfig = jobConfig.withRunMode(RunMode.RUN_LOCALLY);
        jobConfig = jobConfig.withSchedule(Schedule.runOnce(new Date()));
        try {
            LOG.debug(String.format("Scheduling new project creation: %s", new JSONObject(jobConfig.getParameters())));
            schedulerService.scheduleJobWithGeneratedId(jobConfig);
        } catch (SchedulerServiceException e) {
            LOG.error("Failed to schedule new project job with generated ID", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scheduleProjectRemoval(@NotNull final Project project) {

        Assert.notNull(project, "Cannot Schedule project removal task with a null JIRA project.");
        final JobRunnerKey jrk = JobRunnerKey.of(PROJECT_REMOVAL_JOB_RUNNER_KEY);
        final Map<String, Serializable> environment = new HashMap<>();

        final JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        final ApplicationUser user = context.getLoggedInUser();

        environment.put("project-key", project.getKey());
        environment.put("username", user.getUsername());
        environment.put("initiatedOn", Long.toString(System.currentTimeMillis()));
        LOG.info(String.format("Scheduling project removal for key:'%s' by user:'%s'", project.getKey(), user));

        final String deleteDuration = apertureSettings.getDeleteDuration();
        long delayInSeconds;
        try {
            delayInSeconds = DateUtils.getDuration(deleteDuration);
        } catch (InvalidDurationException error) {
            try {
                delayInSeconds = DateUtils.getDuration("2w");
            } catch (InvalidDurationException fallbackError) {
                throw new RuntimeException(fallbackError);
            }
        }
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delayInSeconds));

        JobConfig jobConfig = JobConfig.forJobRunnerKey(jrk);
        jobConfig = jobConfig.withParameters(environment);
        jobConfig = jobConfig.withRunMode(RunMode.RUN_LOCALLY);
        jobConfig = jobConfig.withSchedule(Schedule.runOnce(c.getTime()));
        try {
            schedulerService.scheduleJobWithGeneratedId(jobConfig);
        } catch (SchedulerServiceException e) {
            LOG.error("Failed to schedule new project job with generated ID", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelProjectDeletion(@NotNull String projectKey) {

        Assert.hasText(projectKey, "Cannot cancel project deletion for an empty project key.");
        final JobDetails jobDetails = getJobDetailsForProject(Operation.REMOVE, projectKey);
        if (jobDetails != null) {
            final JobId jobId = jobDetails.getJobId();
            LOG.info(String.format("Cancelling project deletion by key:'%s' job-id:'%s'", projectKey, jobId));
            schedulerService.unscheduleJob(jobId);
        } else {
            LOG.warn(String.format("Failed to cancel project deletion; could not find job for key:'%s'", projectKey));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobDetails getJobDetailsForProject(final Operation operation, final String projectKey) {

        Assert.hasText(projectKey, "Cannot query project job details with an empty project key.");
        Assert.notNull(operation, "Cannot query project job details with a null operation value.");
        final JobRunnerKey jobKey;
        switch (operation) {
            case ADD:
                jobKey = JobRunnerKey.of(PROJECT_CREATOR_JOB_RUNNER_KEY);
                break;
            case REMOVE:
                jobKey = JobRunnerKey.of(PROJECT_REMOVAL_JOB_RUNNER_KEY);
                break;
            default:
                jobKey = null;
                break;
        }

        final List<JobDetails> jobsByJobRunnerKey = schedulerService.getJobsByJobRunnerKey(jobKey);
        for (final JobDetails jobDetails : jobsByJobRunnerKey) {
            final Map<String, Serializable> environment = jobDetails.getParameters();
            final String pk = String.valueOf(environment.getOrDefault("project-key", null));
            if (projectKey.equals(pk)) {
                LOG.debug(String.format("Found existing removal task for project:'%s'", projectKey));
                return jobDetails;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Project> getPendingProjectDeletions() {

        final Collection<Project> projectList = new ArrayList<>();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();

        final JobRunnerKey jobRunnerKey = JobRunnerKey.of(PROJECT_REMOVAL_JOB_RUNNER_KEY);
        final List<JobDetails> jobsByJobRunnerKey = schedulerService.getJobsByJobRunnerKey(jobRunnerKey);
        for (final JobDetails jobDetails : jobsByJobRunnerKey) {
            final Map<String, Serializable> environment = jobDetails.getParameters();
            final String pk = (String) environment.getOrDefault("project-key", null);
            if (StringUtils.hasText(pk)) {
                projectList.add(projectManager.getProjectByCurrentKey(pk));
            }
        }
        return projectList;
    }

    private void startIdleProjectDetection() {

        final Calendar c = Calendar.getInstance();
        final JobRunnerKey jobRunnerKey = JobRunnerKey.of(PROJECT_IDLE_DETECTION_JOB_RUNNER_KEY);
        final String deleteDuration = apertureSettings.getDeleteDuration();
        long delayInSeconds;
        try {
            delayInSeconds = DateUtils.getDuration(deleteDuration);
        } catch (InvalidDurationException error) {
            try {
                delayInSeconds = DateUtils.getDuration("2w");
            } catch (InvalidDurationException fallbackError) {
                throw new RuntimeException(fallbackError);
            }
        }
        JobConfig jobConfig = JobConfig.forJobRunnerKey(jobRunnerKey);
        jobConfig = jobConfig.withRunMode(RunMode.RUN_LOCALLY);
        jobConfig = jobConfig.withSchedule(Schedule.forInterval(TimeUnit.SECONDS.toMillis(delayInSeconds), c.getTime()));
        try {
            schedulerService.scheduleJobWithGeneratedId(jobConfig);
        } catch (SchedulerServiceException e) {
            LOG.error("Failed to schedule new project job with generated ID", e);
            throw new RuntimeException(e);
        }
    }
}
