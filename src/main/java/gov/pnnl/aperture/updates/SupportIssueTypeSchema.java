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
public class SupportIssueTypeSchema extends AbstractIssueTypeSchema {

    public SupportIssueTypeSchema(ApertureSettings settings) {
        super(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IssueType installIssueType(ErrorCollection errors) {

        final AvatarManager am = ComponentAccessor.getAvatarManager();
        final IssueTypeManager itm = ComponentAccessor.getComponent(IssueTypeManager.class);
        final Avatar avatar;
        try {
            avatar = am.create(IconType.ISSUE_TYPE_ICON_TYPE, IconOwningObjectId.from(0), new AvatarImageProvider("/gov/pnnl/aperture/images/support-issuetype-avatar.png"));
        } catch (IOException ex) {
            errors.addErrorMessage(ex.toString());
            throw new RuntimeException("Failed to install issue type avatar", ex);
        }
        return itm.createIssueType(ApertureConstants.IT_DEVELOPER_CENTRAL_SUPPORT, "Requests for support on Developer Central services.", avatar.getId());
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
        createScreen.setName(String.format("%s New Screen", ApertureConstants.IT_DEVELOPER_CENTRAL_SUPPORT));
        createScreen.setDescription(String.format("Screen for new %s issue types", ApertureConstants.IT_DEVELOPER_CENTRAL_SUPPORT));

        final FieldScreenTab screenTab = createScreen.addTab("Field Tab");
        screenTab.addFieldScreenLayoutItem("summary");
        screenTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.PROJECT_KEY).getId());
        screenTab.addFieldScreenLayoutItem("priority");
        screenTab.addFieldScreenLayoutItem("components");
        screenTab.addFieldScreenLayoutItem("description");

        final FieldScreen viewScreen = screenFactory.createScreen();
        viewScreen.setName(String.format("%s View Screen", ApertureConstants.IT_DEVELOPER_CENTRAL_SUPPORT));
        viewScreen.setDescription(String.format("Screen for viewing %s issue types", ApertureConstants.IT_DEVELOPER_CENTRAL_SUPPORT));

        final FieldScreenTab viewScreenTab = viewScreen.addTab("Field Tab");
        viewScreenTab.addFieldScreenLayoutItem("reporter");
        viewScreenTab.addFieldScreenLayoutItem("assignee");
        viewScreenTab.addFieldScreenLayoutItem("summary");
        viewScreenTab.addFieldScreenLayoutItem(settings.getCustomField(ApertureSettings.CustomField.PROJECT_KEY).getId());
        viewScreenTab.addFieldScreenLayoutItem("priority");
        viewScreenTab.addFieldScreenLayoutItem("components");
        viewScreenTab.addFieldScreenLayoutItem("description");
        viewScreenTab.addFieldScreenLayoutItem("timetracking");
        viewScreenTab.addFieldScreenLayoutItem("resolution");
        viewScreenTab.addFieldScreenLayoutItem("issuelinks");
        viewScreenTab.addFieldScreenLayoutItem("labels");

        final FieldScreenScheme fieldScreenScheme = screenFactory.createFieldScreenScheme();
        fieldScreenScheme.setName(String.format("%s Screen Scheme", ApertureConstants.IT_DEVELOPER_CENTRAL_SUPPORT));
        fieldScreenScheme.setDescription(String.format("Screen Scheme for %s issue types", ApertureConstants.IT_DEVELOPER_CENTRAL_SUPPORT));

        final FieldScreenSchemeItem createAssociation = screenFactory.createFieldScreenSchemeItem();
        createAssociation.setFieldScreen(createScreen);
        createAssociation.setFieldScreenScheme(fieldScreenScheme);
        createAssociation.setIssueOperation(IssueOperations.CREATE_ISSUE_OPERATION);
        fieldScreenScheme.addFieldScreenSchemeItem(createAssociation);

        final FieldScreenSchemeItem editAssociation = screenFactory.createFieldScreenSchemeItem();
        editAssociation.setFieldScreen(createScreen);
        editAssociation.setFieldScreenScheme(fieldScreenScheme);
        editAssociation.setIssueOperation(IssueOperations.EDIT_ISSUE_OPERATION);

        final FieldScreenSchemeItem viewAssociation = screenFactory.createFieldScreenSchemeItem();
        viewAssociation.setFieldScreen(viewScreen);
        viewAssociation.setFieldScreenScheme(fieldScreenScheme);
        viewAssociation.setIssueOperation(IssueOperations.VIEW_ISSUE_OPERATION);

        fieldScreenScheme.addFieldScreenSchemeItem(editAssociation);
        fieldScreenScheme.addFieldScreenSchemeItem(createAssociation);
        fieldScreenScheme.addFieldScreenSchemeItem(viewAssociation);

        return fieldScreenScheme;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<String> requiredFieldIds() {

        final Collection<String> fieldIds = new ArrayList<>();
        fieldIds.add("summary");
        fieldIds.add("components");
        fieldIds.add("description");
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
        fieldIds.add("components");
        fieldIds.add("description");
        fieldIds.add("timetracking");
        fieldIds.add("resolution");
        fieldIds.add("issuelinks");
        fieldIds.add("labels");
        fieldIds.add("assignee");
        fieldIds.add("reporter");
        return fieldIds;
    }
}
