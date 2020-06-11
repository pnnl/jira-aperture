package gov.pnnl.aperture.updates;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationType;
import com.atlassian.applinks.api.application.bamboo.BambooApplicationType;
import com.atlassian.applinks.api.application.bitbucket.BitbucketApplicationType;
import com.atlassian.applinks.api.application.confluence.ConfluenceApplicationType;
import com.atlassian.applinks.api.application.fecru.FishEyeCrucibleApplicationType;
import com.atlassian.applinks.api.application.jira.JiraApplicationType;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreenFactory;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreenSchemeManager;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeEntity;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.workflow.*;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.util.ClassLoaderUtils;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.opensymphony.workflow.InvalidWorkflowDescriptorException;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import com.opensymphony.workflow.loader.WorkflowLoader;
import gov.pnnl.aperture.ApertureConstants;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.WorkflowConfiguration;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Installation task for Aperture to install custom fields and other resources automatically.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
@Named("Aperture JIRA Initial Installation Task")
@ExportAsService({PluginUpgradeTask.class})
public class ApertureJiraInstallTask implements PluginUpgradeTask {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ApertureJiraInstallTask.class);
    /**
     * Reference to the current plug-in settings for the current application context.
     */
    private final ApertureSettings settings;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param settings current reference to the Aperture settings service in the current application context.
     */
    @Inject
    public ApertureJiraInstallTask(final ApertureSettings settings) {

        this.settings = settings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getBuildNumber() {

        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortDescription() {

        return "Installs and sets up Developer Central Aperture for the first time.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Message> doUpgrade() {

        LOG.info("Installing Aperture from Developer Central @ PNNL for the first time.");

        final UserManager userManager = ComponentAccessor.getUserManager();
        final ApplicationUser administrator = userManager.getUserByName("admin");
        if (Objects.nonNull(administrator)) {
            settings.setApertureUser(administrator);
            settings.setApertureEmailAddress(administrator.getEmailAddress());
        }

        final FieldLayoutManager layoutManager = ComponentAccessor.getFieldLayoutManager();
        final FieldLayoutScheme fieldLayoutScheme = layoutManager.createFieldLayoutScheme("Aperture Field Configuration Scheme", "Generated field config for an Aperture JIRA Project.");
        final FieldScreenFactory screenFactory = ComponentAccessor.getComponent(FieldScreenFactory.class);
        final IssueTypeScreenScheme issueTypeScreenScheme = screenFactory.createIssueTypeScreenScheme();
        issueTypeScreenScheme.setName("Aperture Issue Type Screen Scheme");
        issueTypeScreenScheme.setDescription("Generated IssueTypeScreenScheme for Aperture @ PNNL");

        final ErrorCollection errors = new SimpleErrorCollection();
        final Map<String, String> issueTypeSchemeValues = new HashMap<>();
        for (final IssueTypeSchema issueTypeSchema : AbstractIssueTypeSchema.getIssueTypeSchemas(settings)) {
            final IssueType issueType = issueTypeSchema.installIssueType(errors);
            issueTypeSchema.installCustomFields(issueType, errors);

            final FieldLayout fieldLayout = issueTypeSchema.installFieldConfigurations(issueType, fieldLayoutScheme, errors);
            layoutManager.createFieldLayoutSchemeEntity(fieldLayoutScheme, issueType.getId(), fieldLayout.getId());

            FieldScreenScheme screenScheme = issueTypeSchema.installScreens(issueType, errors);
            configureScreenToScheme(issueType, issueTypeScreenScheme, screenScheme);
            issueTypeSchemeValues.put(issueType.getName(), issueType.getId());
        }

        final IssueTypeSchemeManager issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager();
        final List<String> issueTypeIdList = new ArrayList<>(issueTypeSchemeValues.values());
        final FieldConfigScheme issueTypeScheme = issueTypeSchemeManager.create("Aperture Issue Type Scheme", "IssueTypeScheme for Aperture@PNNL", issueTypeIdList);
        issueTypeSchemeManager.setDefaultValue(issueTypeScheme.getOneAndOnlyConfig(), issueTypeSchemeValues.get(ApertureConstants.IT_SOFTWARE_PROJECT_REQUEST));

        settings.putPluginSetting(ApertureConstants.APERTURE_ISSUE_TYPE_SCHEME, String.valueOf(issueTypeScheme.getId()));
        settings.putPluginSetting(ApertureConstants.APERTURE_FIELD_CONFIG_SCHEME, String.valueOf(fieldLayoutScheme.getId()));
        settings.putPluginSetting(ApertureConstants.APERTURE_SCREEN_SCHEME, String.valueOf(issueTypeScreenScheme.getId()));

        installWorkflowScheme(issueTypeSchemeValues);
        preconfigureApplicationLinks();
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPluginKey() {

        return "gov.pnnl.aperture.aperture";
    }

    /**
     * Auto-detect and assign any existing application links in the current JIRA environment based on their type.
     */
    private void preconfigureApplicationLinks() {

        for (final ApplicationLink al : settings.getSystemApplicationLinks()) {
            if (al.isPrimary()) {
                final ApplicationType linkType = al.getType();
                if (linkType instanceof BambooApplicationType) {
                    settings.setApplicationLink(ApertureSettings.ProjectService.BAMBOO, al.getId());
                } else if (linkType instanceof ConfluenceApplicationType) {
                    settings.setApplicationLink(ApertureSettings.ProjectService.CONFLUENCE, al.getId());
                } else if (linkType instanceof FishEyeCrucibleApplicationType) {
                    settings.setApplicationLink(ApertureSettings.ProjectService.CRUCIBLE, al.getId());
                } else if (linkType instanceof JiraApplicationType) {
                    settings.setApplicationLink(ApertureSettings.ProjectService.JIRA, al.getId());
                } else if (linkType instanceof BitbucketApplicationType) {
                    settings.setApplicationLink(ApertureSettings.ProjectService.BITBUCKET, al.getId());
                }
            }
        }
    }

    private void installWorkflowScheme(final Map<String, String> issueTypeScheme) {

        // install a new workflow scheme for the actual aperture workflows to be installed into //
        final WorkflowSchemeManager wfSchemeManager = ComponentAccessor.getWorkflowSchemeManager();
        final AssignableWorkflowScheme.Builder builder = wfSchemeManager.assignableBuilder();
        builder.setName("Aperture Workflow Scheme");
        builder.setDescription("Generated workflow scheme for the Aperture project.");
        final AssignableWorkflowScheme defaultScheme = wfSchemeManager.getDefaultWorkflowScheme();
        builder.setDefaultWorkflow(defaultScheme.getActualDefaultWorkflow());

        JiraWorkflow wf = importWorkflowXML("/gov/pnnl/aperture/workflows/aperture_workflow.xml", "Aperture Automation Workflow", "Automatically imported workflow for Aperture Automation tasks from issues.");
        builder.setMapping(issueTypeScheme.get(ApertureConstants.IT_SIMPLE_PROJECT_REQUEST), wf.getName());
        builder.setMapping(issueTypeScheme.get(ApertureConstants.IT_SOFTWARE_PROJECT_REQUEST), wf.getName());
        settings.setWorkflowConfiguration(getDefaultWorkflowConfiguration(wf));


        wf = importWorkflowXML("/gov/pnnl/aperture/workflows/feedback_workflow.xml", "Aperture Feedback Workflow", "Automatically imported workflow for Aperture feedback.");
        builder.setMapping(issueTypeScheme.get(ApertureConstants.IT_FEEDBACK_REQUEST), wf.getName());

        wf = importWorkflowXML("/gov/pnnl/aperture/workflows/support_workflow.xml", "Aperture Support Workflow", "Automatically imported workflow for Aperture support.");
        builder.setMapping(issueTypeScheme.get(ApertureConstants.IT_DEVELOPER_CENTRAL_SUPPORT), wf.getName());

        final AssignableWorkflowScheme workflowScheme = wfSchemeManager.createScheme(builder.build());
        settings.putPluginSetting(ApertureConstants.APERTURE_WORKFLOW_SCHEME, String.valueOf(workflowScheme.getId()));
    }

    private WorkflowConfiguration getDefaultWorkflowConfiguration(final JiraWorkflow wf) {

        final WorkflowConfiguration config = new WorkflowConfiguration();
        config.setWorkflow(wf);
        final Collection<ActionDescriptor> workflowActions = wf.getAllActions();
        for (final ActionDescriptor action : workflowActions) {
            final String actionId = String.valueOf(action.getName());
            if ("Schedule".equals(actionId)) {
                config.setStartAction(action);
            } else if ("Action Needed".equals(actionId)) {
                config.setTriageAction(action);
            } else if ("Closed".equals(actionId) || "Done".equals(actionId)) {
                config.setFinishAction(action);
            }
        }
        final ResolutionManager resolutionManager = ComponentAccessor.getComponent(ResolutionManager.class);
        for (final Resolution resolution : resolutionManager.getResolutions()) {
            if ("Closed".equals(resolution.getName()) || "Done".equals(resolution.getName())) {
                config.setFinishStatus(resolutionManager.getDefaultResolution());
            }
        }
        return config;
    }

    private void configureScreenToScheme(final IssueType type, final IssueTypeScreenScheme itss, final FieldScreenScheme fss) {

        final FieldScreenFactory screenFactory = ComponentAccessor.getComponent(FieldScreenFactory.class);
        final FieldScreenSchemeManager fssm = ComponentAccessor.getComponent(FieldScreenSchemeManager.class);

        IssueTypeScreenSchemeEntity entity = screenFactory.createIssueTypeScreenSchemeEntity();
        entity.setFieldScreenScheme(fss);
        entity.setIssueTypeScreenScheme(itss);
        entity.setIssueTypeId(type.getId());
        itss.addEntity(entity);

        // add default mapping if we don't it breaks the project when it's used //
        entity = screenFactory.createIssueTypeScreenSchemeEntity();
        entity.setIssueTypeId(null);
        entity.setIssueTypeScreenScheme(itss);
        entity.setFieldScreenScheme(fssm.getFieldScreenScheme(FieldScreenSchemeManager.DEFAULT_FIELD_SCREEN_SCHEME_ID));
        itss.addEntity(entity);
    }

    private JiraWorkflow importWorkflowXML(final String workflowXMLResource, final String name, final String description) {

        try {
            final ApplicationUser apertureUser = settings.getApertureUser();
            final WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
            final WorkflowDescriptor workflowDescriptor = WorkflowLoader.load(getWorkflowXMLInputStream(workflowXMLResource), true);
            final ConfigurableJiraWorkflow newWorkflow = new ConfigurableJiraWorkflow(name, workflowDescriptor, workflowManager);
            newWorkflow.setDescription(description);
            workflowManager.createWorkflow(apertureUser, newWorkflow);
            return newWorkflow;
        } catch (InvalidWorkflowDescriptorException e) {
            String message = "Invalid workflow XML: " + e.getMessage();
            LOG.error(message, e);
            throw new IllegalStateException(message, e);
        } catch (IOException e) {
            String message = "Error loading workflow.";
            LOG.error(message, e);
            throw new IllegalStateException(message, e);
        } catch (SAXException e) {
            String message = "Error parsing workflow XML: " + e.getMessage();
            LOG.error(message, e);
            throw new IllegalStateException(message, e);
        } catch (WorkflowException e) {
            String message = "Error saving workflow: " + e.getMessage();
            LOG.error(message, e);
            throw new IllegalStateException(message, e);
        }
    }

    private InputStream getWorkflowXMLInputStream(final String resource) {

        if (isNotBlank(resource)) {
            return ClassLoaderUtils.getResourceAsStream(resource, ApertureJiraInstallTask.class);
        }
        throw new IllegalStateException("Failed to get Workflow XML input stream due to empty resource value.");
    }
}
