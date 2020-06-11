package gov.pnnl.aperture.project;

import com.atlassian.core.util.DateUtils;
import com.atlassian.extras.common.org.springframework.util.StringUtils;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import gov.pnnl.aperture.ApertureProjectSettings;
import org.apache.log4j.Logger;

import java.text.ParseException;
import java.util.Date;
import java.util.Objects;

public class PnnlProjectSettings implements ApertureProjectSettings {
    /**
     * Reference to the current logger for this class instance.
     */
    private static final transient Logger LOG = Logger.getLogger(PnnlProjectSettings.class);
    /**
     *
     */
    private static final String PROJECT_SETTING_PREFIX = "gov.pnnl.aperture";
    /**
     *
     */
    private final PluginSettings pluginSettings;
    /**
     *
     */
    private final String projectKey;

    public PnnlProjectSettings(final PluginSettingsFactory pluginSettingsFactory, final String projectKey) {

        this.pluginSettings = pluginSettingsFactory.createSettingsForKey(projectKey);
        this.projectKey = projectKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Project getProject() {

        final ProjectManager projectManager = ComponentAccessor.getProjectManager();
        return projectManager.getProjectByCurrentKey(projectKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Date getCreatedAt() {

        return getDateSetting("created.at");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Date setCreatedAt(final Date createdAt) {

        return putDateSetting("created.at", createdAt);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled() {

        return Boolean.parseBoolean(getPluginSetting("enabled"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setEnabled(final boolean enabled) {

        return Boolean.parseBoolean(putPluginSetting("enabled", Boolean.toString(enabled)));
    }

    /**
     * @param settingKey
     * @return
     */
    private Date getDateSetting(final String settingKey) {

        final String dateStr = getPluginSetting(settingKey);
        if (StringUtils.hasText(dateStr)) {
            try {
                return DateUtils.ISO8601DateFormat.parse(dateStr);
            } catch (ParseException e) {
                LOG.warn(String.format("Failed to parse date project setting:'%s' with value:'%s'", settingKey, dateStr), e);
            }
        }
        return null;
    }

    /**
     * @param settingKey
     * @param settingDate
     * @return
     */
    private Date putDateSetting(final String settingKey, final Date settingDate) {

        final Date previousValue = getDateSetting(settingKey);
        if (Objects.isNull(settingDate)) {
            removePluginSetting(settingKey);
        } else {
            putPluginSetting(settingKey, DateUtils.formatDateISO8601(settingDate));
        }
        return previousValue;
    }

    /**
     *
     */
    private String getPluginSetting(final String settingKey) {

        return (String) pluginSettings.get(String.format("%s/%s", PROJECT_SETTING_PREFIX, settingKey));
    }

    /**
     *
     */
    private String putPluginSetting(final String settingKey, final String settingValue) {

        if (StringUtils.hasText(settingKey)) {
            return (String) pluginSettings.put(String.format("%s/%s", PROJECT_SETTING_PREFIX, settingKey), settingValue);
        }
        return null;
    }

    /**
     *
     */
    private String removePluginSetting(final String settingKey) {

        if (StringUtils.hasText(settingKey)) {
            return (String) pluginSettings.remove(String.format("%s/%s", PROJECT_SETTING_PREFIX, settingKey));
        }
        return null;
    }
}
