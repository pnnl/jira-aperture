package gov.pnnl.aperture.project.services;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationLinkRequestFactory;
import com.atlassian.applinks.api.ApplicationLinkResponseHandler;
import com.atlassian.applinks.api.CredentialsRequiredException;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import gov.pnnl.aperture.Aperture;
import gov.pnnl.aperture.ApertureSettings;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Abstract project service handler that uses RESTful calls to perform it's work.
 * <p>
 *
 * @author Developer Central @ PNNL
 */
public abstract class AbstractRestfulProjectServiceHandler extends AbstractProjectServiceHandler {

    /**
     * Logger reference for this class.
     */
    private static final transient Logger LOG = Logger.getLogger(AbstractRestfulProjectServiceHandler.class);

    /**
     * Default constructor for this class.
     * <p>
     *
     * @param aperture reference to the current aperture service.
     * @param settings reference the current implementation of the aperture settings service.
     * @throws IllegalArgumentException if either aperture or settings parameters are <code>null</code>.
     */
    public AbstractRestfulProjectServiceHandler(final Aperture aperture, final ApertureSettings settings) {

        super(aperture, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isServiceAvailable(final String projectKey) {

        final ApertureSettings.ProjectService serviceType = getServiceType();
        final ApertureSettings settings = getApertureSettings();
        if (serviceType != null) {
            final ApplicationLink link = settings.getApplicationLink(serviceType);
            LOG.debug(String.format("isServiceAvailable: communicating to URL:%s for service:%s", link.getRpcUrl(), serviceType));
            final ApplicationLinkRequestFactory factory = link.createAuthenticatedRequestFactory();
            try {
                return isServiceAvailable(projectKey, factory);
            } catch (ResponseException | CredentialsRequiredException ex) {
                LOG.fatal("Failed to check service availability due to error", ex);
                throw new IllegalStateException(ex);
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection modifyUsers(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<ApplicationUser> userList) {

        final ApertureSettings.ProjectService serviceType = getServiceType();
        final ApertureSettings settings = getApertureSettings();
        final ErrorCollection errors = new SimpleErrorCollection();
        if (serviceType != null) {
            final ApplicationLink link = settings.getApplicationLink(serviceType);
            LOG.debug(String.format("modifyUsers: communicating to URL:%s for service:%s", link.getRpcUrl(), serviceType));
            final ApplicationLinkRequestFactory factory = link.createAuthenticatedRequestFactory();
            try {
                modifyUsers(projectKey, mode, role, userList, factory, errors);
            } catch (ResponseException | IOException | CredentialsRequiredException ex) {
                LOG.fatal("Failed to modify users against service due to error", ex);
                throw new RuntimeException(ex);
            }
        }
        return errors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection modifyGroups(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<Group> groupList) {

        final ApertureSettings.ProjectService serviceType = getServiceType();
        final ApertureSettings settings = getApertureSettings();
        final ErrorCollection errors = new SimpleErrorCollection();
        if (serviceType != null) {
            final ApplicationLink link = settings.getApplicationLink(serviceType);
            LOG.debug(String.format("modifyGroups: communicating to URL:%s for service:%s", link.getRpcUrl(), serviceType));
            final ApplicationLinkRequestFactory factory = link.createAuthenticatedRequestFactory();
            try {
                modifyGroups(projectKey, mode, role, groupList, factory, errors);
            } catch (ResponseException | IOException | CredentialsRequiredException ex) {
                LOG.fatal("Failed to modify groups against service due to error", ex);
                throw new RuntimeException(ex);
            }
        }
        return errors;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection createService(final MutableIssue issue, final Map<String, Serializable> environment) {

        final ApertureSettings.ProjectService serviceType = getServiceType();
        final ApertureSettings settings = getApertureSettings();
        final ErrorCollection errors = new SimpleErrorCollection();
        if (serviceType != null) {
            final ApplicationLink link = settings.getApplicationLink(serviceType);
            LOG.debug(String.format("createService: communicating to URL:%s for service:%s", link.getRpcUrl(), serviceType));
            final ApplicationLinkRequestFactory factory = link.createAuthenticatedRequestFactory();
            try {
                createService(issue, factory, environment, errors);
            } catch (ResponseException | IOException | CredentialsRequiredException ex) {
                LOG.fatal("Failed to create service due to error", ex);
                throw new RuntimeException(ex);
            }
        }
        return errors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCollection destroyService(final String projectKey, final Map<String, Serializable> environment) {

        final ApertureSettings.ProjectService serviceType = getServiceType();
        final ApertureSettings settings = getApertureSettings();
        final ErrorCollection errors = new SimpleErrorCollection();
        if (serviceType != null) {
            final ApplicationLink link = settings.getApplicationLink(serviceType);
            final ApplicationLinkRequestFactory factory = link.createAuthenticatedRequestFactory();
            LOG.debug(String.format("destroyService: communicating to URL:%s for service:%s", link.getRpcUrl(), serviceType));
            try {
                rollbackService(projectKey, factory, environment, errors);
            } catch (ResponseException | CredentialsRequiredException ex) {
                LOG.fatal("Failed to rollback service due to error", ex);
                errors.addErrorMessage(ex.toString());
            }
        }
        return errors;
    }

    protected ApplicationLink getApplicationLink() {

        final ApertureSettings.ProjectService linkKey = getServiceType();
        final ApertureSettings settings = getApertureSettings();
        return settings.getApplicationLink(linkKey);
    }

    protected abstract void createService(final MutableIssue issue, ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException;

    protected abstract void rollbackService(final String projectKey, ApplicationLinkRequestFactory factory, final Map<String, Serializable> environment, final ErrorCollection errors) throws ResponseException, CredentialsRequiredException;

    protected abstract void modifyGroups(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<Group> groupList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException;

    protected abstract void modifyUsers(final String projectKey, final Aperture.PermissionMode mode, final Aperture.Role role, final Collection<ApplicationUser> userList, final ApplicationLinkRequestFactory factory, final ErrorCollection errors) throws ResponseException, IOException, CredentialsRequiredException;

    protected abstract boolean isServiceAvailable(final String projectKey, final ApplicationLinkRequestFactory factory) throws ResponseException, CredentialsRequiredException;

    protected static class JSONApplicationLinkResponder implements ApplicationLinkResponseHandler<JsonNode> {

        private final boolean lienent;

        JSONApplicationLinkResponder() {

            this(false);
        }

        JSONApplicationLinkResponder(final boolean isLienent) {

            this.lienent = isLienent;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JsonNode credentialsRequired(final Response response) throws ResponseException {

            return handle(response);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JsonNode handle(final Response response) throws ResponseException {

            final ObjectMapper om = new ObjectMapper();
            try {
                return om.readTree(response.getResponseBodyAsStream());
            } catch (IOException ex) {
                if (lienent) {
                    return null;
                }
                throw new ResponseException(ex);
            }
        }

    }

    protected static class DebugApplicationLinkResponder<R> implements ApplicationLinkResponseHandler<R> {

        /**
         *
         */
        private static final transient Logger LOG = Logger.getLogger(AbstractRestfulProjectServiceHandler.class);
        /**
         *
         */
        private final ApplicationLinkResponseHandler<R> handler;

        public DebugApplicationLinkResponder(final ApplicationLinkResponseHandler<R> handler) {
            this.handler = handler;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public R credentialsRequired(Response response) throws ResponseException {

            return handle(response);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public R handle(final Response response) throws ResponseException {

            LOG.debug(String.format("< %s %s", response.getStatusText(), response.getStatusCode()));
            final Map<String, String> headers = response.getHeaders();
            for (final Map.Entry<String, String> entry : headers.entrySet()) {
                LOG.debug(String.format("< %s: %s", entry.getKey(), entry.getValue()));
            }
            return handler.handle(response);
        }

    }

    public static class XmlRpcJsonResponder implements ApplicationLinkResponseHandler<JsonNode> {

        final static Map<String, Boolean> booleanMappings;
        private static final transient Logger LOG = Logger.getLogger(XmlRpcJsonResponder.class);

        static {
            final Map<String, Boolean> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            m.put("1", Boolean.TRUE);
            m.put("yes", Boolean.TRUE);
            m.put("true", Boolean.TRUE);
            m.put("t", Boolean.TRUE);
            m.put("0", Boolean.FALSE);
            m.put("no", Boolean.FALSE);
            m.put("false", Boolean.FALSE);
            m.put("f", Boolean.FALSE);
            booleanMappings = Collections.unmodifiableMap(m);
        }

        final ObjectMapper om = new ObjectMapper();
        final JsonNodeFactory factory = JsonNodeFactory.instance;

        /**
         * {@inheritDoc}
         */
        @Override
        public JsonNode credentialsRequired(Response response) throws ResponseException {

            return handle(response);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JsonNode handle(final Response response) throws ResponseException {

            final Map<String, String> headers = response.getHeaders();
            final Integer contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "10"));
            final ObjectNode jsonResponse = om.createObjectNode();
            if (contentLength > 0) {
                try {
                    final SAXReader reader = new SAXReader();
                    reader.setMergeAdjacentText(true);
                    final Document documentResponse = reader.read(response.getResponseBodyAsStream());
                    final Element rootElement = documentResponse.getRootElement();
                    final Element params = rootElement.element("params");
                    final Element fault = rootElement.element("fault");
                    if (params != null) {
                        for (final Object childElement : params.elements()) {
                            final Element param = (Element) childElement;
                            final Element value = param.element("value");
                            final ArrayNode parameterValues = factory.arrayNode();
                            if (value.isTextOnly()) {
                                parameterValues.add(value.getTextTrim());
                            } else {
                                for (final Object obj : value.elements()) {
                                    final Element valueElement = (Element) obj;
                                    processValueElement(parameterValues, valueElement);
                                }
                            }
                            jsonResponse.put("params", parameterValues);
                        }
                    } else if (fault != null) {
                        final Element value = fault.element("value");
                        if (value.isTextOnly()) {
                            jsonResponse.put("fault", value.getTextTrim());
                        } else {
                            for (final Object obj : value.elements()) {
                                final Element valueElement = (Element) obj;
                                processValueElement(jsonResponse, "fault", valueElement);
                            }
                        }
                    }
                } catch (final DocumentException e) {
                    LOG.warn("Failed to parse XML response into JSON", e);
                }
            }
            return jsonResponse;
        }

        private void processValueElement(final ArrayNode struct, final Element element) {

            final Object extractedValue = extractValue(element);
            if (extractedValue instanceof String) {
                struct.add((String) extractedValue);
            } else if (extractedValue instanceof Integer) {
                struct.add((Integer) extractedValue);
            } else if (extractedValue instanceof Boolean) {
                struct.add((Boolean) extractedValue);
            } else if (extractedValue instanceof Double) {
                struct.add((Double) extractedValue);
            } else if (extractedValue instanceof JsonNode) {
                struct.add((JsonNode) extractedValue);
            }
        }

        private void processValueElement(final ObjectNode struct, final String propertyName, final Element element) {

            final Object extractedValue = extractValue(element);
            if (extractedValue instanceof String) {
                struct.put(propertyName, (String) extractedValue);
            } else if (extractedValue instanceof Integer) {
                struct.put(propertyName, (Integer) extractedValue);
            } else if (extractedValue instanceof Boolean) {
                struct.put(propertyName, (Boolean) extractedValue);
            } else if (extractedValue instanceof Double) {
                struct.put(propertyName, (Double) extractedValue);
            } else if (extractedValue instanceof JsonNode) {
                struct.put(propertyName, (JsonNode) extractedValue);
            }
        }

        private void processValueElement(final ObjectNode struct, final Element element) {

            if ("member".equalsIgnoreCase(element.getName())) {
                final String propertyName = element.elementTextTrim("name");
                final Element value = element.element("value");
                if (value.isTextOnly()) {
                    struct.put(propertyName, value.getTextTrim());
                } else {
                    for (final Object obj : value.elements()) {
                        final Element valueElement = (Element) obj;
                        final Object extractedValue = extractValue(valueElement);
                        if (extractedValue instanceof String) {
                            struct.put(propertyName, (String) extractedValue);
                        } else if (extractedValue instanceof Integer) {
                            struct.put(propertyName, (Integer) extractedValue);
                        } else if (extractedValue instanceof Boolean) {
                            struct.put(propertyName, (Boolean) extractedValue);
                        } else if (extractedValue instanceof Double) {
                            struct.put(propertyName, (Double) extractedValue);
                        } else if (extractedValue instanceof JsonNode) {
                            struct.put(propertyName, (JsonNode) extractedValue);
                        }
                    }
                }
            }
        }

        private Object extractValue(final Element element) {

            final String dataType = element.getName();
            final String cdata = element.getTextTrim();
            if ("string".equalsIgnoreCase(dataType) || "base64".equalsIgnoreCase(dataType)) {
                return cdata;
            } else if ("double".equalsIgnoreCase(dataType)) {
                return om.convertValue(cdata, Double.class);
            } else if ("boolean".equalsIgnoreCase(dataType)) {
                return booleanMappings.get(cdata);
            } else if ("i4".equalsIgnoreCase(dataType) || "int".equalsIgnoreCase(dataType)) {
                return om.convertValue(cdata, Integer.class);
            } else if ("struct".equalsIgnoreCase(dataType)) {
                final ObjectNode structValue = factory.objectNode();
                for (final Object memberElement : element.elements()) {
                    processValueElement(structValue, (Element) memberElement);
                }
                return structValue;
            } else if ("array".equalsIgnoreCase(dataType)) {
                final ArrayNode arrayValue = factory.arrayNode();
                final Element dataElement = element.element("data");
                for (final Object valueElement : dataElement.elements()) {
                    final Element arrayValueElement = (Element) valueElement;
                    for (final Object obj : arrayValueElement.elements()) {
                        processValueElement(arrayValue, (Element) obj);
                    }
                }
                return arrayValue;
            }
            return null;
        }
    }
}
