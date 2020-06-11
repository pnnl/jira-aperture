package gov.pnnl.aperture.workflow.function;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;
import gov.pnnl.aperture.ApertureScheduler;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * JIRA Work-flow function for initiating the automatic project creation and supporting services.
 * <p>
 * THe primary goal of this work-flow function is to post enough information to the {@link ApertureScheduler} service to
 * allow the creation of the project and it's supporting services to occur in the background and not block the web UI
 * for the user.
 *
 * @author Developer Central @ PNNL
 */
public class ProjectCreatorPostFunction extends AbstractJiraFunctionProvider {
    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(ProjectCreatorPostFunction.class);
    /**
     * Reference to the current JIRA plug-in scheduler service in this application context.
     */
    private final ApertureScheduler apertureScheduler;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param apertureScheduler reference to the current Aperture Scheduler in the current context.
     * @throws IllegalArgumentException if aperture scheduler service provided is null.
     */
    @Inject
    public ProjectCreatorPostFunction(@NotNull final ApertureScheduler apertureScheduler) {

        Assert.notNull(apertureScheduler, "ApertureScheduler reference cannot be null.");
        this.apertureScheduler = apertureScheduler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final Map transientVars, final Map args, final PropertySet ps) throws WorkflowException {

        final MutableIssue issue = getIssue(transientVars);
        try {
            LOG.info(String.format("Scheduling project creation for [%s]", issue.getKey()));
            apertureScheduler.scheduleNewProject(issue);
        } catch (RuntimeException rte) {
            throw new WorkflowException(rte);
        }
    }
}
