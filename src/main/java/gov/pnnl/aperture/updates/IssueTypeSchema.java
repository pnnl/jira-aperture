package gov.pnnl.aperture.updates;

import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.util.ErrorCollection;

import java.util.Collection;

/**
 * @author kobo523
 */
public interface IssueTypeSchema {

    IssueType installIssueType(final ErrorCollection errors);

    Collection<CustomField> installCustomFields(final IssueType type, final ErrorCollection errors);

    FieldLayout installFieldConfigurations(final IssueType type, final FieldLayoutScheme fieldLayoutScheme, final ErrorCollection errors);

    FieldScreenScheme installScreens(final IssueType type, final ErrorCollection errors);
}
