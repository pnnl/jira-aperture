package gov.pnnl.aperture.workflow.validator;

import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginValidatorFactory;
import com.atlassian.jira.util.collect.MapBuilder;
import com.atlassian.plugin.util.Assertions;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import gov.pnnl.aperture.ApertureSettings;

import java.util.Map;

/**
 * Typical work-flow factory for the {@link ProjectKeyValidator} component.
 * <p>
 *
 * @author Developer Central @ PNNL
 * @see ProjectKeyValidator
 */
public class ProjectKeyValidatorFactory extends AbstractWorkflowPluginFactory implements WorkflowPluginValidatorFactory {

    /**
     * Reference to the current aperture settings component in this application context.
     */
    private final ApertureSettings apertureSettings;

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param apertureSettings reference to the current Aperture service implementation in the current context.
     * @throws IllegalArgumentException if the aperture service provided is null.
     */
    public ProjectKeyValidatorFactory(final ApertureSettings apertureSettings) {

        Assertions.notNull("ApertureSettings", apertureSettings);
        this.apertureSettings = apertureSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void getVelocityParamsForInput(final Map<String, Object> velocityParams) {

        // nothing to do here yet //
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void getVelocityParamsForEdit(final Map<String, Object> velocityParams, final AbstractDescriptor descriptor) {

        // nothing to do here yet //
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

        MapBuilder<String, Object> builder = MapBuilder.newBuilder();
        return builder.toMap();
    }
}
