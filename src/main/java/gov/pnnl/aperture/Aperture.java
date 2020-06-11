package gov.pnnl.aperture;

import com.atlassian.annotations.PublicApi;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.workflow.AssignableWorkflowScheme;
import com.atlassian.plugin.PluginInformation;
import com.atlassian.plugin.StateAware;
import com.atlassian.sal.api.lifecycle.LifecycleAware;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Primary plug-in interface for providing configuration functionality and common functions for Aperture for JIRA.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
@PublicApi
public interface Aperture extends StateAware, LifecycleAware {

    PluginInformation getPluginInfo();

    Collection<ApplicationUser> getProjectMembers(final String projectKey, final boolean excludeProjectLead);

    ProjectServiceHandler getServiceHandler(final ApertureSettings.ProjectService projectService);

    ErrorCollection createProject(final MutableIssue issue, final Map<String, Serializable> environment);

    ErrorCollection removeProject(final String projectKey, final Map<String, Serializable> environment);

    ErrorCollection modifyProjectUserPermissions(final String projectKey, final PermissionMode mode, final Role role, final Collection<ApplicationUser> users);

    ErrorCollection modifyProjectGroupPermissions(final String projectKey, final PermissionMode mode, final Role role, final Collection<Group> users);

    void detectProjectActivity(final String projectKey);

    /**
     * Gets the default Aperture project where the schemes are installed.
     * <p>
     * This is an based on an internal property setting when the method {@link #installApertureSchemesFor(Project)} is
     * used. The last project that is invoked with this method is considered <em>THE</em> Aperture project. This allows
     * processes that are not operating in the context of the project itself to still phone home to it.
     *
     * @return JIRA project reference of the Aperture JIRA project.
     */
    Project getProject();

    /**
     * Checks to see if a given project reference has been marked as <em>idle</em>.
     *
     * @param projectKey the given project reference to see if it has been marked as <em>idle</em>.
     * @return <code>true</code> if the given project has been previously marked as <em>idle</em>
     */
    boolean isProjectIdle(final String projectKey);

    /**
     * Installs and replaces existing schemes for the given project with Aperture ones.
     * <p>
     * Installs the values for the following schemes into the project and replacing what is currently associated with
     * the project. This method should only be called on new or empty projects. Adverse effects could occur with
     * projects that have existing issues.
     * <p>
     * This method is intended to kick-start Aperture usage and facilitate easier setup and testing of Aperture
     * within JIRA.
     *
     * @param project the project to install new schemes for
     * @throws IllegalArgumentException if the project provided is <code>null</code>.
     * @see #getWorkflowScheme()
     * @see #getFieldConfigurationScheme()
     * @see #getIssueTypeScheme()
     * @see #getIssueTypeScreenScheme()
     */

    void installApertureSchemesFor(final Project project);

    /**
     * Gets the Aperture workflow scheme that is automatically installed with this add-on.
     * <p>
     * When this add-on is first run it installs a custom workflow scheme to be installed on a JIRA based project in
     * order to work. This allows the retrieval of the initial workflow that is installed when the add-on is initially
     * installed into the host JIRA system.
     *
     * @return the workflow scheme installed with this add-on
     * @see gov.pnnl.aperture.updates.ApertureJiraInstallTask
     */
    AssignableWorkflowScheme getWorkflowScheme();

    /**
     * Gets the Aperture field configuration scheme that is automatically installed with this add-on.
     * <p>
     * When this add-on is first run it installs a custom field configuration scheme to be installed on a JIRA based
     * project in order to work. This allows the retrieval of the initial scheme that is installed when the add-on is
     * initially installed into the host JIRA system.
     *
     * @return the field configuration scheme installed with this add-on
     * @see gov.pnnl.aperture.updates.ApertureJiraInstallTask
     */
    FieldConfigScheme getIssueTypeScheme();

    /**
     * Gets the Aperture screen scheme configuration scheme that is automatically installed with this add-on.
     * <p>
     * When this add-on is first run it installs a custom screen configuration scheme to be installed on a JIRA based
     * project in order to work. This allows the retrieval of the initial scheme that is installed when the add-on is
     * initially installed into the host JIRA system.
     *
     * @return the screen scheme configuration scheme installed with this add-on
     * @see gov.pnnl.aperture.updates.ApertureJiraInstallTask
     */
    IssueTypeScreenScheme getIssueTypeScreenScheme();

    /**
     * Gets the Aperture field configuration scheme that is automatically installed with this add-on.
     * <p>
     * When this add-on is first run it installs a custom field configuration scheme to be installed on a JIRA based
     * project in order to work. This allows the retrieval of the initial scheme that is installed when the add-on is
     * initially installed into the host JIRA system.
     *
     * @return the screen scheme configuration scheme installed with this add-on
     * @see gov.pnnl.aperture.updates.ApertureJiraInstallTask
     */
    FieldLayoutScheme getFieldConfigurationScheme();

    enum PermissionMode {

        /**
         *
         */
        ADD,
        /**
         *
         */
        REMOVE,
        /**
         *
         */
        REPLACE
    }

    enum Role {

        /**
         *
         */
        USER("Users"),
        /**
         *
         */
        MANAGER("Managers"),
        /**
         *
         */
        QUALITY_ASSURANCE("QA"),
        /**
         *
         */
        DEVELOPER("Developers"),
        /**
         *
         */
        ADMIN("Administrators");

        private final String jiraRoleName;

        Role(final String jiraRoleName) {

            this.jiraRoleName = jiraRoleName;
        }

        public ProjectRole getProjectRole() {

            final ProjectRoleManager manager = ComponentAccessor.getComponentOfType(ProjectRoleManager.class);
            return manager.getProjectRole(this.jiraRoleName);
        }
    }
}
