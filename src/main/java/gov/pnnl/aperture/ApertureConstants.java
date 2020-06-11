package gov.pnnl.aperture;

/**
 * @author Developer Central @ PNNL
 */
public class ApertureConstants {

    /**
     * Issue Type name for general support issue types within Aperture.
     */
    public static final String IT_DEVELOPER_CENTRAL_SUPPORT = "Developer Central Support";
    /**
     * Issue Type name for non-software project issue types within Aperture.
     */
    public static final String IT_SIMPLE_PROJECT_REQUEST = "Business Project Request";
    /**
     * Issue Type name for new software project issue types within Aperture.
     */
    public static final String IT_SOFTWARE_PROJECT_REQUEST = "Software Project Request";
    /**
     * Issue Type name for feedback and improvements to Developer Central within Aperture.
     */
    public static final String IT_FEEDBACK_REQUEST = "Developer Central Feedback";
    /**
     * Internal plugin-setting key for the Aperture issue type scheme.
     * <p>
     *
     * @see gov.pnnl.aperture.updates.ApertureJiraInstallTask
     */
    public static final String APERTURE_ISSUE_TYPE_SCHEME = "__issue_type_scheme";
    /**
     * Internal plugin-setting key for the Aperture workflow scheme.
     * <p>
     *
     * @see gov.pnnl.aperture.updates.ApertureJiraInstallTask
     */
    public static final String APERTURE_WORKFLOW_SCHEME = "__workflow_scheme";
    /**
     * Internal plugin-setting key for the Aperture issue type screen scheme.
     * <p>
     *
     * @see gov.pnnl.aperture.updates.ApertureJiraInstallTask
     */
    public static final String APERTURE_SCREEN_SCHEME = "__screen_scheme";
    /**
     * Internal plugin-setting key for the Aperture field configuration scheme.
     * <p>
     *
     * @see gov.pnnl.aperture.updates.ApertureJiraInstallTask
     */
    public static final String APERTURE_FIELD_CONFIG_SCHEME = "__field_config_scheme";

    private ApertureConstants() {
        // private constructor for constant class //
    }
}
