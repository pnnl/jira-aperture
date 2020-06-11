package gov.pnnl.aperture.webwork.action;

import com.atlassian.core.util.DateUtils;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.PluginInformation;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import com.atlassian.scheduler.status.JobDetails;
import gov.pnnl.aperture.*;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Developer Central @ PNNL
 */
public class ApertureProjectConfigure extends JiraWebActionSupport {

    /**
     *
     */
    private static final transient Logger LOG = Logger.getLogger(ApertureProjectConfigure.class);
    /**
     *
     */
    private static final long serialVersionUID = -2012012988986893401L;
    /**
     *
     */
    private static final String PROJECT_KEY_ATTR = "com.atlassian.jira.projectconfig.util.ServletRequestProjectConfigRequestCache:project";
    /**
     *
     */
    private final Aperture aperture;
    /**
     *
     */
    private final ApertureSettings apertureSettings;
    /**
     *
     */
    private final ApertureScheduler apertureScheduler;
    /**
     *
     */
    private String projectKey = null;

    public ApertureProjectConfigure(final Aperture aperture, final ApertureSettings apertureSettings, final ApertureScheduler apertureScheduler) {

        Assert.notNull(aperture, "Invalid Aperture service reference.");
        Assert.notNull(apertureSettings, "Invalid Aperture Settings service reference.");
        Assert.notNull(apertureScheduler, "Invalid Aperture Scheduler service reference.");

        this.aperture = aperture;
        this.apertureScheduler = apertureScheduler;
        this.apertureSettings = apertureSettings;
    }

    public ApertureSettings getApertureSettings() {

        return apertureSettings;
    }

    public boolean isApertureEnabled() {

        final ApertureProjectSettings projectSettings = apertureSettings.getProjectSettings(getProjectKey());
        return projectSettings.isEnabled();
    }

    public PluginInformation getPluginInfo() {

        final PluginAccessor pluginAccessor = ComponentAccessor.getPluginAccessor();
        final Plugin plugin = pluginAccessor.getPlugin("gov.pnnl.aperture.jira-core");
        return plugin.getPluginInformation();
    }

    public boolean isScheduledForRemoval() {

        return getRemovalDetails() != null;
    }

    public String getRemovalDuration() {
        final JobDetails jd = getRemovalDetails();
        if (jd != null) {
            final Date nextRunTime = jd.getNextRunTime();
            if (nextRunTime != null) {
                final Date now = new Date();
                final long seconds = TimeUnit.MILLISECONDS.toSeconds(Math.abs(now.getTime() - nextRunTime.getTime()));
                return DateUtils.getDurationPretty(seconds, getResourceBundle());
            }
        }
        return "";
    }

