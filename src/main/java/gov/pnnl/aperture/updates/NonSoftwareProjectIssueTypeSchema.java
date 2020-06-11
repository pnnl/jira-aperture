package gov.pnnl.aperture.updates;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.icon.IconOwningObjectId;
import com.atlassian.jira.icon.IconType;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.screen.*;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.operation.IssueOperations;
import com.atlassian.jira.util.ErrorCollection;
import gov.pnnl.aperture.ApertureConstants;
import gov.pnnl.aperture.ApertureSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Developer Central @ PNNL
 */
public class NonSoftwareProjectIssueTypeSchema extends AbstractIssueTypeSchema {

    public NonSoftwareProjectIssueTypeSchema(ApertureSettings settings) {
        super(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IssueType installIssueType(final ErrorCollection errors) {

        final AvatarManager am = ComponentAccessor.getAvatarManager();
        final IssueTypeManager itm = ComponentAccessor.getComponent(IssueTypeManager.class);
        final Avatar avatar;
        try {
            avatar = am.create(IconType.ISSUE_TYPE_ICON_TYPE, IconOwningObjectId.from(0), new AvatarImageProvider("/gov/pnnl/aperture/images/wiki-issuetype-avatar.png"));
        } catch (IOException ex) {
            errors.addErrorMessage(ex.toString());
            throw new RuntimeException("Failed to install issue type avatar", ex);
        }
        return itm.createIssueType(ApertureConstants.IT_SIMPLE_PROJECT_REQUEST, "Requests new services to support a new non-software project", avatar.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<CustomField> installCustomFields(IssueType type, ErrorCollection errors) {

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FieldScreenScheme installScreens(IssueType type, ErrorCollection errors) {

        final FieldScreenFactory screenFactory = ComponentAccessor.getComponent(FieldScreenFactory.class);
        final FieldScreen createScreen = screenFactory.createScreen();
        createScreen.setName(String.format("%s New Screen", ApertureConstants.IT_SIMPLE_PROJECT_REQUEST));
        createScreen.setDescription(String.format("Screen for new %s issue types", ApertureConstants.IT_SIMPLE_PROJECT_REQUEST));

        final FieldScreenTab createTab = createScreen.addTab("Field Tab");
        createTab.addFieldScreenLayoutItem("summary");
        createTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.PROJECT_KEY).getId());
        createTab.addFieldScreenLayoutItem("description");
        createTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.VISBILITY).getId());
        createTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.GROUPS).getId());
        createTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.USERS).getId());
        createTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.CATEGORY).getId());

        final FieldScreen viewScreen = screenFactory.createScreen();
        viewScreen.setName(String.format("%s View Screen", ApertureConstants.IT_SIMPLE_PROJECT_REQUEST));
        viewScreen.setDescription(String.format("Screen for viewing %s issue types", ApertureConstants.IT_SIMPLE_PROJECT_REQUEST));

        final FieldScreenTab viewScreenTab = viewScreen.addTab("Field Tab");
        viewScreenTab.addFieldScreenLayoutItem("reporter");
        viewScreenTab.addFieldScreenLayoutItem("assignee");
        viewScreenTab.addFieldScreenLayoutItem("summary");
        viewScreenTab.addFieldScreenLayoutItem("components");
        viewScreenTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.PROJECT_KEY).getId());
        viewScreenTab.addFieldScreenLayoutItem("description");
        viewScreenTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.VISBILITY).getId());
        viewScreenTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.GROUPS).getId());
        viewScreenTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.USERS).getId());
        viewScreenTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.CATEGORY).getId());
        viewScreenTab.addFieldScreenLayoutItem("timetracking");
        viewScreenTab.addFieldScreenLayoutItem("resolution");
        viewScreenTab.addFieldScreenLayoutItem("issuelinks");
        viewScreenTab.addFieldScreenLayoutItem("labels");

        final FieldScreenScheme screenScheme = screenFactory.createFieldScreenScheme();
        screenScheme.setName(String.format("%s Screen Scheme", ApertureConstants.IT_SIMPLE_PROJECT_REQUEST));
        screenScheme.setDescription(String.format("Screen Scheme for %s issue types", ApertureConstants.IT_SIMPLE_PROJECT_REQUEST));

        final FieldScreenSchemeItem createAssociation = screenFactory.createFieldScreenSchemeItem();
        createAssociation.setFieldScreen(createScreen);
        createAssociation.setFieldScreenScheme(screenScheme);
        createAssociation.setIssueOperation(IssueOperations.CREATE_ISSUE_OPERATION);
        screenScheme.addFieldScreenSchemeItem(createAssociation);

        final FieldScreenSchemeItem editAssociation = screenFactory.createFieldScreenSchemeItem();
        editAssociation.setFieldScreen(createScreen);
        editAssociation.setFieldScreenScheme(screenScheme);
        editAssociation.setIssueOperation(IssueOperations.EDIT_ISSUE_OPERATION);

        final FieldScreenSchemeItem viewAssociation = screenFactory.createFieldScreenSchemeItem();
        viewAssociation.setFieldScreen(viewScreen);
        viewAssociation.setFieldScreenScheme(screenScheme);
        viewAssociation.setIssueOperation(IssueOperations.VIEW_ISSUE_OPERATION);

        screenScheme.addFieldScreenSchemeItem(editAssociation);
        screenScheme.addFieldScreenSchemeItem(createAssociation);
        screenScheme.addFieldScreenSchemeItem(viewAssociation);
        return screenScheme;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<String> requiredFieldIds() {

        final Collection<String> fieldIds = new ArrayList<>();
        fieldIds.add("summary");
        fieldIds.add(settings.getCustomField(ApertureSettings.CustomField.PROJECT_KEY).getId());
        fieldIds.add(settings.getCustomField(ApertureSettings.CustomField.VISBILITY).getId());
        return fieldIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<String> allowableFieldIds() {

        final Collection<String> fieldIds = new ArrayList<>();
        fieldIds.add("summary");
        fieldIds.add(settings.getCustomField(ApertureSettings.CustomField.PROJECT_KEY).getId());
        fieldIds.add("description");
        fieldIds.add(settings.getCustomField(ApertureSettings.CustomField.VISBILITY).getId());
        fieldIds.add(settings.getCustomField(ApertureSettings.CustomField.GROUPS).getId());
        fieldIds.add(settings.getCustomField(ApertureSettings.CustomField.USERS).getId());
        fieldIds.add(settings.getCustomField(ApertureSettings.CustomField.CATEGORY).getId());
        fieldIds.add("timetracking");
        fieldIds.add("resolution");
        fieldIds.add("issuelinks");
        fieldIds.add("labels");
        fieldIds.add("assignee");
        fieldIds.add("reporter");
        fieldIds.add("components");
        return fieldIds;
    }
}
