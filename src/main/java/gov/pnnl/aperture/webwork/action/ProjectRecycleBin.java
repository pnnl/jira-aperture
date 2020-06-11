package gov.pnnl.aperture.webwork.action;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.type.ProjectType;
import com.atlassian.jira.project.type.ProjectTypeManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.PluginInformation;
import com.atlassian.scheduler.status.JobDetails;
import gov.pnnl.aperture.ApertureScheduler;
import gov.pnnl.aperture.ApertureSettings;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class ProjectRecycleBin extends JiraWebActionSupport {
    /**
     * Reference to the current logger instance for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ProjectRecycleBin.class);

    private final ApertureScheduler apertureScheduler;
    private final ApertureSettings apertureSettings;

    public ProjectRecycleBin(final ApertureScheduler apertureScheduler, final ApertureSettings apertureSettings) {

        this.apertureScheduler = apertureScheduler;
        this.apertureSettings = apertureSettings;
    }

    public PluginInformation getPluginInfo() {

        return apertureSettings.getPluginInfo();
    }

    public ProjectType getProjectType(final Project project) {

        final ProjectTypeManager projectTypeManager = ComponentAccessor.getComponent(ProjectTypeManager.class);
        return projectTypeManager.getAccessibleProjectType(project.getProjectTypeKey()).getOrNull();
    }

    public Collection<Project> getProjects() {

        return apertureScheduler.getPendingProjectDeletions();
    }

    public JobDetails getJobDetailsFor(final String projectKey) {

        return apertureScheduler.getJobDetailsForProject(ApertureScheduler.Operation.REMOVE, projectKey);
    }

    public ApplicationUser getInitiatedBy(final JobDetails jobDetails) {

        final Map<String, Serializable> jobDetailsParameters = jobDetails.getParameters();
        final String initiatedBy = (String) jobDetailsParameters.get("username");
        final UserManager userManager = ComponentAccessor.getUserManager();
        return userManager.getUserByName(initiatedBy);
    }

    public Date getInitiatedOn(final JobDetails jobDetails) {

        final Map<String, Serializable> jobDetailsParameters = jobDetails.getParameters();
        final String initiatedOn = (String) jobDetailsParameters.get("initiatedOn");
        return new Date(Long.parseLong(initiatedOn));
    }

    public String doCancel() {

        final HttpServletRequest httpRequest = getHttpRequest();
        String projectKey = httpRequest.getParameter("projectKey");
        LOG.debug(String.format("processing:doCancel(%s))", projectKey));
        apertureScheduler.cancelProjectDeletion(projectKey);
        return getRedirect("/secure/admin/ApertureProjectRecycleBin.jspa");
    }
}
