package gov.pnnl.aperture;


import com.atlassian.annotations.PublicApi;
import com.atlassian.jira.project.Project;

import java.util.Date;

/**
 * Thin abstraction layer to consistently get and set project settings using JIRA's SAL framework.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
@PublicApi
public interface ApertureProjectSettings {

    /**
     * JIRA Project reference for whom this settings instance applies to.
     * <p>
     *
     * @return current project for this settings object is bound by.
     */
    Project getProject();

    /**
     * Gets the created date for when this JIRA project was created.
     * <p>
     * <strong>NOTE:</strong> This value is not always accurate as JIRA does not track this property internally and is
     * only set by Aperture for projects it makes. So this value cannot be garunteed to 100% accurate.
     *
     * @return the created date when the project was approximately created in JIRA; <code>null</code> if unknown.
     */
    Date getCreatedAt();

    /**
     * Modifies the Aperture created at for the current project.
     * <p>
     *
     * @param createdAt the new created at date and timestamp for when this Aperture project was created.
     * @return previous value set for the created at value for this project.
     */
    Date setCreatedAt(final Date createdAt);

    /**
     * Is the JIRA project currently flagged to support Aperture features?
     * <p>
     * In most cases new projects that are created with Aperture are automatically enabled as it's highly likely that
     * all the connected developer services are configured such that Aperture will work properly with other non-JIRA
     * services related to the project.
     * <p>
     * However, for legacy projects that were created manually without Aperture they will have to manually enabled by
     * system administrators to allow Aperture functionality to be allowed by normal project administrators.
     *
     * @return <code>true</code> if the project is capable of Aperture project administration functions
     */
    boolean isEnabled();

    /**
     * Modifies the Aperture capability for a given JIRA project by key with a new value.
     * <p>
     *
     * @param enabled the new status for Aperture capability for the project.
     * @return previous value set for the enabled flag for this project.
     */
    boolean setEnabled(final boolean enabled);

}