    public String getRemovalDate() {

        final JobDetails jd = getRemovalDetails();
        if (jd != null) {
            final Date nextRunTime = jd.getNextRunTime();
            if (nextRunTime != null) {
                final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, getLocale());
                return df.format(nextRunTime);
            }
        }
        return "";
    }

    public Map<ApertureSettings.ProjectService, String> getProjectLinks() {

        return JiraUtils.getProjectLinks(new HashMap<>(), getProject());
    }

    public JobDetails getRemovalDetails() {

        return apertureScheduler.getJobDetailsForProject(ApertureScheduler.Operation.REMOVE, getProjectKey());
    }

    public MutableIssue getOriginalRequest() {

        return null;
    }

    public String getProjectKey() {

        return this.projectKey;
    }

    public void setProjectKey(final String projectKey) {

        this.projectKey = projectKey;
    }

    public Project getProject() {

        final String pk = getProjectKey();
        if (StringUtils.hasText(pk)) {
            final ProjectManager projectManager = ComponentAccessor.getProjectManager();
            return projectManager.getProjectObjByKeyIgnoreCase(pk);
        }
        return null;
    }

    public boolean isSystemAdmin() {

        final GlobalPermissionManager globalPermissionManager = ComponentAccessor.getGlobalPermissionManager();
        return globalPermissionManager.hasPermission(GlobalPermissionKey.SYSTEM_ADMIN, getLoggedInUser());
    }

    @Override
    public String execute() throws Exception {

        LOG.debug("processing:execute();");
        final ProjectManager projectManager = getProjectManager();
        final Project project = projectManager.getProjectObjByKey(this.projectKey);
        if (project == null) {
            LOG.debug(String.format("Invalid project key received(%s); redirecting to all projects view", this.projectKey));
            return getRedirect("secure/BrowseProjects.jspa#all");
        }
        LOG.debug(String.format("Valid project key (%s) received", project.getKey()));
        final HttpServletRequest httpRequest = getHttpRequest();
        httpRequest.setAttribute(PROJECT_KEY_ATTR, project);
        return super.execute();
    }

    public String doInstall() {

        LOG.debug("processing:doInstall();");
        final ProjectManager projectManager = getProjectManager();
        final Project project = projectManager.getProjectObjByKey(this.projectKey);
        this.aperture.installApertureSchemesFor(project);
        return getRedirect(String.format("/secure/admin/ProjectConfigureAperture.jspa?projectKey=%s", getProjectKey()));
    }

    @WebSudoRequired
    public String doAcl() {

        LOG.debug("processing:doAcl();");
        final HttpServletRequest httpRequest = getHttpRequest();
        final String pk = getProjectKey();
        final Aperture.Role role = Aperture.Role.valueOf(httpRequest.getParameter("role").toUpperCase());
        final Aperture.PermissionMode mode = Aperture.PermissionMode.valueOf(httpRequest.getParameter("mode").toUpperCase());
        final Collection<ApplicationUser> projectUsers = constructUserList(httpRequest.getParameter("projectUsers"));
        final Collection<Group> projectGroups = constructGroupList(httpRequest.getParameter("projectGroups"));

        final ErrorCollection errors = new SimpleErrorCollection();
        if (!projectUsers.isEmpty()) {
            LOG.debug(String.format("doAcl(project=%s, role=%s, mode=%s, users=%s);", pk, role, mode, Arrays.toString(projectUsers.toArray())));
            errors.addErrorCollection(this.aperture.modifyProjectUserPermissions(pk, mode, role, projectUsers));
        }

        if (!projectGroups.isEmpty()) {
            LOG.debug(String.format("doAcl(project=%s, role=%s, mode=%s, groups=%s);", pk, role, mode, Arrays.toString(projectGroups.toArray())));
            errors.addErrorCollection(this.aperture.modifyProjectGroupPermissions(pk, mode, role, projectGroups));
        }

        if (errors.hasAnyErrors()) {
            for (final String errorMessage : errors.getErrorMessages()) {
                LOG.error(errorMessage);
            }
        }
        return getRedirect(String.format("/secure/admin/ProjectConfigureAperture.jspa?projectKey=%s", getProjectKey()));
    }

    @WebSudoRequired
    public String doUndelete() {

        LOG.debug("processing:doUndelete();");
        final String jobKey = String.format("gov.pnnl.aperture:APR-REMOVE-%s", getProjectKey());
        LOG.debug(String.format("Un-scheduling project removal:%s", jobKey));
        apertureScheduler.cancelProjectDeletion(getProjectKey());
        return getRedirect(String.format("/secure/admin/ProjectConfigureAperture.jspa?projectKey=%s", getProjectKey()));
    }

    @WebSudoRequired
    public String doToggle() {

        final ApertureProjectSettings projectSettings = apertureSettings.getProjectSettings(getProjectKey());
        final boolean enabled = projectSettings.isEnabled();
        projectSettings.setEnabled(!enabled);
        return getRedirect(String.format("/secure/admin/ProjectConfigureAperture.jspa?projectKey=%s", getProjectKey()));
    }

    @WebSudoRequired
    public String doRemove() {

        LOG.debug("processing:doRemove();");
        final ProjectManager projectManager = getProjectManager();
        final Project project = projectManager.getProjectObjByKey(getProjectKey());
        this.apertureScheduler.scheduleProjectRemoval(project);
        return getRedirect(String.format("/secure/admin/ProjectConfigureAperture.jspa?projectKey=%s", getProjectKey()));
    }

    private Collection<Group> constructGroupList(final String groupList) {

        if (StringUtils.hasText(groupList)) {
            final UserManager userManager = getUserManager();
            final List<Group> projectGroups = new ArrayList<>();
            final String[] groupNames = StringUtils.delimitedListToStringArray(groupList, ",");
            for (final String groupName : groupNames) {
                final Group group = userManager.getGroup(groupName);
                if (group == null) {
                    continue;
                }
                projectGroups.add(group);
            }
            return projectGroups;
        }
        return Collections.emptyList();
    }

    private Collection<ApplicationUser> constructUserList(final String userList) {

        if (StringUtils.hasText(userList)) {
            final UserManager userManager = getUserManager();
            final List<ApplicationUser> projectUsers = new ArrayList<>();
            final String[] userNames = StringUtils.delimitedListToStringArray(userList, ",");
            for (final String userName : userNames) {
                final ApplicationUser applicationUser = userManager.getUserByName(userName.trim());
                if (applicationUser == null) {
                    LOG.warn(String.format("Failed to find user by name:'%s'", userName));
                    continue;
                }
                projectUsers.add(applicationUser);
            }
            return projectUsers;
        }
        return Collections.emptyList();
    }

}
