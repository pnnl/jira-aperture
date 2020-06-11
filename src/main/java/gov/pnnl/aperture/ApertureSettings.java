package gov.pnnl.aperture;

import com.atlassian.annotations.PublicApi;
import com.atlassian.applinks.api.ApplicationId;
import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.core.util.DateUtils;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.project.type.ProjectType;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.PluginInformation;
import com.atlassian.plugin.StateAware;
import com.atlassian.sal.api.lifecycle.LifecycleAware;

import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Service definition for providing high-level configuration data for Aperture.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
@PublicApi
public interface ApertureSettings extends StateAware, LifecycleAware {

    /**
     * Default delete duration for delaying the removal of project resources using Aperture.
     * <p>
     *
     * @see #getDeleteDuration()
     */
    String DEFAULT_DELETE_DURATION = "2w";

    /**
     * Default duration allowed for project inactivity before it is considered <em>idle</em>.
     * <p>
     *
     * @see #getProjectIdleDuration()
     */
    String DEFAULT_PROJECT_IDLE_DURATION = "90d";

    /**
     * Default interval duration for how often idle project detection task is executed.
     * <p>
     *
     * @see #getProjectIdleInterval()
     */
    String DEFAULT_PROJECT_IDLE_INTERVAL = "1w";

    /**
     * Gets the general plug-in information including version, description, and other meta-data about Aperture.
     * <p>
     *
     * @return the current plug-in meta-data information about Aperture.
     */
    PluginInformation getPluginInfo();

    /**
     * Gets a distinct set of users from a given a project creation issue.
     * <p>
     * This method is intended to capture and provide a distinct list of {@link ApplicationUser} for all of the users
     * and including users in the groups that were provided as part of the issue provided to this method.
     * <p>
     * This collection of users will not contain any duplicate users. If the issue type is not a valid Aperture project
     * creation issue type or the issue for whatever reason does not have the {@link #getCustomField(CustomField)} for
     * users or groups this method will simply return an empty collection.
     *
     * @param issue the project creation request issue.
     * @return collection of users that are reference in the provided issue.
     */
    Collection<ApplicationUser> getProjectMembersFor(final Issue issue);

    /**
     * Gets the appropriate JIRA {@link ProjectType} for a given issue.
     * <p>
     * This method is should encapsulate the mapping of issue types to project types based on how Aperture is
     * configured. The project type is required for Aperture to properly create project spaces within JIRA.
     *
     * @param issue a valid JIRA issue that represents a project creation request.
     * @return the designated project type for the give issue type.
     */
    ProjectType getProjectTypeFor(final Issue issue);

    /**
     * Gets the current JIRA work flow configuration for this Aperture instance.
     * <p>
     *
     * @return new instance of a work flow configuration and it's properties for the current Aperture instance.
     */
    WorkflowConfiguration getWorkflowConfiguration();

    /**
     * Sets the JIRA work flow configuration with a new work flow and appropriate actions values.
     * <p>
     *
     * @param workflowConfiguration the new work flow configuration to save.
     */
    void setWorkflowConfiguration(final WorkflowConfiguration workflowConfiguration);

    /**
     * Gets a list of all available application links currently installed in the current JIRA system.
     * <p>
     *
     * @return list of all configured application links for this JIRA instance.
     */
    List<ApplicationLink> getSystemApplicationLinks();

    /**
     * Gets the associated JIRA application link for the logical Aperture project service.
     * <p>
     *
     * @param service the logical Aperture project service to look up the application link for.
     * @return the associated application link for the service; can be <code>null</code> if not associated.
     * @throws IllegalArgumentException if the service argument provided is <code>null</code>.
     * @see #getSystemApplicationLinks()
     */
    ApplicationLink getApplicationLink(final ProjectService service);

    /**
     * Associates a new project service to a JIRA Application Link ID.
     * <p>
     *
     * @param service         the logical project service to associate with the application link.
     * @param applicationLink the JIRA {@link ApplicationLink} ID to associate; can be <code>null</code>.
     * @throws IllegalArgumentException if the project service argument provided is <code>null</code>.
     * @see #getApplicationLink(gov.pnnl.aperture.ApertureSettings.ProjectService)
     * @see #getSystemApplicationLinks()
     */
    void setApplicationLink(final ProjectService service, final ApplicationId applicationLink);

