package gov.pnnl.aperture;

import com.atlassian.annotations.PublicSpi;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Protocol for providing a layer of abstraction for Aperture and the various software services for projects it can
 * support.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
@PublicSpi
@Component
public interface ProjectServiceHandler {

    /**
     * Gets the type of service this handler...handles.
     * <p>
     *
     * @return non <code>null</code> service type value for this handler.
     */
    @NotNull
    ApertureSettings.ProjectService getServiceType();

    /**
     * Creates the software service using properties of the JIRA issue and current operating environment.
     * <p>
     * The issue passed into this method provides all the user provided inputs to create the project as requested by
     * the end-user. Implementations should only create the initial project structure and reasonable defaults.
     *
     * @param issue       the JIRA issue for the service request.
     * @param environment the current runtime environment.
     * @return an error collection of any issues that arose while creating this service.
     * @throws IllegalArgumentException if the <code>issue</code> provided is <code>null</code>
     */
    ErrorCollection createService(final @NotNull MutableIssue issue, final Map<String, Serializable> environment);

    /**
     * Modifies the access control list of users for a given project.
     * <p>
     *
     * @param projectKey the project key that is used to identify with the external service.
     * @param mode       the mode in which to use the provided user list on the remove service.
     * @param role       the logical role to apply to the user as part of this method operation.
     * @param userList   list of users to be affected by this action.
     * @return an error collection of any issues that arose while modifying the users access this service.
     * @throws IllegalArgumentException if the <code>projectKey</code> provided is <code>null</code>
     */
    ErrorCollection modifyUsers(final @NotNull String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<ApplicationUser> userList);

    /**
     * Modifies the access control list of groups for a given project.
     * <p>
     *
     * @param projectKey the project key that is used to identify with the external service.
     * @param mode       the mode in which to use the provided user list on the remove service.
     * @param role       the logical role to apply to the user group as part of this method operation.
     * @param groupList  list of user groups to be affected by this action.
     * @return an error collection of any issues that arose while modifying the users access this service.
     * @throws IllegalArgumentException if the <code>projectKey</code> provided is <code>null</code>
     */
    ErrorCollection modifyGroups(final @NotNull String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<Group> groupList);

    /**
     * Removes an existing service as means to rollback in the event of a failure.
     * <p>
     * Since all the software services supported by Aperture are not within JIRA itself. The ability to create a
     * pseudo-transaction for creating all or none of the services if need be. The caller of this method should be able
     * to decide if a particular service is option and may not require a rollback.
     *
     * @param projectKey  the project key that is used to remove the external service.
     * @param environment the current runtime environment.
     * @return an error collection of any issues that arose while destroying this service.
     * @throws IllegalArgumentException if the <code>projectKey</code> provided is <code>null</code>
     */
    ErrorCollection destroyService(final @NotNull String projectKey, final Map<String, Serializable> environment);

    /**
     * Support method to check if the remote service for the given project exists.
     * <p>
     * Callers of this method should assume some sort of API call to a RESTful end-point or something similar. It is
     * recommended that the {@link IllegalStateException} when the remote service is not accessible.
     *
     * @param projectKey the project key that is used to identify this project in the external service.
     * @return <code>true</code> if the service has been provisioned for this project.
     * @throws IllegalArgumentException if the <code>projectKey</code> provided is <code>null</code>
     */
    boolean isServiceAvailable(final @NotNull String projectKey);

    /**
     * Is this project <em>idle</em> or otherwise inactive?.
     * <p>
     * This method allows for each service to determine it's own idle state. This is because some services may have
     * simple resolutions or more complex ones. This can all be encapsulated in the implementation of the service
     * handler.
     *
     * @param projectKey the project key that is used to identify this project in the external service.
     * @return <code>true</code> if the service has been determined to be idle.
     * @throws IllegalArgumentException if the <code>projectKey</code> provided is <code>null</code>
     */
    boolean isIdle(final @NotNull String projectKey);

}
