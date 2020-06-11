package gov.pnnl.aperture.updates;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.GlobalIssueContext;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.customfields.CustomFieldSearcher;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.OrderableField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.fields.layout.field.*;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.I18nHelper;
import gov.pnnl.aperture.ApertureSettings;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.springframework.util.Assert;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * @author Developer Central @ PNNL
 */
public abstract class AbstractIssueTypeSchema implements IssueTypeSchema {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(SoftwareProjectIssueTypeSchema.class);
    /**
     *
     */
    protected final ApertureSettings settings;
    public AbstractIssueTypeSchema(final @Nonnull ApertureSettings settings) {

        Assert.notNull(settings, "Cannot create an issue type scheme with a null aperture settings reference.");
        this.settings = settings;
    }

    public static List<IssueTypeSchema> getIssueTypeSchemas(final @Nonnull ApertureSettings settings) {

        final List<IssueTypeSchema> installTasks = new ArrayList<>();
        installTasks.add(new SoftwareProjectIssueTypeSchema(settings));
        installTasks.add(new NonSoftwareProjectIssueTypeSchema(settings));
        installTasks.add(new SupportIssueTypeSchema(settings));
        installTasks.add(new FeedbackIssueTypeSchema(settings));
        return installTasks;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FieldLayout installFieldConfigurations(IssueType type, FieldLayoutScheme fieldLayoutScheme, ErrorCollection errors) {

        final FieldLayoutManager layoutManager = ComponentAccessor.getFieldLayoutManager();
        final Collection<String> allowableFieldIds = allowableFieldIds();
        final Collection<String> requiredFieldIds = requiredFieldIds();

        final EditableFieldLayout fieldLayoutCopy = createFieldLayout(layoutManager.getEditableDefaultFieldLayout());
        fieldLayoutCopy.setName(String.format("Aperture %s Field Configuration", type.getName()));
        fieldLayoutCopy.setDescription(String.format("Generatated Aperture Field Configuration for %s", type.getName()));
        applyFieldConfigurations(fieldLayoutCopy, allowableFieldIds, requiredFieldIds);

        final EditableFieldLayout updatedEFL = layoutManager.storeAndReturnEditableFieldLayout(fieldLayoutCopy);
        layoutManager.createFieldLayoutSchemeEntity(fieldLayoutScheme, type.getId(), updatedEFL.getId());
        return updatedEFL;
    }

    protected abstract Collection<String> allowableFieldIds();

    protected abstract Collection<String> requiredFieldIds();

    protected EditableFieldLayout createFieldLayout(final EditableDefaultFieldLayout efl) {
        // this is essnetially how to copy the default field configuration //
        try {
            final String className = "com.atlassian.jira.issue.fields.layout.field.EditableFieldLayoutImpl";
            final Class<?> clazz = Class.forName(className);
            final Class<? extends EditableFieldLayout> subClass = clazz.asSubclass(EditableFieldLayout.class);
            Constructor<? extends EditableFieldLayout> ctr = subClass.getConstructor(GenericValue.class, List.class);
            return ctr.newInstance(null, efl.getFieldLayoutItems());
        } catch (ClassNotFoundException | InvocationTargetException | IllegalArgumentException | IllegalAccessException | InstantiationException | SecurityException | NoSuchMethodException error) {
            throw new RuntimeException(error);
        }
    }

    protected void applyFieldConfigurations(final EditableFieldLayout layout, final Collection<String> allowableFieldIds, final Collection<String> requiredFieldIds) {

        for (final FieldLayoutItem fli : layout.getFieldLayoutItems()) {
            final OrderableField field = fli.getOrderableField();
            if (allowableFieldIds.contains(field.getId())) {
                try {
                    layout.show(fli);
                } catch (IllegalArgumentException ignored) {
                    LOG.trace("failed to show field layout", ignored);
                }
                if (requiredFieldIds.contains(field.getId())) {
                    try {
                        layout.makeRequired(fli);
                    } catch (IllegalArgumentException ignored) {
                        LOG.trace("failed to make required field layout", ignored);
                    }
                } else {
                    try {
                        layout.makeOptional(fli);
                    } catch (IllegalArgumentException ignored) {
                        LOG.trace("failed to make optional field layout", ignored);
                    }
                }
            } else {
                try {
                    layout.hide(fli);
                } catch (IllegalArgumentException ignored) {
                    LOG.trace("failed to hide field layout", ignored);
                }
            }
        }
    }

    protected CustomField addCustomField(final String i18nName, final String i18nDesc, final String type, final ApertureSettings.CustomField apetureType, String... options) {

        if (settings.getCustomField(apetureType) == null) {
            final I18nHelper i18n = ComponentAccessor.getI18nHelperFactory().getInstance(Locale.ENGLISH);
            final CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
            final String actualJiraType = String.format("com.atlassian.jira.plugin.system.customfieldtypes:%s", type);
            final CustomFieldType cfType = customFieldManager.getCustomFieldType(actualJiraType);
            final CustomFieldSearcher cfSearcher = customFieldManager.getDefaultSearcher(cfType);
            final List<JiraContextNode> contexts = new ArrayList<>();
            contexts.add(GlobalIssueContext.getInstance());
            final List<IssueType> types = new ArrayList<>(1);
            final CustomField cf;
            try {
                cf = customFieldManager.createCustomField(i18n.getText(i18nName), i18n.getText(i18nDesc), cfType, cfSearcher, contexts, types);
            } catch (GenericEntityException error) {
                throw new IllegalArgumentException("Failed to create custom field.", error);
            }
            final FieldConfigSchemeManager fieldConfigSchemeManager = ComponentAccessor.getFieldConfigSchemeManager();
            fieldConfigSchemeManager.createDefaultScheme(cf, contexts);
            if (options != null) {
                final OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
                final FieldConfig relevantConfig = cf.getRelevantConfig(GlobalIssueContext.getInstance());
                final Options configOptions = optionsManager.getOptions(relevantConfig);
                final List<Option> rootOptions = configOptions.getRootOptions();
                final Option rootOption = rootOptions.isEmpty() ? null : rootOptions.get(0);
                final Long parentOption = rootOption == null ? null : rootOption.getOptionId();
                for (int idx = 0; idx < options.length; idx++) {
                    optionsManager.createOption(relevantConfig, parentOption, Integer.valueOf(idx).longValue(), i18n.getText(options[idx]));
                }
            }
            settings.setCustomField(apetureType, cf);
            return cf;
        }
        return null;
    }

}