    /**
     * Gets a collection of properties and respective values required to make a project service work properly.
     * <p>
     * This configuration is typically used for project defaults for the given service and or extra configuration that
     * may be required for connecting to the service in question.
     *
     * @param projectService the logical project service to get a configuration object for.
     * @param projectType    the JIRA project type of project for the service.
     * @return configuration object of key value pairs for the given project service.
     * @throws IllegalArgumentException if the project service provided is <code>null</code>.
     */
    Map<String, Object> getServiceConfiguration(final ProjectService projectService, final String projectType);

    /**
     * Sets the configuration for a project service with the following key value pairs.
     * <p>
     *
     * @param projectService the logical project service to get a configuration object for.
     * @param projectType    the JIRA project type of project for the service.
     * @param configuration  configuration object of key value pairs for the given project service.
     */
    void setServiceConfiguration(final ProjectService projectService, final String projectType, final Map<String, Object> configuration);

    /**
     * Creates or modifies the Aperture logical custom field mapping to the actual JIRA custom field.
     * <p>
     *
     * @param cf          the reference to the Aperture logical custom field to associate with the JIRA custom field.
     * @param customField reference to the JIRA custom field; can be <code>null</code> to disassociate.
     * @throws IllegalArgumentException if the logical Aperture custom field provided is <code>null</code>.
     * @see #getCustomField(gov.pnnl.aperture.ApertureSettings.CustomField)
     */
    void setCustomField(final CustomField cf, final com.atlassian.jira.issue.fields.CustomField customField);

    /**
     * Utility method to look-up the JIRA {@link com.atlassian.jira.issue.fields.CustomField}.
     * <p>
     * The logical Aperture custom fields are mapped to internal JIRA custom fields. This method provides a common
     * facility for looking them up in the current application context.
     *
     * @param customField the logical Aperture custom field to look up a JIRA custom field for.
     * @return the JIRA custom field for the logical Aperture custom field; can be <code>null</code> if not set
     * @throws IllegalArgumentException if the logical Aperture custom field provided is <code>null</code>.
     * @see #setCustomField(gov.pnnl.aperture.ApertureSettings.CustomField, com.atlassian.jira.issue.fields.CustomField)
     */
    com.atlassian.jira.issue.fields.CustomField getCustomField(final CustomField customField);

    /**
     * Utility method for getting the project key value from a given issue.
     * <p>
     * This method will query the aperture service for the mapped custom field key {@link CustomField#PROJECT_KEY} to
     * get the value out of the issue provided.
     *
     * @param issue that contains the project key value as a custom field.
     * @return the project key value from the issue provided.
     * @throws IllegalArgumentException if the issue provided is <code>null</code>
     */
    String getProjectKeyFor(final Issue issue);

    /**
     * Utility method for getting the project category value from a given issue.
     * <p>
     * This method will query the aperture service for the mapped custom field key {@link CustomField#CATEGORY} to get
     * the value out of the issue provided.
     *
     * @param issue that contains the project key value as a custom field.
     * @return the project key value from the issue provided.
     * @throws IllegalArgumentException if the issue provided is <code>null</code>
     */
    String getCategoryFor(final Issue issue);

    /**
     * Utility method for getting the project user list value from a given issue.
     * <p>
     * This method will query the aperture service for the mapped custom field key {@link CustomField#USERS} to get the
     * value out of the issue provided.
     * <p>
     * In most cases these users should have read/write access to the project service being created. It does not
     * currently imply that these users have administrative privileges on the service as well.
     *
     * @param issue that contains the user list value as a custom field
     * @return the collection of users who should have access to the project service.
     * @throws IllegalArgumentException if the issue provided is <code>null</code>
     * @throws ClassCastException       if the custom field value is not actually an instance of {@link Collection}
     */
    Collection<ApplicationUser> getUsersFor(final Issue issue);

    /**
     * Utility method for getting the project group list value from a given issue.
     * <p>
     * This method will query the aperture service for the mapped custom field key {@link CustomField#GROUPS} to get the
     * value out of the issue provided.
     * <p>
     * In most cases these groups of users should be granted read/write access to the project service being created. It
     * does not currently imply that these user groups should also have administrative privileges on the service as
     * well.
     *
     * @param issue that contains the project group list value as a custom field
     * @return the collection of user groups who should have access to the project service.
     * @throws IllegalArgumentException if the issue provided is <code>null</code>
     * @throws ClassCastException       if the custom field value is not actually an instance of {@link Collection}
     */
    Collection<Group> getGroupsFor(final Issue issue);

