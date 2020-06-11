package gov.pnnl.aperture.workflow.function;

import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.atlassian.jira.util.collect.MapBuilder;
import com.atlassian.plugin.util.Assertions;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import gov.pnnl.aperture.ApertureSettings;

import java.util.Map;

/**
 * Typical work-flow factory for the {@link ProjectCreatorPostFunction} component.
 * <p>
 *
 * @author Developer Central @ PNNL
 * @see ProjectCreatorPostFunction
 */
public class ProjectCreatorFunctionFactory extends AbstractWorkflowPluginFactory implements WorkflowPluginFunctionFactory {

    /**
     * Reference to the current aperture settings component in this application context.
     */
    private final ApertureSettings apertureSettings;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param aperture reference to the current Aperture service implementation in the current context.
     * @throws IllegalArgumentException if the aperture service provided is null.
     */
    public ProjectCreatorFunctionFactory(final ApertureSettings aperture) {

        Assertions.notNull("ApertureService", aperture);
        this.apertureSettings = aperture;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void getVelocityParamsForInput(final Map<String, Object> velocityParams) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void getVelocityParamsForEdit(final Map<String, Object> velocityParams, final AbstractDescriptor descriptor) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void getVelocityParamsForView(final Map<String, Object> velocityParams, final AbstractDescriptor descriptor) {

        // add the custom field configured in aperture for project keys //
        velocityParams.put("cf", apertureSettings.getCustomField(ApertureSettings.CustomField.PROJECT_KEY));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, ?> getDescriptorParams(final Map conditionParameters) {

        final MapBuilder<String, Object> builder = MapBuilder.newBuilder();
        return builder.toMap();
    }
}
