package gov.pnnl.aperture.project.services;

import com.atlassian.core.util.DateUtils;
import com.atlassian.core.util.InvalidDurationException;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarManager;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.ProjectCreationData;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.bc.projectroles.ProjectRoleService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.icon.IconOwningObjectId;
import com.atlassian.jira.icon.IconType;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.customfields.CustomFieldUtils;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.notification.NotificationSchemeManager;
import com.atlassian.jira.permission.PermissionSchemeManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectCategory;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.type.ProjectType;
import com.atlassian.jira.project.type.ProjectTypeKey;
import com.atlassian.jira.project.type.ProjectTypeManager;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.scheme.Scheme;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleActor;
import com.atlassian.jira.task.context.Context;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.util.collect.CollectionEnclosedIterable;
import com.atlassian.jira.util.collect.Sized;
import com.atlassian.jira.util.index.Contexts;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.plugin.util.Assertions;
import com.atlassian.query.Query;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureProjectSettings;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.JiraUtils;
import gov.pnnl.aperture.updates.AvatarImageProvider;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static gov.pnnl.aperture.ApertureConstants.IT_SIMPLE_PROJECT_REQUEST;
import static gov.pnnl.aperture.ApertureConstants.IT_SOFTWARE_PROJECT_REQUEST;

