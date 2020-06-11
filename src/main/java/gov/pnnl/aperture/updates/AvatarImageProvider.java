package gov.pnnl.aperture.updates;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarImageDataProvider;
import com.atlassian.plugin.util.ClassLoaderUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Developer Central @ PNNL
 */
public class AvatarImageProvider implements AvatarImageDataProvider {

    private final String resourceURL;

    public AvatarImageProvider(final String resourceURL) {

        Assert.hasText(resourceURL, "Cannot create an AvatarImageProvider with an empty image path.");
        this.resourceURL = resourceURL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeImage(final Avatar.Size size, final OutputStream out) throws IOException {

        final InputStream is = ClassLoaderUtils.getResourceAsStream(resourceURL, ApertureJiraInstallTask.class);
        try {
            IOUtils.copy(is, out);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }
}