    /**
     * Gets the designated user for the Aperture code to impersonate as for performing tasks.
     * <p>
     * In most situations the Aperture code is not run in the context of a user. Even if it is, the Aperture code needs
     * elevated privileges to do it's work. The user provided by this method is the JIRA user that Aperture code will
     * use and impersonate when performing functionality that requires elevated privileges; or when there is no user
     * context at all.
     *
     * @return valid JIRA user with administrative privileges to perform operations.
     */
    ApplicationUser getApertureUser();

    /**
     * Sets the Aperture user as new value with a given {@link ApplicationUser} from the JIRA system.
     * <p>
     *
     * @param apertureUser the user context in which Aperture operations will operate as.
     */
    void setApertureUser(final ApplicationUser apertureUser);

    /**
     * Gets the designated email address for internal Aperture notifications to be sent to.
     * <p>
     * There can be various internal events only to Aperture when an email notification is appropriate. Most appropriate
     * to a JIRA administrative mailing list.
     * <p>
     * The most common usage of this email is to send notifications of critical failures of Aperture code to JIRA
     * administrators.
     *
     * @return valid JIRA user with administrative privileges to perform operations.
     */
    String getApertureEmailAddress();

    /**
     * Sets the designated email address for email notifications for Aperture operations with a new value.
     * <p>
     *
     * @param emailAddress a valid email address for sending internal notifications from the Aperture code.
     * @throws IllegalArgumentException if the email address provided is <em>null</em> or invalid.
     */
    void setApertureEmailAddress(final String emailAddress);

    /**
     * Gets the current delete duration for this instance of Aperture.
     * <p>
     * When a project and all of it's associated resources need to be removed using Aperture. Aperture will use the
     * duration string <em>2w, 1h, 36h</em> etc. as a delay before the actual removal takes place. This allows for the
     * removal to be <strong>undone</strong> if it was removed in error by a user.
     * <p>
     * This value should default to {@link  #DEFAULT_DELETE_DURATION} when it is not previously set.
     *
     * @return the give date duration value for delaying the deletion of project resources via Aperture.
     * @see DateUtils#getDuration(String)
     */
    String getDeleteDuration();

    /**
     * Sets the delete <em>delay</em> duration for this Aperture instance with a new value.
     * <p>
     *
     * @param deleteDuration a valid date duration in the Atlassian style of 1w, 2h, 3d.
     * @see DateUtils
     */
    void setDeleteDuration(final String deleteDuration);

    /**
     * Gets the project settings for a given project key.
     * <p>
     *
     * @param projectKey the project key to get the {@link ApertureProjectSettings} for.
     * @return Aperture project settings for the given project key; if any. Can be <code>null</code>.
     */
    ApertureProjectSettings getProjectSettings(final String projectKey);

    /**
     * Gets the current project idle duration for this instance of Aperture.
     * <p>
     * A project will be considered idle when there is no activity from the current date minus the date duration defined
     * by this methond. So a duration of '26w' is effectively project not generating activity the last 26 weeks or 6
     * months.
     * <p>
     * This value should default to {@link  #DEFAULT_PROJECT_IDLE_DURATION} when it is not previously set.
     *
     * @return the given date duration value for detecting idle projects via Aperture.
     * @see DateUtils#getDuration(String)
     */
    String getProjectIdleDuration();

    /**
     * Sets the data duration a project is considered <em>idle</em> for this Aperture instance with a new value.
     * <p>
     * Setting an interval duration to a new value of <code>null</code>. Will result in a default duration of
     * {@link #DEFAULT_PROJECT_IDLE_DURATION}
     *
     * @param idleDuration a valid date duration in the Atlassian style of 1w, 2h, 3d.
     * @see DateUtils
     */
    void setProjectIdleDuration(final String idleDuration);

    /**
     * Gets the current project idle interval for this instance of Aperture.
     * <p>
     * This is the interval at which project idle detection will run within the background scheduler service. The value
     * returned here can be a duration string e.g. '2w' to run every 2 weeks. Otherwise a CRON expression for more
     * elaborate scheduling times.
     * <p>
     * This value will default to the {@link  #DEFAULT_PROJECT_IDLE_INTERVAL} value when it is has not been set.
     *
     * @return the given date duration value for detecting idle projects via Aperture.
     * @see DateUtils#getDuration(String)
     */
    String getProjectIdleInterval();

    /**
     * Sets the interval for detecting idle projects with a new value.
     * <p>
     * Setting an interval duration to a new value of <code>null</code>. Will result in a default duration of
     * {@link #DEFAULT_PROJECT_IDLE_INTERVAL}
     *
     * @param intervalDuration a valid date duration in the Atlassian style of 1w, 2h, 3d or CRON expression.
     * @see DateUtils
     */
    void setProjectIdleInterval(final String intervalDuration);

