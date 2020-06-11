package gov.pnnl.aperture.webwork.action;

import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.notification.NotificationSchemeManager;
import com.atlassian.jira.permission.PermissionSchemeManager;
import com.atlassian.jira.project.type.ProjectType;
import com.atlassian.jira.project.type.ProjectTypeManager;
import com.atlassian.jira.scheme.Scheme;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.jira.workflow.AssignableWorkflowScheme;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.plugin.PluginInformation;
import gov.pnnl.aperture.ApertureSettings;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * JIRA Web Action for configuring project defaults per service.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public class ApertureProjectDefaults extends JiraWebActionSupport {

    /**
     * Reference to the current Aperture Settings service in the current application context.
     */
    private final ApertureSettings apertureSettings;

    public ApertureProjectDefaults(final ApertureSettings apertureSettings) {

        this.apertureSettings = apertureSettings;
    }

    public List<ProjectType> getProjectTypes() {

        final ProjectTypeManager projectTypeManager = ComponentAccessor.getComponent(ProjectTypeManager.class);
        return projectTypeManager.getAllAccessibleProjectTypes();
    }


    public List<Scheme> getPermissionSchemes() {

        final PermissionSchemeManager permissionSchemeManager = ComponentAccessor.getPermissionSchemeManager();
        return permissionSchemeManager.getSchemeObjects();
    }

    public List<Scheme> getNotificationSchemes() {

        final NotificationSchemeManager notificationSchemeManager = ComponentAccessor.getNotificationSchemeManager();
        return notificationSchemeManager.getSchemeObjects();
    }

    public List<IssueTypeScreenScheme> getScreenSchemes() {

        IssueTypeScreenSchemeManager issueTypeScreenSchemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager();
        return new ArrayList<>(issueTypeScreenSchemeManager.getIssueTypeScreenSchemes());
    }

    public List<JiraWorkflow> getWorkflows() {

        final WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
        return new ArrayList<>(workflowManager.getActiveWorkflows());
    }

    public List<AssignableWorkflowScheme> getWorkflowSchemes() {

        final WorkflowSchemeManager workflowManager = ComponentAccessor.getComponent(WorkflowSchemeManager.class);
        final List<AssignableWorkflowScheme> schemes = new ArrayList<>();
        schemes.add(workflowManager.getDefaultWorkflowScheme());
        for (AssignableWorkflowScheme awfs : workflowManager.getAssignableSchemes()) {
            schemes.add(awfs);
        }
        return schemes;
    }

    public List<FieldLayoutScheme> getFieldConfigurationSchemes() {

        final FieldLayoutManager layoutManager = ComponentAccessor.getFieldLayoutManager();
        return layoutManager.getFieldLayoutSchemes();
    }

    public List<FieldConfigScheme> getIssueTypeSchemes() {

        final IssueTypeSchemeManager issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager();
        return issueTypeSchemeManager.getAllSchemes();
    }

    public PluginInformation getPluginInfo() {

        return apertureSettings.getPluginInfo();
    }

    public Map<String, Object> getDefaults(final String serviceName, final String type) {

        if (StringUtils.hasText(serviceName)) {
            final String normalizedName = serviceName.toUpperCase().trim();
            final ApertureSettings.ProjectService projectService = ApertureSettings.ProjectService.valueOf(normalizedName);
            return apertureSettings.getServiceConfiguration(projectService, type);
        }
        return Collections.emptyMap();
    }

    public String doConfluence() {

        log.debug("processing:doConfluence();");
        final HttpServletRequest httpRequest = getHttpRequest();
        final Map<String, Object> config = new HashMap<>();
        config.put("username", httpRequest.getParameter("username"));
        config.put("password", httpRequest.getParameter("password"));
        apertureSettings.setServiceConfiguration(ApertureSettings.ProjectService.CONFLUENCE, "", config);
        return getRedirect("/secure/admin/ConfigureApertureDefaults.jspa");
    }

    public String doJira() {

        log.debug("processing:doJira();");
        final Map<String, Object> config = new HashMap<>();
        final HttpServletRequest httpRequest = getHttpRequest();
        final String projectType = getLogicalString(httpRequest, "project_type_key");
        config.put("issue_scheme", getLogicalString(httpRequest, "issue_scheme"));
        config.put("screen_scheme", getLogicalString(httpRequest, "screen_scheme"));
        config.put("field_scheme", getLogicalString(httpRequest, "field_scheme"));
        config.put("workflow_scheme", getLogicalString(httpRequest, "workflow_scheme"));
        config.put("notify_scheme", getLogicalString(httpRequest, "notify_scheme"));
        config.put("perm_scheme", getLogicalString(httpRequest, "perm_scheme"));
        apertureSettings.setServiceConfiguration(ApertureSettings.ProjectService.JIRA, projectType, config);
        return getRedirect("/secure/admin/ConfigureApertureDefaults.jspa");
    }

    protected String getLogicalString(final HttpServletRequest request, final String paramName) {

        if (StringUtils.hasText(paramName)) {
            final String value = request.getParameter(paramName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
