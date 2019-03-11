package com.dotcms.job.unlockcontent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This class reads configuration values from the {@code ${PLUGIN_ROOT}/src/main/resources/plugin.properties}
 * file.
 *
 * @author dotCMS
 * @version 4.3.3
 * @since Feb 26th, 2019
 */
public class OSGiPluginProperties {

    private static final String PROPERTY_FILE_NAME = "plugin.properties";
    private static Properties properties;

    static {
        properties = new Properties();
        try {
            InputStream in = OSGiPluginProperties.class.getResourceAsStream("/" + PROPERTY_FILE_NAME);
            if (in == null) {
                in = OSGiPluginProperties.class.getResourceAsStream("/com/dotcms/job/unlockcontent/" + PROPERTY_FILE_NAME);
                if (in == null) {
                    throw new FileNotFoundException(PROPERTY_FILE_NAME + " not found");
                }
            }
            properties.load(in);
        } catch (final FileNotFoundException e) {
            System.out.println("FileNotFoundException : " + PROPERTY_FILE_NAME + " not found");
            e.printStackTrace();
        } catch (final IOException e) {
            System.out.println("IOException : Can't read " + PROPERTY_FILE_NAME);
            e.printStackTrace();
        }
    }

    /**
     * Returns the property associated to the specified key.
     *
     * @param key The key to the property that will be retrieved.
     *
     * @return The property associated to the specified key.
     */
    public static String getProperty(final String key) {
        return properties.getProperty(key);
    }

    /**
     * Returns the property associated to the specified key. If the property is {@code null}, a default value will be
     * returned.
     *
     * @param key          The key to the property that will be retrieved.
     * @param defaultValue The default value to be returned in case the property is {@code null}.
     *
     * @return The property associated to the specified key, or the default value instead.
     */
    public static String getProperty(final String key, final String defaultValue) {
        final String x = properties.getProperty(key);
        return (x == null) ? defaultValue : x;
    }

}
