package gov.pnnl.aperture;

import com.atlassian.annotations.PublicApi;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.project.Project;
import com.atlassian.plugin.PluginInformation;
import com.atlassian.plugin.StateAware;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.scheduler.status.JobDetails;

import javax.validation.constraints.NotNull;
import java.util.Collection;

/**
 * Service provider for having high-level functionality to JIRA's {@link com.atlassian.scheduler.SchedulerService}.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
@PublicApi
public interface ApertureScheduler extends StateAware, LifecycleAware {

    /**
     * Base key name for tracking {@link gov.pnnl.aperture.project.tasks.ApertureProjectCreatorTask} jobs.
     */
    String PROJECT_CREATOR_JOB_RUNNER_KEY = "aperture:gov.pnnl.project.creator.task";
    /**
     * Base key name for tracking {@link gov.pnnl.aperture.project.tasks.ApertureProjectRemovalTask} jobs.
     */
    String PROJECT_REMOVAL_JOB_RUNNER_KEY = "aperture:gov.pnnl.project.removal.task";
    /**
     * Base key name for tracking {@link gov.pnnl.aperture.project.tasks.IdleProjectDetectorTask} jobs.
     */
    String PROJECT_IDLE_DETECTION_JOB_RUNNER_KEY = "aperture:gov.pnnl.project.idle-detection.task";

    /**
     * Initiates a new job to create a new project based on a given issue request.
     * <p>
     * This method will schedule a new job instance of the
     * {@link gov.pnnl.aperture.project.tasks.ApertureProjectCreatorTask} to create the new project and available
     * connected services.
     *
     * @param issue a valid JIRA issue containing project creations parameters
     * @throws IllegalArgumentException if the issue provided is <em>null</em>.
     */
    void scheduleNewProject(@NotNull final Issue issue);

    /**
     * Initiates a new job to remove a project based on an existing JIRA project.
     * <p>
     * This method will schedule a new job instance of the
     * {@link gov.pnnl.aperture.project.tasks.ApertureProjectRemovalTask} to remove the project and available
     * connected services associated with it.
     * <p>
     * The scheduler will schedule this removal job with a delay dictated by the value set provided by
     * {@link ApertureSettings#getDeleteDuration()} this by default should be 2 weeks. Such that the job and project
     * deletion process can be <em>undone</em>
     *
     * @param project a valid JIRA project that is to removed from this JIRA instance and connected services.
     * @throws IllegalArgumentException if the project provided is <em>null</em>.
     * @see #cancelProjectDeletion(String)
     * @see ApertureSettings#getDeleteDuration()
     */
    void scheduleProjectRemoval(@NotNull final Project project);

    /**
     * Removes a project deletion job from the scheduler thus canceling the project removal.
     * <p>
     *
     * @param projectKey the unique key of the project to cancel the removal of.
     * @throws IllegalArgumentException if the projectKey is an <em>empty</em> value.
     */
    void cancelProjectDeletion(@NotNull final String projectKey);

    /**
     * Gets job scheduler details based on the operation and project key provided.
     * <p>
     *
     * @param operation  the operation being performed on the project.
     * @param projectKey unique project key to query job details about.
     * @return <code>null</code> if no job details exist for the operation and project key.
     * @throws IllegalArgumentException if either projectKey or operation provided are <em>empty</em> values.
     */
    JobDetails getJobDetailsForProject(final Operation operation, final String projectKey);

    /**
     * @return
     */
    Collection<Project> getPendingProjectDeletions();

    /**
     * Gets the plug-in information for this instance of Aperture in JIRA.
     * <p>
     * Plug-in information that can be useful for debugging or just general informational output.
     *
     * @return plug-in information about the current running version of Aperture.
     */
    PluginInformation getPluginInfo();


    /**
     * Enumeration of logical scheduler operations allowed for methods defined by the enclosing class.
     */
    enum Operation {

        /**
         * Removes an existing scheduled job from running
         */
        REMOVE,
        /**
         * Adds a new job to be scheduled.
         */
        ADD
    }
}