/**
 * Project service handler for creating and configuring JIRA projects as part of Aperture.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public class JiraProjectServiceHandler extends AbstractProjectServiceHandler {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(JiraProjectServiceHandler.class);
    /**
     * Type key for adding user directory groups to project roles.
     *
     * @see #modifyGroups(String, Aperture.PermissionMode, Aperture.Role, Collection)
     */
    private static final String GROUP_ROLE_ACTOR_TYPE = ProjectRoleActor.GROUP_ROLE_ACTOR_TYPE;
    /**
     * Type key for adding user directory users to project roles.
     *
     * @see #modifyUsers(String, Aperture.PermissionMode, Aperture.Role, Collection)
     */
    private static final String USER_ROLE_ACTOR_TYPE = ProjectRoleActor.USER_ROLE_ACTOR_TYPE;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param aperture reference to the current aperture service.
     * @param settings reference the current implementation of the aperture settings service.
     * @throws IllegalArgumentException if either aperture or settings parameters are <code>null</code>.
     */
    public JiraProjectServiceHandler(final Aperture aperture, final ApertureSettings settings) {

        super(aperture, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isServiceAvailable(final String projectKey) {

        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final Project project = projectManager.getProjectByCurrentKeyIgnoreCase(projectKey);
        return project != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIdle(final String projectKey) {

        final ApertureSettings settings = getApertureSettings();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final Project project = projectManager.getProjectByCurrentKeyIgnoreCase(projectKey);
        final Query issueQuery = generateProjectIssueQuery(project, settings);
        final SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
        try {
            final long count = searchService.searchCountOverrideSecurity(settings.getApertureUser(), issueQuery);
            return count <= 0;
        } catch (SearchException e) {
            LOG.warn(String.format("Failed to execute project issue search for project '%s'", projectKey), e);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApertureSettings.ProjectService getServiceType() {

        return ApertureSettings.ProjectService.JIRA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection createService(final MutableIssue issue, final Map<String, Serializable> environment) {

        final ErrorCollection errors = new SimpleErrorCollection();
        final JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        final ApplicationUser currentUser = context.getLoggedInUser();
        Assert.notNull(currentUser, "Cannot create JIRA service with an non-authenticated user.");

        final ApertureSettings settings = getApertureSettings();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final ApplicationUser projectLead = issue.getReporter();

        final String projectKey = settings.getProjectKeyFor(issue);
        final ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
        final String baseURL = applicationProperties.getString(APKeys.JIRA_BASEURL);
        final String url = String.format("%s/browse/%s", baseURL, projectKey);

        LOG.info(String.format("Provisioning JIRA project:'%s' as user:'%s'", projectKey, currentUser));

        ProjectCreationData.Builder builder = new ProjectCreationData.Builder();
        builder.withKey(projectKey);
        builder.withLead(projectLead);
        builder.withDescription(issue.getDescription());
        builder.withUrl(url);
        builder.withName(issue.getSummary());
        //  builder.withType(settings.getProjectTypeFor(issue).getKey() );
        builder.withType(getSoftwareProjectType().getKey());


        final Project prj = projectManager.createProject(currentUser, builder.build());
        try {
            installProjectAvatar(prj, projectManager);
        } catch (IOException error) {
            LOG.warn("Failed to install custom project avatar;using default", error);
        }

        final String categoryFor = settings.getCategoryFor(issue);
        if (StringUtils.hasText(categoryFor)) {
            ProjectCategory category = projectManager.getProjectCategoryObjectByNameIgnoreCase(categoryFor);
            if (category == null) {
                final String description = String.format("Automatic category for project %s", projectKey);
                category = projectManager.createProjectCategory(categoryFor, description);
            }
            projectManager.setProjectCategory(prj, category);
        }

        final ProjectTypeKey projectTypeKey = prj.getProjectTypeKey();
        final Map<String, Object> defaults = settings.getServiceConfiguration(ApertureSettings.ProjectService.JIRA, projectTypeKey.getKey());
        longifyValues(defaults);
        assignWorkflowScheme(prj, (String) defaults.get("workflow_scheme"));
        assignIssueTypeScheme(prj, (Long) defaults.get("issue_scheme"));
        assignPermissionScheme(prj, (Long) defaults.get("perm_scheme"));
        assignNotificationScheme(prj, (Long) defaults.get("notify_scheme"));
        assignIssueTypeScreenScheme(prj, (Long) defaults.get("screen_scheme"));
        assignFieldConfigurationScheme(prj, (Long) defaults.get("field_scheme"));

        final VersionManager versionManager = ComponentAccessor.getVersionManager();
        try {
            final String desc = String.format("Initial Release for %s", prj.getName());
            final Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 30);
            versionManager.createVersion("1.0", new Date(), calendar.getTime(), desc, prj.getId(), null, false);
        } catch (CreateException ex) {
            LOG.warn("Failed to create initial version", ex);
        }

        JiraUtils.addErrorCollectionAsComments(issue, "JIRA Remote Link", addServiceLinkTo(issue, "JIRA Project", url));
        addComponentTo(issue);
        final ApertureProjectSettings projectSettings = settings.getProjectSettings(projectKey);
        projectSettings.setCreatedAt(new Date());
        return errors;
    }

    private void installProjectAvatar(final Project prj, final ProjectManager projectManager) throws IOException {

        final AvatarManager am = ComponentAccessor.getAvatarManager();
        final IconOwningObjectId ownerId = IconOwningObjectId.from(prj.getId());
        final Avatar avatar = am.create(IconType.PROJECT_ICON_TYPE, ownerId, new AvatarImageProvider("/gov/pnnl/aperture/images/new-project-avatar.png"));
        projectManager.updateProject(prj, prj.getName(), prj.getDescription(), prj.getLeadUserKey(), prj.getUrl(), prj.getAssigneeType(), avatar.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection destroyService(final String projectKey, final Map<String, Serializable> environment) {

        LOG.info("Rolling back JIRA project creation.");
        final ErrorCollection errors = new SimpleErrorCollection();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final ProjectService projectService = ComponentAccessor.getComponent(ProjectService.class);

        final Project project = projectManager.getProjectObjByKeyIgnoreCase(projectKey);
        if (project != null) {
            final ApertureSettings apertureSettings = getApertureSettings();
            final ApplicationUser apertureUser = apertureSettings.getApertureUser();
            final ProjectService.DeleteProjectValidationResult validationResult = projectService.validateDeleteProject(apertureUser, projectKey);
            if (validationResult.isValid()) {
                LOG.info(String.format("Removing project by key [%s] from JIRA", projectKey));
                final ProjectService.DeleteProjectResult deleteProjectResult = projectService.deleteProject(apertureUser, validationResult);
                errors.addErrorCollection(deleteProjectResult.getErrorCollection());
            } else {
                errors.addErrorCollection(validationResult.getErrorCollection());
            }
        }
        return errors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection modifyUsers(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<ApplicationUser> projectUsers) {

        final ErrorCollection errors = new SimpleErrorCollection();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final Project project = projectManager.getProjectByCurrentKeyIgnoreCase(projectKey);
        final ProjectRole jiraRole = role.getProjectRole();

        for (final ApplicationUser user : projectUsers) {
            LOG.debug(String.format("Adding user:'%s' to project:'%s' under role:'%s'", user.getName(), projectKey, jiraRole.getName()));
            final Set<String> actor = Collections.singleton(user.getKey());
            errors.addErrorCollection(modifyActors(mode, project, actor, jiraRole, USER_ROLE_ACTOR_TYPE, errors));
        }
        return errors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection modifyGroups(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<Group> projectGroups) {

        final ErrorCollection errors = new SimpleErrorCollection();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final Project project = projectManager.getProjectByCurrentKeyIgnoreCase(projectKey);
        final ProjectRole jiraRole = role.getProjectRole();

        for (final Group group : projectGroups) {
            LOG.debug(String.format("Adding group:'%s' to project:'%s' under role:'%s'", group.getName(), projectKey, jiraRole.getName()));
            final Set<String> actor = Collections.singleton(group.getName());
            errors.addErrorCollection(modifyActors(mode, project, actor, jiraRole, GROUP_ROLE_ACTOR_TYPE, errors));
        }
        return errors;
    }


    private void assignPermissionScheme(final Project project, final Long schemeId) {

        final PermissionSchemeManager permissionSchemeManager = ComponentAccessor.getPermissionSchemeManager();
        final Scheme scheme = schemeId == null ? null : permissionSchemeManager.getSchemeObject(schemeId);
        if (scheme == null) {
            LOG.debug(String.format("Assigning default permission scheme to project:'%s'", project.getKey()));
            permissionSchemeManager.addSchemeToProject(project, permissionSchemeManager.getDefaultSchemeObject());
        } else {
            LOG.debug(String.format("Assigning permission scheme:'%s' to project:'%s'", scheme.getName(), project.getKey()));
            permissionSchemeManager.addSchemeToProject(project, scheme);
        }
    }

    private void assignIssueTypeScheme(final Project project, final Long schemeId) {

        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final Long[] projectList = new Long[]{project.getId()};

        final List<JiraContextNode> contexts = CustomFieldUtils.buildJiraIssueContexts(true, projectList, projectManager);
        final FieldConfigSchemeManager fcSchemeManager = ComponentAccessor.getComponent(FieldConfigSchemeManager.class);
        final FieldManager fieldManager = ComponentAccessor.getFieldManager();
        fcSchemeManager.removeSchemeAssociation(contexts, fieldManager.getIssueTypeField());
        fieldManager.refresh();

        final IssueTypeSchemeManager schemeManager = ComponentAccessor.getIssueTypeSchemeManager();
        final FieldConfigScheme scheme = schemeId == null ? null : fcSchemeManager.getFieldConfigScheme(schemeId);
        if (scheme == null) {
            final FieldConfigScheme defaultScheme = schemeManager.getDefaultIssueTypeScheme();
            LOG.debug(String.format("Assigning default issue type scheme to project:'%s'", project.getKey()));
            fcSchemeManager.updateFieldConfigScheme(defaultScheme, contexts, defaultScheme.getField());
        } else {
            LOG.debug(String.format("Assigning issue type scheme:'%s' to project:'%s'", scheme.getName(), project.getKey()));
            fcSchemeManager.updateFieldConfigScheme(scheme, contexts, scheme.getField());
        }
    }

    private void assignIssueTypeScreenScheme(final Project project, final Long schemeId) {

        final IssueTypeScreenSchemeManager schemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager();
        final IssueTypeScreenScheme scheme = schemeId == null ? null : schemeManager.getIssueTypeScreenScheme(schemeId);
        if (scheme == null) {
            LOG.debug(String.format("Assigning default issue type screen scheme to project:'%s'", project.getKey()));
            schemeManager.associateWithDefaultScheme(project);
        } else {
            LOG.debug(String.format("Assigning issue type screen scheme:'%s' to project:'%s'", scheme.getName(), project.getKey()));
            schemeManager.addSchemeAssociation(project, scheme);
        }
    }

    private void assignNotificationScheme(final Project project, final Long schemeId) {

        final NotificationSchemeManager notificationSchemeManager = ComponentAccessor.getNotificationSchemeManager();
        final Scheme scheme = schemeId == null ? null : notificationSchemeManager.getSchemeObject(schemeId);
        if (scheme == null) {
            LOG.debug(String.format("Assigning default notification scheme to project:'%s'", project.getKey()));
            notificationSchemeManager.addDefaultSchemeToProject(project);
        } else {
            LOG.debug(String.format("Assigning notification scheme:'%s' to project:'%s'", scheme.getName(), project.getKey()));
            notificationSchemeManager.addSchemeToProject(project, scheme);
        }
    }

    private void assignFieldConfigurationScheme(final Project project, final Long schemeId) {

        final FieldLayoutManager layoutManager = ComponentAccessor.getComponentOfType(FieldLayoutManager.class);
        final FieldLayoutScheme scheme = schemeId == null ? null : layoutManager.getMutableFieldLayoutScheme(schemeId);
        // we don't have to assign the field configuration if it is the 'default' one
        if (scheme != null) {
            LOG.debug(String.format("Assigning field configuration scheme:'%s' to project:'%s'", scheme.getName(), project.getKey()));
            layoutManager.addSchemeAssociation(project, schemeId);
        }
    }

    private void assignWorkflowScheme(final Project project, final String workflowSchemeName) {

        final WorkflowSchemeManager schemeManager = ComponentAccessor.getWorkflowSchemeManager();
        final Scheme scheme = StringUtils.hasText(workflowSchemeName) ? schemeManager.getSchemeObject(workflowSchemeName) : null;
        if (scheme == null) {
            LOG.debug(String.format("Assigning default workflow scheme to project:'%s'", project.getKey()));
            schemeManager.addDefaultSchemeToProject(project);
        } else {
            LOG.debug(String.format("Assigning workflow scheme:'%s' to project:'%s'", scheme.getName(), project.getKey()));
            schemeManager.addSchemeToProject(project, scheme);
        }
    }

    private void longifyValues(final Map<String, Object> config) {

        for (Map.Entry<String, Object> entrySet : config.entrySet()) {
            final Object value = entrySet.getValue();
            if (value instanceof String) {
                try {
                    entrySet.setValue(Long.valueOf((String) value));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private ErrorCollection modifyActors(final Aperture.PermissionMode mode, final Project project, final Set<String> actor, final ProjectRole role, final String roleType, final ErrorCollection errors) {

        final ProjectRoleService service = ComponentAccessor.getComponentOfType(ProjectRoleService.class);
        switch (mode) {
            case ADD:
            case REPLACE:
                service.addActorsToProjectRole(actor, role, project, roleType, errors);
                break;
            case REMOVE:
                service.removeActorsFromProjectRole(actor, role, project, roleType, errors);
                break;
            default:
                break;
        }
        return errors;
    }

    private ProjectType getSoftwareProjectType(){

        final ProjectTypeManager typeManager = ComponentAccessor.getComponent(ProjectTypeManager.class);
        final List<ProjectType> projectTypes = typeManager.getAllAccessibleProjectTypes();
        for (final ProjectType pt : projectTypes) {
            final ProjectTypeKey projectTypeKey = pt.getKey();
            if ("software".equals(projectTypeKey.getKey())) {
                return pt;
            }
        }
        return null;
    }

    private Query generateProjectIssueQuery(final Project project, final ApertureSettings settings) {

        final Calendar c = Calendar.getInstance();
        try {
            final long duration = DateUtils.getDuration(settings.getDeleteDuration());
            c.setTimeInMillis(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(duration));
        } catch (InvalidDurationException e) {
            LOG.warn("Received an invalid project-idle duration string from settings", e);
        }
        final Date boundary = c.getTime();
        final JqlClauseBuilder subjectBuilder = JqlQueryBuilder.newClauseBuilder();
        return subjectBuilder.project(project.getId()).and().updatedAfter(boundary).or().createdAfter(boundary).buildQuery();
    }
}
