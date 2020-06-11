package gov.pnnl.aperture;

import com.atlassian.annotations.PublicApi;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.opensymphony.workflow.loader.ActionDescriptor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A simple JavaBean to represent the JIRA work-flow configuration parameters required for Aperture.
 * <p>
 * This class is intended to make the interface for {@link ApertureSettings} less cumbersome. The aperture service can
 * understand and work with this object in lieu of encapsulating the functionality of this class as primarily a data
 * structure.
 * <p>
 * This class also will open the possibility of using different work-flow automation configurations through aperture
 * without further cluttering the {@link ApertureSettings} interface.
 * <p>
 * There is currently no cross checking of the JIRA work-flow property with the other <em>action</em> properties of this
 * instance. So it is entirely possible for the JIRA work-flow property and the work-flow actions of the same instance
 * to be inconsistent. The helper method {@link #isConsistent()} is provided to assist with a basic concept of a
 * <em>consistent</em> work-flow configuration.
 *
 * @see ApertureSettings#getWorkflowConfiguration()
 */
@PublicApi
public class WorkflowConfiguration implements Serializable {

    /**
     * Generated Serial Version UID.
     */
    private static final long serialVersionUID = -2586583628276286663L;
    /**
     * Reference to the JIRA work-flow object for this configuration instance.
     */
    private transient JiraWorkflow workflow = null;
    /**
     * Reference to the action performed when aperture starting an automated process from a JIRA issue.
     */
    private ActionDescriptor startAction = null;
    /**
     * Reference to the work-flow action performed when aperture fails during an automated process from a JIRA issue.
     */
    private ActionDescriptor triageAction = null;
    /**
     * Reference to the work-flow action performed when aperture completes an automated process from a JIRA issue.
     */
    private ActionDescriptor finishAction = null;
    /**
     * The default resolution when invoking the finish work-flow action.
     */
    private Resolution finishStatus = null;

    /**
     * Gets the current JIRA work-flow reference for this configuration instance.
     * <p>
     *
     * @return the current JIRA work-flow reference for this configuration instance; can be <code>null</code>.
     */
    public JiraWorkflow getWorkflow() {

        return workflow;
    }

    /**
     * Sets the JIRA work-flow reference on this instance with a new value.
     * <p>
     *
     * @param workflow the new JIRA work-flow reference for this instance to set; can be <code>null</code>.
     */
    public void setWorkflow(final JiraWorkflow workflow) {

        this.workflow = workflow;
    }

    /**
     * Gets the work-flow action to perform when Aperture is automating a process from a JIRA issue.
     * <p>
     * When Aperture is automatically performing a process initiated from a JIRA issue; this is the work-flow action to
     * perform when that process begins.
     *
     * @return the work-flow action to perform when beginning an automated process; can be <code>null</code>.
     */
    public ActionDescriptor getStartAction() {

        return startAction;
    }

    /**
     * Sets the JIRA work-flow start action on this instance with a new value.
     * <p>
     *
     * @param startAction the new JIRA work-flow start action for this instance to set; can be <code>null</code>.
     * @see #getStartAction()
     */
    public void setStartAction(final ActionDescriptor startAction) {

        this.startAction = startAction;
    }

    /**
     * Gets the work-flow action to perform when Aperture fails during an automated a process from a JIRA issue.
     * <p>
     * When Aperture is automatically performing a process initiated from a JIRA issue; this is the work-flow action to
     * perform when that process fails and requires human intervention to complete. This action could also be used when
     * the automated portion of Aperture is complete and the manual portion of a process needs to be completed
     * elsewhere.
     *
     * @return the work-flow action to perform when triaging an automated process; can be <code>null</code>.
     */
    public ActionDescriptor getTriageAction() {

        return triageAction;
    }

    /**
     * Sets the JIRA work-flow triage action on this instance with a new value.
     * <p>
     *
     * @param triageAction the new JIRA work-flow triage action for this instance to set; can be <code>null</code>.
     * @see #getTriageAction()
     */
    public void setTriageAction(final ActionDescriptor triageAction) {

        this.triageAction = triageAction;
    }

    /**
     * Gets the work-flow action to perform when Aperture completes an automated a process from a JIRA issue.
     * <p>
     * When Aperture is automatically performing a process initiated from a JIRA issue; this is the work-flow action to
     * perform when that process is successfully completed.
     *
     * @return the work-flow action to perform when finishing an automated process; can be <code>null</code>.
     */
    public ActionDescriptor getFinishAction() {

        return finishAction;
    }

    /**
     * Sets the JIRA work-flow finish action on this instance with a new value.
     * <p>
     *
     * @param finishAction the new JIRA work-flow finish action for this instance to set; can be <code>null</code>.
     * @see #getFinishAction()
     */
    public void setFinishAction(final ActionDescriptor finishAction) {

        this.finishAction = finishAction;
    }

    /**
     * Gets all the actions of this configuration instance as a list of actions.
     * <p>
     * This method is functionally equivalent to the follow Java code or similar.
     * <p>
     * <code>
     * Arrays.asList(wfconfig.getStartAction(), wfConfig.getFinishAction(), wfConfig.getTriageAction());
     * </code>
     *
     * @return
     */
    public List<ActionDescriptor> getActions() {

        return Arrays.asList(getStartAction(), getFinishAction(), getTriageAction());
    }

    /**
     * Gets the work-flow action resolution to use when Aperture completes an automated a process from a JIRA issue.
     * <p>
     * Using the Resolution status for invoking the {@link #getFinishAction()} work-flow allows the setting of the
     * resolution of the issue which is commonly set to <em>Fixed</em>.
     *
     * @return the work-flow action to perform when finishing an automated process; can be <code>null</code>.
     */
    public Resolution getFinishStatus() {

        return finishStatus;
    }

    /**
     * Sets the JIRA work-flow finish action resolution status on this instance with a new value.
     * <p>
     *
     * @param finishStatus the new resolution to use in conjunction with the finish action; can be <code>null</code>.
     * @see #getFinishAction()
     * @see #getFinishStatus()
     */
    public void setFinishStatus(final Resolution finishStatus) {

        this.finishStatus = finishStatus;
    }

    /**
     * Utility method to ensure consistency between the work-flow and action properties of this instance.
     * <p>
     * In order for this instance to be considered consistent the following criteria must all be met.
     * <ul>
     * <li>The work-flow and all action properties must not be <code>null</code></li>
     * <li>All action properties of this instance must be valid actions in the work-flow property</li>
     * </ul>
     *
     * @return <code>true</code> if the work-flow and action properties of this instance are <em>consistent</em>
     */
    public boolean isConsistent() {

        final JiraWorkflow wf = getWorkflow();
        if (wf != null) {
            final Set<Integer> actionIds = new TreeSet<>();
            for (final ActionDescriptor action : wf.getAllActions()) {
                actionIds.add(action.getId());
            }
            for (final ActionDescriptor action : getActions()) {
                if (action == null || !actionIds.contains(action.getId())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
