package gov.pnnl.aperture.project;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.PluginInformation;
import com.atlassian.plugin.StateAware;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import javax.inject.Inject;

/**
 * @author Developer Central @ PNNL
 */
abstract class AbstractAperturePlugin implements LifecycleAware, StateAware {

    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    protected AbstractAperturePlugin(final @ComponentImport PluginSettingsFactory pluginSettingsFactory) {

        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    protected PluginSettingsFactory getPluginSettingsFactory() {

        return pluginSettingsFactory;
    }


    /**
     * {@inheritDoc}
     */
    public PluginSettings getSettings() {

        return pluginSettingsFactory.createGlobalSettings();
    }

    public PluginInformation getPluginInfo() {

        final PluginAccessor pluginAccessor = ComponentAccessor.getPluginAccessor();
        final Plugin plugin = pluginAccessor.getPlugin("gov.pnnl.aperture.aperture");
        return plugin.getPluginInformation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enabled() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disabled() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {

    }
}
