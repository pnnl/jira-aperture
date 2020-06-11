package gov.pnnl.aperture.project;

import com.atlassian.annotations.PublicSpi;
import com.atlassian.applinks.api.ApplicationId;
import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationLinkService;
import com.atlassian.applinks.api.TypeNotInstalledException;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.project.type.ProjectType;
import com.atlassian.jira.project.type.ProjectTypeKey;
import com.atlassian.jira.project.type.ProjectTypeManager;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.plugin.StateAware;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.util.Assertions;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.opensymphony.workflow.loader.ActionDescriptor;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureProjectSettings;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.WorkflowConfiguration;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.constraints.NotNull;
import java.util.*;

import static gov.pnnl.aperture.ApertureConstants.IT_SIMPLE_PROJECT_REQUEST;
import static gov.pnnl.aperture.ApertureConstants.IT_SOFTWARE_PROJECT_REQUEST;

/**
 * Implementation for {@link ApertureSettings} by PNNL.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
@PublicSpi
@Named("Aperture Settings & Configuration Component")
@ExportAsService({ApertureSettings.class, LifecycleAware.class, StateAware.class})
public class PnnlApertureSettings extends AbstractAperturePlugin implements ApertureSettings {

    /**
     * Reference to the current logger for this class instance.
     */
    private static final transient Logger LOG = Logger.getLogger(PnnlApertureSettings.class);
    /**
     * Constant for the work flow setting where all the work flow actions are part of.
     */
    private static final String SETTING_JIRA_WORKFLOW = "jira_workflow";
    /**
     * Constant for the email notifications via Aperture.
     */
    private static final String SETTING_EMAIL_ADDRESS = "email-address";
    /**
     * Constant for the {@link ApplicationUser} key value for performing tasks via Aperture.
     */
    private static final String SETTING_APERTURE_USER = "aperture-user";
    /**
     * Constant for the setting of duration for how to wait before actually removing projects via Aperture.
     */
    private static final String SETTING_DELETE_DURATION = "delete-duration";
    /**
     * Constant for the setting of duration for how long a project can be active before being considered idle via Aperture.
     */
    private static final String SETTING_PROJECT_IDLE_DURATION = "project-idle-duration";
    /**
     * Constant for the setting of interval at which idle project detection runs at via Aperture.
     */
    private static final String SETTING_PROJECT_IDLE_INTERVAL = "project-idle-interval";
    /**
     * Constant for the work flow action when Aperture finishes a new project creation work flow.
     */
    private static final String SETTING_WORKFLOW_FINISH_ACTION = "finish";
    /**
     * Constant for the work flow action when Aperture encounters an error during a new project creation work flow.
     */
    private static final String SETTING_WORKFLOW_TRIAGE_ACTION = "triage";
    /**
     * Constant for the work flow action when Aperture starts a new project creation work flow.
     */
    private static final String SETTING_WORKFLOW_START_ACTION = "start";
    /**
     * Constant for the resolution ID to use when invoking the finish work-flow action.
     */
    private static final String SETTING_WORKFLOW_FINISH_RESOLUTION = "finish_resolution";
    /**
     * Internal plugin-setting key for which JIRA project key is the 'Aperture' project.
     * <p>
     *
     * @see Aperture#getProject
     */
    private static final String APERTURE_PROJECT_KEY = "aperture-project-key";

    /**
     * Reference to the current application link service in the current application context.
     */
    private final ApplicationLinkService applinkService;

    @Inject
    public PnnlApertureSettings(@ComponentImport final PluginSettingsFactory pluginSettingsFactory, @ComponentImport final ApplicationLinkService applinkService) {

        super(pluginSettingsFactory);
        this.applinkService = applinkService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCustomField(final CustomField cf, final com.atlassian.jira.issue.fields.CustomField customField) {

        Assertions.notNull("customField", customField);
        putPluginSetting(cf.name(), customField.getIdAsLong().toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public com.atlassian.jira.issue.fields.CustomField getCustomField(final CustomField cf) {

        final Object customFieldIdValue = getPluginSetting(cf.name());
        if (Objects.nonNull(customFieldIdValue)) {
            try {
                final Long customFieldId = Long.valueOf(customFieldIdValue.toString());
                final CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
                return cfm.getCustomFieldObject(customFieldId);
            } catch (NumberFormatException nfe) {
                LOG.warn(String.format("Failed to lookup custom field:%s", customFieldIdValue), nfe);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationLink(final ProjectService service, final ApplicationId applicationLink) {

        if (Objects.isNull(applicationLink)) {
            removePluginSetting(service.name());
        } else {
            putPluginSetting(service.name(), applicationLink.get());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ApplicationLink> getSystemApplicationLinks() {

        final List<ApplicationLink> links = new ArrayList<>();
        for (final ApplicationLink al : applinkService.getApplicationLinks()) {
            links.add(al);
        }
        return links;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApplicationLink getApplicationLink(final ProjectService service) {

        final String applicationLinkId = getPluginSetting(service.name());
        if (StringUtils.hasText(applicationLinkId)) {
            try {
                LOG.debug(String.format("Getting Application Link for ID:%s for project service:%s.", applicationLinkId, service));
                return applinkService.getApplicationLink(new ApplicationId(applicationLinkId));
            } catch (TypeNotInstalledException ex) {
                LOG.error(String.format("Application ID:%s for project service:%s is not available.", applicationLinkId, service));
                throw new IllegalStateException(ex);
            }
        }
        LOG.debug(String.format("No Application Link for project service:%s.", service));
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProjectKeyFor(final Issue issue) {

        Assertions.notNull("issue", issue);
        final com.atlassian.jira.issue.fields.CustomField keyCustomField = getCustomField(CustomField.PROJECT_KEY);
        final Object customFieldValue = issue.getCustomFieldValue(keyCustomField);
        return customFieldValue == null ? null : customFieldValue.toString().toUpperCase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCategoryFor(final Issue issue) {

        Assertions.notNull("issue", issue);
        final com.atlassian.jira.issue.fields.CustomField cf = getCustomField(CustomField.CATEGORY);
        final Object customFieldValue = issue.getCustomFieldValue(cf);
        return customFieldValue == null ? null : customFieldValue.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<ApplicationUser> getUsersFor(final Issue issue) {

        Assertions.notNull("issue", issue);
        final com.atlassian.jira.issue.fields.CustomField usersCustomField = getCustomField(CustomField.USERS);
        final Object targetUsers = issue.getCustomFieldValue(usersCustomField);
        if (targetUsers instanceof Collection) {
            return (Collection) targetUsers;
        }
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<ApplicationUser> getProjectMembersFor(final Issue issue) {

        final UserManager userManager = ComponentAccessor.getUserManager();
        final GroupManager groupManager = ComponentAccessor.getGroupManager();
        final Set<ApplicationUser> projectMembers = new HashSet<>();
        for (final Group group : getGroupsFor(issue)) {
            for (final ApplicationUser user : groupManager.getUsersInGroup(group)) {
                projectMembers.add(userManager.getUserByName(user.getName()));
            }
        }
        projectMembers.addAll(getUsersFor(issue));

        final ApplicationUser reporter = issue.getReporter();
        final ApplicationUser projectOwner = userManager.getUserByName(reporter.getName());
        projectMembers.add(projectOwner);
        return projectMembers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Group> getGroupsFor(final Issue issue) {

        Assertions.notNull("issue", issue);
        final com.atlassian.jira.issue.fields.CustomField groupsCustomField = getCustomField(CustomField.GROUPS);
        final Object targetUsers = issue.getCustomFieldValue(groupsCustomField);
        if (targetUsers instanceof Collection) {
            return (Collection) targetUsers;
        }
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getServiceConfiguration(final ProjectService projectService, final String projectType) {

        final Map<String, Object> config = new HashMap<>();
        switch (projectService) {
            case CONFLUENCE:
                config.put("username", getPluginSetting(String.format("%s.%s.username", projectService.name(), projectType)));
                config.put("password", getPluginSetting(String.format("%s.%s.password", projectService.name(), projectType)));
                break;
            case JIRA:
                config.put("issue_scheme", getPluginSetting(String.format("%s.%s.issue_scheme", projectService.name(), projectType)));
                config.put("screen_scheme", getPluginSetting(String.format("%s.%s.screen_scheme", projectService.name(), projectType)));
                config.put("field_scheme", getPluginSetting(String.format("%s.%s.field_scheme", projectService.name(), projectType)));
                config.put("workflow_scheme", getPluginSetting(String.format("%s.%s.workflow_scheme", projectService.name(), projectType)));
                config.put("notify_scheme", getPluginSetting(String.format("%s.%s.notify_scheme", projectService.name(), projectType)));
                config.put("perm_scheme", getPluginSetting(String.format("%s.%s.perm_scheme", projectService.name(), projectType)));
                break;
            default:
                break;
        }
        return config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setServiceConfiguration(final ProjectService projectService, final String projectType, final Map<String, Object> configuration) {

        for (Map.Entry<String, Object> entry : configuration.entrySet()) {
            final Object newValue = entry.getValue();
            final String configurationKey = String.format("%s.%s.%s", projectService.name(), projectType, entry.getKey());
            if (Objects.isNull(newValue)) {
                removePluginSetting(configurationKey);
            } else {
                putPluginSetting(configurationKey, newValue.toString());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowConfiguration getWorkflowConfiguration() {

        final WorkflowConfiguration configuration = new WorkflowConfiguration();
        final JiraWorkflow wf = getWorkflow();
        if (Objects.nonNull(wf)) {
            configuration.setWorkflow(wf);
            configuration.setStartAction(getAction(SETTING_WORKFLOW_START_ACTION));
            configuration.setTriageAction(getAction(SETTING_WORKFLOW_TRIAGE_ACTION));
            configuration.setFinishAction(getAction(SETTING_WORKFLOW_FINISH_ACTION));
            final String resolutionId = getPluginSetting(SETTING_WORKFLOW_FINISH_RESOLUTION);
            final ResolutionManager resolutionManager = ComponentAccessor.getComponent(ResolutionManager.class);
            if (StringUtils.hasText(resolutionId)) {
                configuration.setFinishStatus(resolutionManager.getResolution(resolutionId));
            } else {
                configuration.setFinishStatus(resolutionManager.getDefaultResolution());
            }
        }
        return configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkflowConfiguration(final WorkflowConfiguration workflowConfiguration) {

        Assertions.notNull("workflowConfiguration", workflowConfiguration);
        final JiraWorkflow wf = workflowConfiguration.getWorkflow();
        if (Objects.isNull(wf)) {
            removePluginSetting(SETTING_JIRA_WORKFLOW);
            removePluginSetting(SETTING_WORKFLOW_FINISH_RESOLUTION);
            setAction(SETTING_WORKFLOW_START_ACTION, null);
            setAction(SETTING_WORKFLOW_TRIAGE_ACTION, null);
            setAction(SETTING_WORKFLOW_FINISH_ACTION, null);
        } else {
            putPluginSetting(SETTING_JIRA_WORKFLOW, wf.getName());
            final Resolution finishStatus = workflowConfiguration.getFinishStatus();
            if (Objects.isNull(finishStatus)) {
                removePluginSetting(SETTING_WORKFLOW_FINISH_RESOLUTION);
            } else {
                putPluginSetting(SETTING_WORKFLOW_FINISH_RESOLUTION, finishStatus.getId());
            }
            setAction(SETTING_WORKFLOW_START_ACTION, workflowConfiguration.getStartAction());
            setAction(SETTING_WORKFLOW_TRIAGE_ACTION, workflowConfiguration.getTriageAction());
            setAction(SETTING_WORKFLOW_FINISH_ACTION, workflowConfiguration.getFinishAction());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeleteDuration() {

        return getSettingsString(SETTING_DELETE_DURATION, DEFAULT_DELETE_DURATION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeleteDuration(final String deleteDuration) {

        if (StringUtils.hasText(deleteDuration)) {
            putPluginSetting(SETTING_DELETE_DURATION, deleteDuration);
        } else {
            removePluginSetting(SETTING_DELETE_DURATION);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProjectIdleDuration() {

        return getSettingsString(SETTING_PROJECT_IDLE_DURATION, DEFAULT_PROJECT_IDLE_DURATION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProjectIdleDuration(final String idleDuration) {

        if (StringUtils.hasText(idleDuration)) {
            putPluginSetting(SETTING_PROJECT_IDLE_DURATION, idleDuration);
        } else {
            removePluginSetting(SETTING_PROJECT_IDLE_DURATION);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProjectIdleInterval() {

        return getSettingsString(SETTING_PROJECT_IDLE_INTERVAL, DEFAULT_PROJECT_IDLE_INTERVAL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProjectIdleInterval(final String idleInterval) {

        if (StringUtils.hasText(idleInterval)) {
            putPluginSetting(SETTING_PROJECT_IDLE_INTERVAL, idleInterval);
        } else {
            removePluginSetting(SETTING_PROJECT_IDLE_INTERVAL);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPluginSetting(final String settingKey) {

        final PluginSettings settings = getSettings();
        return (String) settings.get(String.format("gov.pnnl.aperture/%s", settingKey));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String putPluginSetting(final String settingKey, final String settingValue) {

        if (StringUtils.hasText(settingKey)) {
            final PluginSettings settings = getSettings();
            return (String) settings.put(String.format("gov.pnnl.aperture/%s", settingKey), settingValue);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String removePluginSetting(final String settingKey) {

        if (StringUtils.hasText(settingKey)) {
            final PluginSettings settings = getSettings();
            return (String) settings.remove(String.format("gov.pnnl.aperture/%s", settingKey));
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getApertureProjectKey() {

        return getPluginSetting(APERTURE_PROJECT_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApertureProjectSettings getProjectSettings(@NotNull final String projectKey) {

        Assertions.notNull("Cannot get project settings for a null project key.", projectKey);
        return new PnnlProjectSettings(getPluginSettingsFactory(), projectKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectType getProjectTypeFor(@NotNull final Issue issue) {

        Assertions.notNull("Cannot get project type for null JIRA issue reference.", issue);
        final ProjectTypeManager typeManager = ComponentAccessor.getComponent(ProjectTypeManager.class);
        final List<ProjectType> projectTypes = typeManager.getAllAccessibleProjectTypes();
        final IssueType issueType = issue.getIssueType();
        final String typeName = issueType.getName();

        for (final ProjectType pt : projectTypes) {
            final ProjectTypeKey projectTypeKey = pt.getKey();
            if (typeName.equals(IT_SOFTWARE_PROJECT_REQUEST) && "software".equals(projectTypeKey.getKey())) {
                return pt;
            } else if (typeName.equals(IT_SIMPLE_PROJECT_REQUEST) && "business".equals(projectTypeKey.getKey())) {
                return pt;
                // TODO add service desk support in the future ?
                // } else if (typeName.equals(IT_SERVICE_DESK_PROJECT_REQUEST) && "service_desk".equals(projectTypeKey.getKey())) {
                //   return pt;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getApertureEmailAddress() {

        return getPluginSetting(SETTING_EMAIL_ADDRESS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApertureEmailAddress(final String emailAddress) {

        if (StringUtils.hasText(emailAddress)) {
            putPluginSetting(SETTING_EMAIL_ADDRESS, emailAddress);
        } else {
            removePluginSetting(SETTING_EMAIL_ADDRESS);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApplicationUser getApertureUser() {

        final UserManager userManager = ComponentAccessor.getUserManager();
        final String userKey = getPluginSetting(SETTING_APERTURE_USER);
        if (StringUtils.hasText(userKey)) {
            return userManager.getUserByKey(userKey);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApertureUser(final ApplicationUser apertureUser) {

        Assertions.notNull("Aperture user cannot be null", apertureUser);
        putPluginSetting(SETTING_APERTURE_USER, apertureUser.getKey());
    }

    protected JiraWorkflow getWorkflow() {

        final String workflowName = getPluginSetting(SETTING_JIRA_WORKFLOW);
        final WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
        if (StringUtils.hasText(workflowName)) {
            return workflowManager.getWorkflow(workflowName);
        }
        return null;
    }

    private void setAction(final String actionKey, final ActionDescriptor action) {

        final String actionSetting = String.format("action_%s_id", actionKey);
        if (Objects.isNull(action)) {
            removePluginSetting(actionSetting);
        } else {
            final String actionId = String.valueOf(action.getId());
            putPluginSetting(actionSetting, actionId);
        }
    }

    private ActionDescriptor getAction(final String actionKey) {

        final JiraWorkflow workflow = getWorkflow();
        if (Objects.isNull(workflow)) {
            LOG.warn(String.format("Failed to retrieve workflow-action:'%s'; workflow is not defined.", actionKey));
        } else {
            final String workflowActionSetting = String.format("action_%s_id", actionKey);
            final String actionId = getPluginSetting(workflowActionSetting);
            if (StringUtils.hasText(actionId)) {
                try {
                    final int targetId = Integer.parseInt(actionId);
                    for (final ActionDescriptor action : workflow.getAllActions()) {
                        if (action.getId() == targetId) {
                            return action;
                        }
                    }
                } catch (NumberFormatException nfe) {
                    LOG.warn(String.format("Failed to retrieve workflow-action:'%s'; workflow-action[%s] is not a number:%s.", actionKey, actionId, nfe));
                }
            }
        }
        LOG.warn(String.format("Failed to get workflow action for key:'%s'", actionKey));
        return null;
    }

    private String getSettingsString(final String settingsKey, final String defaultValue) {

        final String settingsValue = getPluginSetting(settingsKey);
        if (StringUtils.hasText(settingsValue)) {
            return settingsValue;
        }
        return defaultValue;
    }
}
