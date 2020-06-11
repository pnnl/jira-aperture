package gov.pnnl.aperture.project.services;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.spi.application.IconizedType;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.link.RemoteIssueLinkService;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.bc.project.component.ProjectComponentManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.link.RemoteIssueLink;
import com.atlassian.jira.issue.link.RemoteIssueLinkBuilder;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.plugin.util.Assertions;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import gov.pnnl.aperture.JiraUtils;
import gov.pnnl.aperture.ProjectServiceHandler;
import org.apache.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;

/**
 * Base class providing some common functionality for creating both internal and external project services.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public abstract class AbstractProjectServiceHandler implements ProjectServiceHandler {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(AbstractProjectServiceHandler.class);
    /**
     * Reference to the current aperture service in the current application context.
     */
    private final Aperture aperture;
    /**
     * Reference to the current aperture settings component in the current application context.
     */
    private final ApertureSettings apertureSettings;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param aperture reference to the current aperture service.
     * @param settings reference the current implementation of the aperture settings service.
     * @throws IllegalArgumentException if either aperture or settings parameters are <code>null</code>.
     */
    public AbstractProjectServiceHandler(final Aperture aperture, final ApertureSettings settings) {

        Assertions.notNull("Aperture", aperture);
        Assertions.notNull("ApertureSettings", settings);
        this.aperture = aperture;
        this.apertureSettings = settings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIdle(final String projectKey) {

        return false;
    }

    /**
     * Gets the current Aperture Service reference in the current context.
     * <p>
     *
     * @return non-null reference to the aperture service.
     */
    protected Aperture getAperture() {

        return aperture;
    }

    /**
     * Gets the current Aperture Service reference in the current context.
     * <p>
     *
     * @return non-null reference to the aperture service.
     */
    protected ApertureSettings getApertureSettings() {

        return apertureSettings;
    }

    protected void addComponentTo(final MutableIssue issue) {

        final I18nHelper helper = ComponentAccessor.getI18nHelperFactory().getInstance(Locale.getDefault());
        final String componentName = helper.getText(getServiceType().getI18nNameKey());
        for (final ProjectComponent component : issue.getComponents()) {
            if (component.getName().equals(componentName)) {
                return;
            }
        }

        final ProjectComponentManager componentManager = ComponentAccessor.getProjectComponentManager();
        final List<ProjectComponent> componentList = new ArrayList<>(issue.getComponents());
        for (final ProjectComponent component : componentManager.findAllForProject(issue.getProjectId())) {
            if (component.getName().equals(componentName)) {
                componentList.add(component);
            }
        }
        issue.setComponent(componentList);
        final IssueService issueService = ComponentAccessor.getIssueService();
        final ErrorCollection errors = new SimpleErrorCollection();
        final Map<String, Object> holder = new HashMap<>();
        final IssueService.UpdateValidationResult result = new IssueService.UpdateValidationResult(issue, errors, holder);
        final IssueService.IssueResult update = issueService.update(apertureSettings.getApertureUser(), result);
        final ErrorCollection updateErrors = update.getErrorCollection();
        if (updateErrors.hasAnyErrors()) {
            JiraUtils.addErrorCollectionAsComments(issue, String.format("addComponentTo(%s)", componentName), updateErrors);
        }
    }

    /**
     * Utility method for adding web links to a JIRA issue.
     * <p>
     * This method is intended to simplify adding web links to the software service that is associate with the issue
     * request at hand. More information on what this method does and how can be found on this atlassian documentation
     * site.
     * https://developer.atlassian.com/jiradev/jira-platform/other/guide-jira-remote-issue-links/fields-in-remote-issue-links
     * The following link could be used to improve this method over time.
     *
     * @param issue    the JIRA issue to add a new web link to
     * @param linkText the display text describing the link
     * @param url      the actual URL of the web link
     * @return <code>null</code> if the link was successfully made otherwise collection of errors describing why
     */
    protected ErrorCollection addServiceLinkTo(final MutableIssue issue, final String linkText, final String url) {

        final RemoteIssueLinkService linkService = ComponentAccessor.getComponentOfType(RemoteIssueLinkService.class);
        final ApertureSettings settings = getApertureSettings();
        final ApplicationUser apertureUser = settings.getApertureUser();
        final ApplicationLink link = settings.getApplicationLink(getServiceType());
        final RemoteIssueLinkService.RemoteIssueLinkListResult existingLinks = linkService.getRemoteIssueLinksForIssue(apertureUser, issue);
        final ErrorCollection errorCollection = new SimpleErrorCollection();

        final String code = Integer.toHexString(linkText.hashCode());
        if (existingLinks.getRemoteIssueLinks() != null) {
            for (final RemoteIssueLink remoteLink : existingLinks.getRemoteIssueLinks()) {
                if (remoteLink.getTitle().equalsIgnoreCase(linkText)) {
                    RemoteIssueLinkBuilder builder = new RemoteIssueLinkBuilder(remoteLink);
                    builder.applicationType(link.getType().getI18nKey());
                    builder.globalId(String.format("appLinkId=%s&key=%s&code=%s", link.getId().get(), settings.getProjectKeyFor(issue), code));
                    builder.title(linkText);
                    builder.url(url);
                    final RemoteIssueLinkService.UpdateValidationResult updated = linkService.validateUpdate(apertureUser, builder.build());
                    if (updated.isValid()) {
                        LOG.info(String.format("Remote issue link is valid: updating link:'%s'", updated.getRemoteIssueLink()));
                        linkService.update(apertureUser, updated);
                        return errorCollection;
                    }
                }
            }
        }

        final RemoteIssueLinkBuilder builder = new RemoteIssueLinkBuilder();
        builder.issueId(issue.getId());
        builder.title(linkText);
        builder.url(url);
        if (link != null) {
            if (link.getType() instanceof IconizedType){
                final IconizedType it = (IconizedType) link.getType();
                final URI iconUri = it.getIconUri();
                if (Objects.nonNull(iconUri)){
                    try {
                        builder.iconUrl(String.valueOf(iconUri.toURL().toExternalForm()));
                    } catch (MalformedURLException e) {
                        LOG.debug(String.format("Malformed URL in iconURI for service (%s)", getServiceType()),e);
                    }
                }
            }
            builder.applicationType(link.getType().getI18nKey());
            builder.applicationName(link.getName());
            builder.globalId(String.format("appLinkId=%s&key=%s&code=%s", link.getId().get(), settings.getProjectKeyFor(issue), code));
        }
        final RemoteIssueLinkService.CreateValidationResult vc = linkService.validateCreate(apertureUser, builder.build());
        if (vc.isValid()) {
            LOG.info(String.format("Remote issue link is valid: creating link:'%s'", vc.getRemoteIssueLink()));
            linkService.create(apertureUser, vc);
            return errorCollection;
        }
        LOG.warn(String.format("Remote issue link is not valid for issue:'%s'", issue.getKey()));
        JiraUtils.addErrorCollectionAsComments(issue, String.format("addServiceLinkTo(%s):invalid", linkText), vc.getErrorCollection());
        errorCollection.addErrorCollection(vc.getErrorCollection());
        return errorCollection;
    }
}
