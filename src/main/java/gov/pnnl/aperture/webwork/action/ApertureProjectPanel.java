package gov.pnnl.aperture.webwork.action;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.plugin.projectpanel.impl.AbstractProjectTabPanel;
import com.atlassian.jira.project.browse.BrowseContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.plugin.ProjectPermissionKey;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import gov.pnnl.aperture.ApertureProjectSettings;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.JiraUtils;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * JIRA Project Tab panel for general viewing of Aperture based project information.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public class ApertureProjectPanel extends AbstractProjectTabPanel {

    private final ApertureSettings apertureSettings;

    public ApertureProjectPanel(final ApertureSettings apertureSettings) {

        Assert.notNull(apertureSettings, "Invalid Aperture Settings service reference.");
        this.apertureSettings = apertureSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, Object> createVelocityParams(final BrowseContext ctx) {
        final Map<String, Object> params = super.createVelocityParams(ctx);
        final ApertureProjectSettings projectSettings = apertureSettings.getProjectSettings(ctx.getProject().getKey());
        final boolean projectApertureCapable = projectSettings.isEnabled();
        params.put("apertureEnabled", projectApertureCapable);
        if (projectApertureCapable) {
            params.put("projectLinks", JiraUtils.getProjectLinks(params, ctx.getProject()));
        }
        return params;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean showPanel(final BrowseContext ctx) {

        final PermissionManager permissionManager = ComponentAccessor.getPermissionManager();
        final UserManager userManager = ComponentAccessor.getUserManager();
        final ApplicationUser user = ctx.getUser() == null ? null : userManager.getUserByName(ctx.getUser().getName());
        final ProjectPermissionKey canViewDevTools = ProjectPermissions.WORK_ON_ISSUES;
        final ProjectPermissionKey canAdministerProjects = ProjectPermissions.ADMINISTER_PROJECTS;
        final boolean hasAdminPermission = permissionManager.hasPermission(canAdministerProjects, ctx.getProject(), user);
        final boolean hasDeveloperPermission = permissionManager.hasPermission(canViewDevTools, ctx.getProject(), user);
        return hasAdminPermission || hasDeveloperPermission;
    }
}
