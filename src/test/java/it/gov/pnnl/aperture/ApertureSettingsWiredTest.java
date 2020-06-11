package it.gov.pnnl.aperture;

import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;
import com.atlassian.sal.api.ApplicationProperties;
import gov.pnnl.aperture.ApertureSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AtlassianPluginsTestRunner.class)
public class ApertureSettingsWiredTest {
    private final ApplicationProperties applicationProperties;
    private final ApertureSettings apertureSettings;

    public ApertureSettingsWiredTest(final ApplicationProperties applicationProperties, final ApertureSettings apertureSettings) {
        this.applicationProperties = applicationProperties;
        this.apertureSettings = apertureSettings;
    }

    @Test
    public void testApertureInitialization() {

        assertEquals("Unexpected default value for delete project duration", "2w", apertureSettings.getDeleteDuration());

        final ApplicationUser apertureUser = apertureSettings.getApertureUser();
        assertNotNull("Default Aperture user should not be null.", apertureUser);
        assertEquals("Unexpected default value for Aperture user.", "admin", apertureUser.getName());
    }
}