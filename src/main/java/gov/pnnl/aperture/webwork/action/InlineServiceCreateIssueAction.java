package gov.pnnl.aperture.webwork.action;

import com.atlassian.core.util.DateUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.issuetabpanel.IssueAction;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.JiraUtils;
import com.atlassian.jira.util.JiraVelocityHelper;
import com.atlassian.velocity.VelocityManager;
import com.opensymphony.util.TextUtils;
import webwork.action.ServletActionContext;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Developer Central @ PNNL
 */
public class InlineServiceCreateIssueAction implements IssueAction {

    private final Date epoch;
    private final String resourceName;
    private final Map<String, Object> velocityContext = new HashMap<>();

    public InlineServiceCreateIssueAction(final Date epoch, final String resourceName, final Map<String, Object> context) {

        this.epoch = epoch;
        this.resourceName = resourceName;
        velocityContext.putAll(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Date getTimePerformed() {

        return epoch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHtml() {

        final VelocityManager velocityManager = ComponentAccessor.getVelocityManager();
        final ApplicationProperties applicationProperties = ComponentAccessor.getApplicationProperties();
        final String baseUrl = applicationProperties.getString(APKeys.JIRA_BASEURL);
        final String webworkEncoding = applicationProperties.getString(APKeys.JIRA_WEBWORK_ENCODING);
        final String basePath = "/gov/pnnl/aperture/templates/issue_actions/";
        final JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext();
        final I18nHelper.BeanFactory i18nHelperFactory = ComponentAccessor.getI18nHelperFactory();
        final I18nHelper i18n = i18nHelperFactory.getInstance(authContext.getLoggedInUser());

        velocityContext.putIfAbsent("i18n", i18n);
        velocityContext.putIfAbsent("textutils", new TextUtils());
        velocityContext.putIfAbsent("jirautils", new JiraUtils());
        velocityContext.putIfAbsent("velocityhelper", new JiraVelocityHelper(ComponentAccessor.getFieldManager()));
        velocityContext.putIfAbsent("dateutils", new DateUtils(i18n.getDefaultResourceBundle()));
        velocityContext.putIfAbsent("applicationProperties", ComponentAccessor.getApplicationProperties());
        velocityContext.putIfAbsent("req", ServletActionContext.getRequest());

        final Object obj = velocityContext.get("issue");
        if (obj instanceof Issue) {
            final Issue issue = (Issue) obj;
            velocityContext.putIfAbsent("assignee", issue.getAssignee());
            velocityContext.putIfAbsent("reported", issue.getReporter());
        }
        return velocityManager.getEncodedBody(basePath, resourceName, baseUrl, webworkEncoding, velocityContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDisplayActionAllTab() {

        return false;
    }
}
