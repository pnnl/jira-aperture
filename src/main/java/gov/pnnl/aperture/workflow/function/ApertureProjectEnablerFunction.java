package gov.pnnl.aperture.workflow.function;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.atlassian.plugin.util.Assertions;
import com.opensymphony.module.propertyset.PropertySet;
import gov.pnnl.aperture.ApertureProjectSettings;
import gov.pnnl.aperture.ApertureSettings;

import java.util.Map;

/**
 * This is a work-flow post-function that can flag a JIRA project as Aperture enabled.
 * <p>
 * This function should fire whenever a project service provisioning has been completed. Failing to flag a JIRA project
 * as not Aperture capable can disable functionality for end-users.
 *
 * @author Developer Central @ PNNL
 * @see ApertureProjectSettings#isEnabled()
 */
public class ApertureProjectEnablerFunction extends AbstractJiraFunctionProvider {

    /**
     * Reference to the current instance of ApertureSettings in the current application context.
     */
    private final ApertureSettings apertureSettings;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param apertureSettings reference to the current ApertureSettings service implementation in the current context.
     * @throws IllegalArgumentException if either of the aperture settings is <code>null</code>.
     */
    public ApertureProjectEnablerFunction(final ApertureSettings apertureSettings) {

        Assertions.notNull("ApertureSettings", apertureSettings);
        this.apertureSettings = apertureSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final Map variables, final Map arguments, final PropertySet propertySet) {

        final MutableIssue issue = getIssue(variables);
        final String projectKey = apertureSettings.getProjectKeyFor(issue);
        final ApertureProjectSettings settings = apertureSettings.getProjectSettings(projectKey);
        settings.setEnabled(true);
    }
}
