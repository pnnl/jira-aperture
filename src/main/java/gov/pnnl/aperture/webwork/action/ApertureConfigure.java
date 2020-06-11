package gov.pnnl.aperture.webwork.action;

import com.atlassian.applinks.api.ApplicationId;
import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.notification.NotificationSchemeManager;
import com.atlassian.jira.permission.PermissionSchemeManager;
import com.atlassian.jira.scheme.Scheme;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.plugin.PluginInformation;
import com.opensymphony.workflow.loader.ActionDescriptor;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.WorkflowConfiguration;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Developer Central @ PNNL
 */

public class ApertureConfigure extends JiraWebActionSupport {

    /**
     * Generated Serial Version UID.
     */
    private static final long serialVersionUID = -2012012988986893401L;
    /**
     * Reference to the current logger instance for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ApertureConfigure.class);

    private final Aperture aperture;
    private final ApertureSettings apertureSettings;

    public ApertureConfigure(final Aperture aperture, final ApertureSettings apertureSettings) {

        this.apertureSettings = apertureSettings;
        this.aperture = aperture;
    }

    public ApertureSettings getSettings() {

        return apertureSettings;
    }

    public URI getApertureUserAvatarURL() {
        final ApertureSettings settings = getSettings();

        final ApplicationUser user = settings.getApertureUser();
        if (user != null) {
            final AvatarService avatarService = ComponentAccessor.getAvatarService();
            return avatarService.getAvatarURL(getLoggedInUser(), user);
        }
        return null;
    }


    public List<Scheme> getPermissionSchemes() {

        final PermissionSchemeManager permissionSchemeManager = ComponentAccessor.getPermissionSchemeManager();
        return permissionSchemeManager.getSchemeObjects();
    }

    public List<Scheme> getNotificationSchemes() {

        final NotificationSchemeManager notificationSchemeManager = ComponentAccessor.getNotificationSchemeManager();
        return notificationSchemeManager.getSchemeObjects();
    }

    public List<Resolution> getResolutions() {

        ResolutionManager resolutionManager = ComponentAccessor.getComponent(ResolutionManager.class);
        return resolutionManager.getResolutions();
    }

    public List<IssueTypeScreenScheme> getScreenSchemes() {

        IssueTypeScreenSchemeManager issueTypeScreenSchemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager();
        return new ArrayList<>(issueTypeScreenSchemeManager.getIssueTypeScreenSchemes());
    }

    public List<JiraWorkflow> getWorkflows() {

        final WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
        return new ArrayList<>(workflowManager.getWorkflows());
    }

    public List<FieldConfigScheme> getIssueTypeSchemes() {

        final IssueTypeSchemeManager issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager();
        return issueTypeSchemeManager.getAllSchemes();
    }

    public List<CustomField> getCustomFields() {

        final CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
        return cfm.getCustomFieldObjects();
    }

    public PluginInformation getPluginInfo() {

        return apertureSettings.getPluginInfo();
    }

    public List<ApplicationLink> getAppLinks() {

        return apertureSettings.getSystemApplicationLinks();
    }

    public ApplicationLink getApplicationLink(final String serviceKey) {

        ApertureSettings.ProjectService service = ApertureSettings.ProjectService.valueOf(serviceKey);
        return apertureSettings.getApplicationLink(service);
    }

    public List<ApertureSettings.ProjectService> getRemoteServices() {

        return Arrays.asList(ApertureSettings.ProjectService.values());
    }

    public List<ApertureSettings.CustomField> getConfigurationFields() {

        return Arrays.asList(ApertureSettings.CustomField.values());
    }

    public CustomField getCustomField(final String customFieldKey) {

        ApertureSettings.CustomField field = ApertureSettings.CustomField.valueOf(customFieldKey);
        return apertureSettings.getCustomField(field);
    }

    public JiraWorkflow getWorkflow() {

        final WorkflowConfiguration wfConfig = apertureSettings.getWorkflowConfiguration();
        return wfConfig.getWorkflow();
    }

    public List<ActionDescriptor> getWorkflowActions() {

        final List<ActionDescriptor> actions = new ArrayList<>();
        final JiraWorkflow workflow = getWorkflow();
        if (workflow != null) {
            actions.addAll(workflow.getAllActions());
        }
        return actions;
    }

    public Resolution getFinishResolution() {

        final WorkflowConfiguration wfConfig = apertureSettings.getWorkflowConfiguration();
        return wfConfig.getFinishStatus();
    }

    public ActionDescriptor getAction(final String actionId) {

        final WorkflowConfiguration wfConfig = apertureSettings.getWorkflowConfiguration();
        if ("start_action".equalsIgnoreCase(actionId)) {
            return wfConfig.getStartAction();
        } else if ("triage_action".equalsIgnoreCase(actionId)) {
            return wfConfig.getTriageAction();
        } else if ("done_action".equalsIgnoreCase(actionId)) {
            return wfConfig.getFinishAction();
        }
        return null;
    }

    public String doInput() {

        LOG.debug("processing:doInput();");
        final CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
        final HttpServletRequest httpRequest = getHttpRequest();
        for (final ApertureSettings.CustomField fieldParameter : getConfigurationFields()) {
            final String customFieldId = httpRequest.getParameter(fieldParameter.name());
            if (StringUtils.hasText(customFieldId)) {
                LOG.debug(String.format("Linking custom-field:%s to paramter:%s", customFieldId, fieldParameter));
                final CustomField customField = cfm.getCustomFieldObject(Long.valueOf(customFieldId));
                apertureSettings.setCustomField(fieldParameter, customField);
            }
        }
        return getRedirect("/secure/admin/ConfigureAperture.jspa");
    }

    public String doApplinks() {

        LOG.debug("processing:doApplinks();");
        final HttpServletRequest httpRequest = getHttpRequest();
        for (ApertureSettings.ProjectService projectService : getRemoteServices()) {
            final String applicationLinkId = httpRequest.getParameter(projectService.name());
            if (StringUtils.hasText(applicationLinkId)) {
                LOG.debug(String.format("Linking application:%s to service:%s", applicationLinkId, projectService));
                apertureSettings.setApplicationLink(projectService, new ApplicationId(applicationLinkId));
            } else {
                LOG.debug(String.format("Unlinking application-link for service:%s", projectService));
                apertureSettings.setApplicationLink(projectService, null);
            }
        }
        return getRedirect("/secure/admin/ConfigureAperture.jspa");
    }

    public String doGeneral() {

        LOG.debug("processing:doGeneral();");
        final UserManager userManager = ComponentAccessor.getUserManager();
        final HttpServletRequest httpRequest = getHttpRequest();
        apertureSettings.setApertureUser(userManager.getUserByName(httpRequest.getParameter("apertureUser")));
        apertureSettings.setApertureEmailAddress(httpRequest.getParameter("emailAddress"));
        apertureSettings.setDeleteDuration(httpRequest.getParameter("deleteDuration"));
        apertureSettings.setProjectIdleDuration(httpRequest.getParameter("idleDuration"));
        return getRedirect("/secure/admin/ConfigureAperture.jspa");
    }

    public String doWorkflow() {

        LOG.debug("processing:doWorkflow();");
        final HttpServletRequest httpRequest = getHttpRequest();
        final WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
        final WorkflowConfiguration wfConfig = apertureSettings.getWorkflowConfiguration();
        final JiraWorkflow existingWorkflow = wfConfig.getWorkflow();
        final String workflowName = httpRequest.getParameter("apr_wf");
        final JiraWorkflow workflow = workflowManager.getWorkflow(workflowName);

        if (existingWorkflow != null && !existingWorkflow.getName().equals(workflowName)) {
            LOG.debug(String.format("Changing aperture workflow from:'%s' => '%s'", existingWorkflow.getName(), workflowName));
            wfConfig.setWorkflow(workflow);
            wfConfig.setStartAction(null);
            wfConfig.setTriageAction(null);
            wfConfig.setFinishAction(null);
            apertureSettings.setWorkflowConfiguration(wfConfig);
            return getRedirect("/secure/admin/ConfigureAperture.jspa");
        }

        if (workflow != null) {
            LOG.info(String.format("Changing workflow action configurations for workflow %s", workflow.getName()));
            final String startAction = httpRequest.getParameter("start_action");
            final String triageAction = httpRequest.getParameter("triage_action");
            final String doneAction = httpRequest.getParameter("done_action");
            for (final ActionDescriptor action : workflow.getAllActions()) {
                final String actionId = String.valueOf(action.getId());
                if (actionId.equals(startAction)) {
                    LOG.debug(String.format("setting workflow start action:%s", action.getName()));
                    wfConfig.setStartAction(action);
                } else if (actionId.equals(triageAction)) {
                    LOG.debug(String.format("setting workflow triage action:%s", action.getName()));
                    wfConfig.setTriageAction(action);
                } else if (actionId.equals(doneAction)) {
                    LOG.debug(String.format("setting workflow finished action:%s", action.getName()));
                    final ResolutionManager resolutionManager = ComponentAccessor.getComponent(ResolutionManager.class);
                    wfConfig.setFinishStatus(resolutionManager.getResolution(httpRequest.getParameter("done_status")));
                    wfConfig.setFinishAction(action);
                }
            }
            wfConfig.setWorkflow(workflow);
            apertureSettings.setWorkflowConfiguration(wfConfig);
        }
        return getRedirect("/secure/admin/ConfigureAperture.jspa");
    }
}
