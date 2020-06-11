package gov.pnnl.aperture.project;

import com.atlassian.annotations.PublicSpi;
import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarManager;
import com.atlassian.jira.bc.project.component.ProjectComponentManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.icon.IconOwningObjectId;
import com.atlassian.jira.icon.IconType;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.customfields.CustomFieldUtils;
import com.atlassian.jira.issue.fields.ConfigurableField;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.fields.layout.field.FieldConfigurationScheme;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.AssigneeTypes;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.scheme.Scheme;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleActors;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.web.action.admin.issuetypes.IssueTypeManageableOption;
import com.atlassian.jira.web.action.admin.issuetypes.ManageableOptionType;
import com.atlassian.jira.workflow.AssignableWorkflowScheme;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.plugin.StateAware;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import gov.pnnl.aperture.*;
import gov.pnnl.aperture.project.services.*;
import gov.pnnl.aperture.updates.AvatarImageProvider;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.*;


/**
 * @author Developer Central @ PNNL
 */
@PublicSpi
@Named("Aperture Core Component")
@ExportAsService({Aperture.class, LifecycleAware.class, StateAware.class})
public class PnnlAperture extends AbstractAperturePlugin implements Aperture {

    /**
     * Default logger for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(PnnlAperture.class);

    /**
     * Reference to the current ApertureSettings implementation in the current application context.
     */
    private final ApertureSettings settings;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param pluginSettingsFactory pluginSettingsFactory to the current plug-in settings factory.
     * @param settings              current aperture settings implementation for this instance.
     * @throws IllegalArgumentException if either settings parameters provided are <code>null</code>.
     */
    @Inject
    public PnnlAperture(@ComponentImport final PluginSettingsFactory pluginSettingsFactory, final ApertureSettings settings) {

        super(pluginSettingsFactory);
        this.settings = settings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectServiceHandler getServiceHandler(final ApertureSettings.ProjectService projectService) {

        Assert.notNull(projectService, "Cannot get a service handle with a null service type.");
        switch (projectService) {
            case CONFLUENCE:
                return new ConfluenceProjectServiceHandler(this, settings);
            case CRUCIBLE:
                return new CrucibleProjectServiceHandler(this, settings);
            case JENKINS:
                return new JenkinsProjectServiceHandler(this, settings);
            case JIRA:
                return new JiraProjectServiceHandler(this, settings);
            case BITBUCKET:
                return new BitbucketProjectServiceHandler(this, settings);
            default:
                return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<ApplicationUser> getProjectMembers(final String projectKey, final boolean excludeProjectLead) {

        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final ProjectRoleManager manager = ComponentAccessor.getComponentOfType(ProjectRoleManager.class);
        final Project project = projectManager.getProjectObjByKey(projectKey);
        final Collection<ApplicationUser> projectMembers = new HashSet<>();

        for (final ProjectRole projectRole : manager.getProjectRoles()) {
            final ProjectRoleActors actors = manager.getProjectRoleActors(projectRole, project);
            projectMembers.addAll(actors.getApplicationUsers());
        }
        if (!excludeProjectLead) {
            projectMembers.add(project.getProjectLead());
        }
        return projectMembers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection createProject(final MutableIssue issue, final Map<String, Serializable> environment) {

        final ErrorCollection errors = new SimpleErrorCollection();
        final Collection<ProjectServiceHandler> tasks = getProjectServices(issue.getIssueType());
        for (final ProjectServiceHandler handler : tasks) {
            try {
                final ErrorCollection serviceErrors = handler.createService(issue, environment);
                if (serviceErrors.hasAnyErrors()) {
                    for (final String serviceMessage : serviceErrors.getErrorMessages()) {
                        errors.addError(handler.getServiceType().name(), serviceMessage);
                    }
                }
            } catch (RuntimeException error) {
                final StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw, true));
                final String st = sw.toString();
                final String errorMessage = String.format("Failed to invoke service handler: *%s*; reason: _%s_\n{noformat}%s{noformat}", handler.getServiceType(), error, st);
                errors.addError(handler.getServiceType().name(), errorMessage, ErrorCollection.Reason.SERVER_ERROR);
                LOG.fatal(String.format("Failed to invoke service handler:%s; reason:%s", handler.getClass(), error), error);
                final String projectKey = settings.getProjectKeyFor(issue);
                // need to remove it as it could be in an inconsistent or inaccessible state //
                final ErrorCollection serviceErrors = handler.destroyService(projectKey, environment);
                if (serviceErrors.hasAnyErrors()) {
                    for (final String serviceMessage : serviceErrors.getErrorMessages()) {
                        errors.addError(handler.getServiceType().name(), serviceMessage);
                    }
                }
            }
        }
        return errors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection removeProject(final String projectKey, final Map<String, Serializable> environment) {

        final ErrorCollection errors = new SimpleErrorCollection();
        final List<ProjectServiceHandler> tasks = getProjectServices();
        Collections.reverse(tasks);
        for (final ProjectServiceHandler handler : tasks) {
            try {
                final ErrorCollection serviceErrors = handler.destroyService(projectKey, environment);
                errors.addErrorCollection(serviceErrors);
                LOG.debug(String.format("Successfully removed service from handler:%s", handler.getClass()));
            } catch (RuntimeException error) {
                LOG.fatal(String.format("Failed to invoke service handler:%s", handler.getClass()), error);
            }
        }
        return errors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection modifyProjectUserPermissions(final String projectKey, final PermissionMode mode, final Role role, final Collection<ApplicationUser> users) {

        final ErrorCollection errors = new SimpleErrorCollection();
        final List<ProjectServiceHandler> tasks = getProjectServices();
        for (final ProjectServiceHandler handler : tasks) {
            try {
                if (handler.isServiceAvailable(projectKey)) {
                    final ErrorCollection serviceErrors = handler.modifyUsers(projectKey, mode, role, users);
                    errors.addErrorCollection(serviceErrors);
                }
                LOG.debug(String.format("Successfully modified user-permissions on service from handler:%s", handler.getClass()));
            } catch (RuntimeException error) {
                LOG.fatal(String.format("Failed to invoke service handler:%s", handler.getClass()), error);
            }
        }

        if (!errors.hasAnyErrors()) {
            final ProjectManager projectManager = ComponentAccessor.getProjectManager();
            final Project project = projectManager.getProjectByCurrentKeyIgnoreCase(projectKey);
            final Map<String, Object> overrides = new HashMap<>();
            final JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext();

            overrides.put("actionUser", authContext.getLoggedInUser());
            overrides.put("projectMembers", getProjectMembers(projectKey, true));
            overrides.put("projectKey", projectKey);
            overrides.put("projectDesc", project.getDescription());
            overrides.put("projectLinks", JiraUtils.getProjectLinks(overrides, project));
            overrides.put("projectLead", project.getProjectLead());

            String subject;
            for (final ApplicationUser user : users) {
                overrides.put("i18n", ComponentAccessor.getI18nHelperFactory().getInstance(user));
                overrides.put("recipient", user);
                try {
                    LOG.debug(String.format("Delivering email to:%s <%s>", user.getDisplayName(), user.getEmailAddress()));
                    switch (mode) {
                        case ADD:
                        case REPLACE:
                            overrides.put("headerTitle", String.format("Welcome to Developer Central Project '%s' / %s", project.getName(), projectKey));
                            subject = String.format("Welcome to the %s [%s] project from Developer Central", project.getName(), overrides.get("projectKey"));
                            JiraUtils.deliverEmail(overrides, user.getEmailAddress(), "/gov/pnnl/aperture/templates/email/welcome-inline-project.vm.html", subject);
                            break;
                        case REMOVE:
                            subject = String.format("Confirmation of being removed from the %s [%s] project from Developer Central", project.getName(), overrides.get("projectKey"));
                            overrides.put("headerTitle", String.format("%s has revoked access from project '%s' / %s", authContext.getLoggedInUser().getDisplayName(), project.getName(), projectKey));
                            JiraUtils.deliverEmail(overrides, user.getEmailAddress(), "/gov/pnnl/aperture/templates/email/removal-inline-project.vm.html", subject);
                            break;
                    }
                } catch (IOException ex) {
                    LOG.debug("I/O Error generating permission modification email", ex);
                }
            }
        }
        return errors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection modifyProjectGroupPermissions(final String projectKey, final PermissionMode mode, final Role role, final Collection<Group> groups) {

        final ErrorCollection errors = new SimpleErrorCollection();
        final List<ProjectServiceHandler> tasks = getProjectServices();
        for (final ProjectServiceHandler handler : tasks) {
            try {
                final ErrorCollection serviceErrors = handler.modifyGroups(projectKey, mode, role, groups);
                if (serviceErrors.hasAnyErrors()) {
                    for (Map.Entry<String, String> entry : serviceErrors.getErrors().entrySet()) {
                        errors.addError(entry.getKey(), entry.getValue());
                    }
                }
                LOG.debug(String.format("Successfully modified user-permissions on service from handler:%s", handler.getClass()));
            } catch (RuntimeException error) {
                LOG.fatal(String.format("Failed to invoke service handler:%s", handler.getClass()), error);
            }
        }
        return errors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void detectProjectActivity(final String projectKey) {

        final List<ProjectServiceHandler> serviceHandlers = getProjectServices();
        for (final ProjectServiceHandler projectService : serviceHandlers) {
            try {
                final boolean isIdle = projectService.isIdle(projectKey);
                LOG.debug(String.format("Successfully checked service service activity from handler:%s => %s", projectService.getClass(), isIdle));
            } catch (RuntimeException error) {
                LOG.fatal(String.format("Failed to invoke service handler:%s", projectService), error);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Project getProject() {

        final String projectKey = settings.getApertureProjectKey();
        if (StringUtils.hasText(projectKey)) {
            final ProjectManager projectManager = ComponentAccessor.getProjectManager();
            return projectManager.getProjectByCurrentKey(projectKey);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isProjectIdle(final String projectKey) {
        return false;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void installApertureSchemesFor(final Project project) {

        Assert.notNull(project, "Cannot install Aperture schemes into a null JIRA project.");
        installIssueTypeScheme(project);
        installWorkflowScheme(project);
        installFieldConfigurationScheme(project);
        installScreenScheme(project);
        installProjectComponents(project);
        try {
            updateProjectDetails(project);
            settings.putPluginSetting("aperture-project-key", project.getKey());
        } catch (IOException e) {
            LOG.warn("Failed to updated project details when installing Aperture schemes", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssignableWorkflowScheme getWorkflowScheme() {

        final WorkflowSchemeManager workflowSchemeManager = ComponentAccessor.getWorkflowSchemeManager();
        try {
            final long schemeId = Long.valueOf(settings.getPluginSetting(ApertureConstants.APERTURE_WORKFLOW_SCHEME));
            return workflowSchemeManager.getWorkflowSchemeObj(schemeId);
        } catch (NumberFormatException nfe) {
            LOG.warn("Failed to parse aperture workflow scheme ID", nfe);
            throw new IllegalStateException("Aperture workflow type scheme has not been properly installed.", nfe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FieldConfigScheme getIssueTypeScheme() {

        final FieldConfigSchemeManager fcSchemeManager = ComponentAccessor.getComponent(FieldConfigSchemeManager.class);
        try {
            final long schemeId = Long.valueOf(settings.getPluginSetting(ApertureConstants.APERTURE_ISSUE_TYPE_SCHEME));
            return fcSchemeManager.getFieldConfigScheme(schemeId);
        } catch (NumberFormatException nfe) {
            LOG.warn("Failed to parse aperture issue type scheme ID", nfe);
            throw new IllegalStateException("Aperture issue type scheme has not been properly installed.", nfe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IssueTypeScreenScheme getIssueTypeScreenScheme() {

        final IssueTypeScreenSchemeManager schemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager();
        try {
            final long schemeId = Long.valueOf(settings.getPluginSetting(ApertureConstants.APERTURE_SCREEN_SCHEME));
            return schemeManager.getIssueTypeScreenScheme(schemeId);
        } catch (NumberFormatException nfe) {
            LOG.warn("Failed to parse aperture issue type screen scheme ID", nfe);
            throw new IllegalStateException("Aperture issue type screen scheme has not been properly installed.", nfe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FieldLayoutScheme getFieldConfigurationScheme() {

        final FieldLayoutManager layoutManager = ComponentAccessor.getComponentOfType(FieldLayoutManager.class);
        try {
            final long schemeId = Long.valueOf(settings.getPluginSetting(ApertureConstants.APERTURE_FIELD_CONFIG_SCHEME));
            return layoutManager.getMutableFieldLayoutScheme(schemeId);
        } catch (NumberFormatException nfe) {
            LOG.warn("Failed to parse aperture field configuration scheme ID", nfe);
            throw new IllegalStateException("Aperture field configuration scheme has not been properly installed.", nfe);
        }
    }

    /**
     * Gets all available service handlers that are configured with an {@link ApplicationLink} in JIRA.
     * <p>
     *
     * @return collection of all service handlers that have an application link that a project can have.
     */
    private List<ProjectServiceHandler> getProjectServices() {

        final List<ProjectServiceHandler> serviceHandlers = new ArrayList<>();
        for (final ApertureSettings.ProjectService projectService : ApertureSettings.ProjectService.values()) {
            if (projectService.isApplicationLinkRequired()) {
                final ApplicationLink link = settings.getApplicationLink(projectService);
                if (link == null) {
                    LOG.warn(String.format("Cannot initialize task list with service '%s' as an application link has not yet been configured.", projectService));
                    continue;
                }
                LOG.debug(String.format("Application link detected; adding project service handler (%s)", projectService));
                serviceHandlers.add(getServiceHandler(projectService));
            } else {
                LOG.debug(String.format("Adding project service handler (%s)", projectService));
                serviceHandlers.add(getServiceHandler(projectService));
            }
        }
        return serviceHandlers;
    }

    /**
     * Gets the default list of service handlers supported by this implementation of Aperture.
     * <p>
     *
     * @param type issue type from the original JIRA issue being a software or business project...or something else.
     * @return collection of service handlers that will create the project spaces in those services.
     */
    private List<ProjectServiceHandler> getProjectServices(final IssueType type) {

        final List<ProjectServiceHandler> projectServiceHandlers = new ArrayList<>();
        projectServiceHandlers.add(new JiraProjectServiceHandler(this, settings));
        ApplicationLink link = settings.getApplicationLink(ApertureSettings.ProjectService.CONFLUENCE);
        LOG.debug(String.format("Initializing task-list with issueType:%s", type));
        if (link != null) {
            projectServiceHandlers.add(new ConfluenceProjectServiceHandler(this, settings));
        }
        if (type == null || ApertureConstants.IT_SOFTWARE_PROJECT_REQUEST.equalsIgnoreCase(type.getName())) {
            link = settings.getApplicationLink(ApertureSettings.ProjectService.BITBUCKET);
            if (link != null) {
                projectServiceHandlers.add(new BitbucketProjectServiceHandler(this, settings));
            }
            link = settings.getApplicationLink(ApertureSettings.ProjectService.CRUCIBLE);
            if (link != null) {
                projectServiceHandlers.add(new CrucibleProjectServiceHandler(this, settings));
            }
            link = settings.getApplicationLink(ApertureSettings.ProjectService.JENKINS);
            if (link != null) {
                projectServiceHandlers.add(new JenkinsProjectServiceHandler(this, settings));
            }
        }
        return projectServiceHandlers;
    }

    private void installIssueTypeScheme(final Project project) {

        final FieldConfigSchemeManager schemeManager = ComponentAccessor.getFieldConfigSchemeManager();
        final FieldConfigScheme scheme = getIssueTypeScheme();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final Long[] projectIds = new Long[]{project.getId()};
        final List<JiraContextNode> contexts = CustomFieldUtils.buildJiraIssueContexts(false, projectIds, projectManager);

        final ManageableOptionType manageableOptionType = ComponentAccessor.getComponent(IssueTypeManageableOption.class);
        final FieldManager fieldManager = ComponentAccessor.getFieldManager();
        final ConfigurableField configurableField = fieldManager.getConfigurableField(manageableOptionType.getFieldId());

        schemeManager.removeSchemeAssociation(contexts, configurableField);
        LOG.info(String.format("Installing Aperture issue-type-scheme:'%s' into project:'%s'", scheme.getName(), project.getKey()));
        schemeManager.updateFieldConfigScheme(scheme, contexts, configurableField);
    }

    private void installProjectComponents(final Project project) {

        final ProjectComponentManager projectComponentManager = ComponentAccessor.getProjectComponentManager();
        final I18nHelper helper = ComponentAccessor.getI18nHelperFactory().getInstance(Locale.getDefault());
        final long defaultAssigneeType = AssigneeTypes.COMPONENT_LEAD;
        final Long projectId = project.getId();
        for (final ApertureSettings.ProjectService projectService : ApertureSettings.ProjectService.values()) {
            final String componentName = helper.getText(projectService.getI18nNameKey());
            final String componentDesc = helper.getText(projectService.getI18nDescriptionKey());
            if (projectComponentManager.containsName(componentName, projectId)) {
                continue;
            }
            projectComponentManager.create(componentName, componentDesc, null, defaultAssigneeType, projectId);
        }

        final List<String[]> internalComponents = new ArrayList<>();
        internalComponents.add(new String[] { "Aperture", "Issues related to the Aperture JIRA plug-in" });

        for(final String[] component : internalComponents) {
            if (projectComponentManager.containsName(component[0], projectId)) {
                continue;
            }
            projectComponentManager.create(component[0], component[1], null, defaultAssigneeType, projectId);
        }
    }

    private void installScreenScheme(final Project project) {

        final IssueTypeScreenSchemeManager screenSchemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager();
        final IssueTypeScreenScheme scheme = getIssueTypeScreenScheme();
        final IssueTypeScreenScheme existingScheme = screenSchemeManager.getIssueTypeScreenScheme(project);
        screenSchemeManager.removeSchemeAssociation(project, existingScheme);
        LOG.info(String.format("Installing Aperture screen-scheme:'%s' into project:'%s'", scheme.getName(), project.getKey()));
        screenSchemeManager.addSchemeAssociation(project, scheme);
    }

    private void installFieldConfigurationScheme(final Project project) {

        final FieldLayoutManager fieldLayoutManager = ComponentAccessor.getComponentOfType(FieldLayoutManager.class);
        final FieldLayoutScheme fieldConfigurationScheme = getFieldConfigurationScheme();
        final FieldConfigurationScheme existingScheme = fieldLayoutManager.getFieldConfigurationScheme(project);
        if (existingScheme != null) {
            fieldLayoutManager.removeSchemeAssociation(project, existingScheme.getId());
        }

        final Long schemeId = fieldConfigurationScheme.getId();
        LOG.info(String.format("Installing Aperture field-configuration-scheme:'%s' into project:'%s'", schemeId, project.getKey()));
        fieldLayoutManager.addSchemeAssociation(project, schemeId);
    }

    private void installWorkflowScheme(final Project project) {

        final WorkflowSchemeManager workflowSchemeManager = ComponentAccessor.getWorkflowSchemeManager();
        final AssignableWorkflowScheme workflowScheme = getWorkflowScheme();
        final Scheme scheme = workflowSchemeManager.getSchemeObject(workflowScheme.getName());
        LOG.info(String.format("Installing Aperture workflow-scheme:'%s' into project:'%s'", scheme.getName(), project.getKey()));
        workflowSchemeManager.removeSchemesFromProject(project);
        workflowSchemeManager.addSchemeToProject(project, scheme);
    }

    private void updateProjectDetails(final Project project) throws IOException {

        final AvatarManager am = ComponentAccessor.getAvatarManager();
        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        final IconOwningObjectId ownerId = IconOwningObjectId.from(project.getId());
        final AvatarImageProvider imageProvider = new AvatarImageProvider("/gov/pnnl/aperture/images/pluginLogo.png");
        final Avatar avatar = am.create(IconType.PROJECT_ICON_TYPE, ownerId, imageProvider);
        projectManager.updateProject(project, "Developer Central Aperture", "", project.getLeadUserKey(), "https://jira.pnnl.gov/request", project.getAssigneeType(), avatar.getId());
    }
}