    /**
     * Gets a properly namespaced plug-in setting based on a given key.
     * <p>
     * Implementations should eventually persist to {@link com.atlassian.sal.api.pluginsettings.PluginSettings} however
     * this allows for caching, name interception and other functions to ensure access to plug-in settings for Aperture
     * are consistent.
     *
     * @param pluginSettingKey the logical name of the plug-in setting to get.
     * @return text value of the given plug-in setting based on the key provided.
     * @throws IllegalArgumentException if the provided <code>pluginSettingKey</code> is null.
     */
    String getPluginSetting(final @NotNull String pluginSettingKey);

    /**
     * Removes a properly namespaced plug-in setting based on a given key.
     * <p>
     * Implementations should eventually persist to {@link com.atlassian.sal.api.pluginsettings.PluginSettings} however
     * this allows for caching, name interception and other functions to ensure access to plug-in settings for Aperture
     * are consistent.
     * <p>
     * Implementations should also consider a <code>null</code> for <code>pluginSettingValue</code> as a remove action
     * and can delegate to {@link #removePluginSetting(String)} or handle it internally as needed.
     *
     * @param pluginSettingKey   the logical name of the plug-in setting to change it's value.
     * @param pluginSettingValue the new value of the plug-in setting for the associated key.
     * @return previous text value of the given plug-in setting based on the key provided; can be <code>null</code>.
     * @throws IllegalArgumentException if the provided <code>pluginSettingKey</code> is null.
     */
    String putPluginSetting(final @NotNull String pluginSettingKey, final String pluginSettingValue);

    /**
     * Removes a properly namespaced plug-in setting based on a given key.
     * <p>
     * Implementations should eventually persist to {@link com.atlassian.sal.api.pluginsettings.PluginSettings} however
     * this allows for caching, name interception and other functions to ensure access to plug-in settings for Aperture
     * are consistent.
     *
     * @param pluginSettingKey the logical name of the plug-in setting to remove.
     * @return previous text value of the given plug-in setting based on the key provided; can be <code>null</code>.
     * @throws IllegalArgumentException if the provided <code>pluginSettingKey</code> is null.
     */
    String removePluginSetting(final @NotNull String pluginSettingKey);

    String getApertureProjectKey();

    /**
     * Enumeration definition for logical Aperture services that are supported via JIRA application links.
     * <p>
     *
     * @author Developer Central @ PNNL
     * @see ApplicationLink
     */
    enum ProjectService {

        /**
         * Enumerated value for the JIRA service for software development projects.
         */
        JIRA(false),
        /**
         * Enumerated value for the Confluence service for software development & general projects.
         */
        CONFLUENCE,
        /**
         * Enumerated value for the Fisheye/Crucible service for software development & general projects.
         */
        CRUCIBLE,
        /**
         * Enumerated value for the Stash/Bitbucket service for software development projects.
         */
        BITBUCKET,
        /**
         * Enumerated value for the Jenkins service for software development projects.
         */
        JENKINS,
        /**
         * Enumerated value for the Bamboo service for software development projects.
         */
        BAMBOO;

        private final boolean applicationLinkRequired;

        ProjectService() {
            this(true);
        }

        ProjectService(final boolean applicationLinkRequired) {
            this.applicationLinkRequired = applicationLinkRequired;
        }

        public String getI18nNameKey() {

            return String.format("pnnl.aperture.project-service.%s.name", name().toLowerCase());
        }

        public String getI18nDescriptionKey() {

            return String.format("pnnl.aperture.project-service.%s.desc", name().toLowerCase());
        }

        public boolean isApplicationLinkRequired() {
            return applicationLinkRequired;
        }
    }

    /**
     * Enumeration definition for logical Aperture custom fields that are mapped into JIRA Custom fields.
     * <p>
     *
     * @author Developer Central @ PNNL
     * @see com.atlassian.jira.issue.fields.CustomField
     */
    enum CustomField {

        /**
         *
         */
        PROJECT_KEY,
        /**
         *
         */
        USERS,
        /**
         *
         */
        GROUPS,
        /**
         *
         */
        VISBILITY,
        /**
         *
         */
        CATEGORY;

        public String getI18nNameKey() {

            return String.format("pnnl.aperture.custom-field.%s.name", name().toLowerCase());
        }

        public String getI18nDescriptionKey() {

            return String.format("pnnl.aperture.custom-field.%s.desc", name().toLowerCase());
        }
    }
}
