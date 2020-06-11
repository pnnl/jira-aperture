package gov.pnnl.aperture.webwork.action;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueTabPanel3;
import com.atlassian.jira.plugin.issuetabpanel.GetActionsRequest;
import com.atlassian.jira.plugin.issuetabpanel.IssueAction;
import com.atlassian.jira.plugin.issuetabpanel.ShowPanelRequest;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureConstants;
import gov.pnnl.aperture.ApertureSettings;

import java.util.*;

/**
 * @author kobo523
 */
public class IssueAdminTabPanel extends AbstractIssueTabPanel3 {

    private final Aperture aperture;
    private final ApertureSettings apertureSettings;

    public IssueAdminTabPanel(final Aperture aperture, final ApertureSettings apertureSettings) {

        this.aperture = aperture;
        this.apertureSettings = apertureSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean showPanel(final ShowPanelRequest spr) {

        final ApplicationUser remoteUser = spr.remoteUser();
        if (spr.isAnonymous() || remoteUser == null) {
            return false;
        }

        final Issue issue = spr.issue();
        final GlobalPermissionManager globalPermissionManager = ComponentAccessor.getGlobalPermissionManager();
        final IssueType issueTypeObject = issue.getIssueType();
        boolean isSysAdmin = globalPermissionManager.hasPermission(GlobalPermissionKey.SYSTEM_ADMIN, remoteUser);
        final String issueType = issueTypeObject.getName();
        boolean correctIssueType = ApertureConstants.IT_SOFTWARE_PROJECT_REQUEST.equals(issueType)
            || ApertureConstants.IT_SIMPLE_PROJECT_REQUEST.equals(issueType);
        return isSysAdmin && correctIssueType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IssueAction> getActions(final GetActionsRequest gar) {

        final List<IssueAction> actions = new ArrayList<>();
        for (ApertureSettings.ProjectService service : ApertureSettings.ProjectService.values()) {
            final ApplicationLink applicationLink = apertureSettings.getApplicationLink(service);
            if (service.isApplicationLinkRequired() && applicationLink == null) {
                // this service is not properly configured as it requires an application link and doesn't have one yet
                continue;
            }

            final Map<String, Object> velocityContext = new HashMap<>();
            final Issue issue = gar.issue();
            velocityContext.put("service", service);
            velocityContext.put("applink", applicationLink);
            velocityContext.put("issue", issue);
            velocityContext.put("projectKey", apertureSettings.getProjectKeyFor(issue));
            actions.add(new InlineServiceCreateIssueAction(new Date(), "service_action.vm.html", velocityContext));
        }
        return actions;
    }

}
