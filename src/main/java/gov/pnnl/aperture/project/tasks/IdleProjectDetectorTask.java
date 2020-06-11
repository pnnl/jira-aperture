package gov.pnnl.aperture.project.tasks;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.scheduler.JobRunnerRequest;
import com.atlassian.scheduler.JobRunnerResponse;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

/**
 * JIRA scheduler task for detecting and notifying when projects are deemed <em>idle</em>.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public class IdleProjectDetectorTask extends AbstractAperturePluginJob {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(IdleProjectDetectorTask.class);
    /**
     * Reference to the installed OSGI instance of {@link ApertureSettings} in this JIRA instance.
     */
    private final ApertureSettings apertureSettings;
    /**
     * Reference to the installed OSGI instance of {@link Aperture} in this JIRA instance.
     */
    private final Aperture aperture;

    @Inject
    public IdleProjectDetectorTask(@NotNull final Aperture aperture, @NotNull final ApertureSettings apertureSettings) {

        this.apertureSettings = apertureSettings;
        this.aperture = aperture;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public JobRunnerResponse runJob(final JobRunnerRequest jobRunnerRequest) {

        LOG.info("Starting scan for idle projects.");
        final JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        context.setLoggedInUser(apertureSettings.getApertureUser());
        for (final Project project : projectManager.getProjects()) {
            aperture.detectProjectActivity(project.getKey());
        }
        return JobRunnerResponse.success();
    }

}
