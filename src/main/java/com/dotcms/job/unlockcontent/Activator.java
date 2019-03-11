package com.dotcms.job.unlockcontent;

import com.dotmarketing.loggers.Log4jUtil;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.quartz.CronScheduledTask;
import com.dotmarketing.quartz.QuartzUtils;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.dotcms.repackage.org.apache.logging.log4j.LogManager;
import com.dotcms.repackage.org.apache.logging.log4j.core.LoggerContext;
import org.osgi.framework.BundleContext;
import org.quartz.CronTrigger;

/**
 * This is the entry point for the activation and deployment of the Timed Content Unlock 
 * plugin.
 * <p>
 * This bundle plugin provides a plugin that will automatically unlock content that has 
 * been locked for X amount of time. You can specify the duration to wait before unlocking 
 * by modifying the {@code UNLOCK_AFTER_SECONDS} property in the plugin.properties file 
 * found under the {@code ${PLUGIN_ROOT}/src/main/resources/} directory. You can also 
 * modify the {@code CRON_EXPRESSION} expression of the Cron Job for the Job itself.
 *
 * @author dotCMS
 * @version 4.3.3
 * @since Feb 26th, 2019
 */
public class Activator extends GenericBundleActivator {

    private LoggerContext pluginLoggerContext;

    public final static String JOB_NAME = "Unlock locked content based on a timer";
    public final static String JOB_GROUP = "OSGi Jobs";

    @Override
    public void start(final BundleContext context) throws Exception {
        // Initializing log4j...
        final LoggerContext dotcmsLoggerContext = Log4jUtil.getLoggerContext();
        // Initialing the log4j context of this plugin based on the dotCMS logger context
        pluginLoggerContext = (LoggerContext) LogManager.getContext(this.getClass().getClassLoader(),
                Boolean.FALSE,
                dotcmsLoggerContext,
                dotcmsLoggerContext.getConfigLocation());
        //Initializing services...
        initializeServices(context);
        publishBundleServices(context);
        final String unlock = OSGiPluginProperties.getProperty("UNLOCK_AFTER_SECONDS");
        final int limit = Integer.parseInt(OSGiPluginProperties.getProperty("SQL_LIMIT_CLAUSE", "1000"));
        final long threadSleep = Integer.parseInt(OSGiPluginProperties.getProperty("THREAD_SLEEP_BETWEEN_UNLOCKS", "50"));
        final Map<String, Object> params = new HashMap<>();
        params.put("UNLOCK_AFTER_SECONDS", unlock);
        params.put("SQL_LIMIT_CLAUSE", limit);
        params.put("THREAD_SLEEP_BETWEEN_UNLOCKS", threadSleep);
        // Give us a minute before we fire the first time
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, 0);
        QuartzUtils.removeJob(JOB_NAME, JOB_GROUP);
        // Creating our custom Quartz Job
        final Class<UnlockContentTimer> clazz = com.dotcms.job.unlockcontent.UnlockContentTimer.class;
        final String cronExpression = OSGiPluginProperties.getProperty("CRON_EXPRESSION");
        CronScheduledTask cronScheduledTask =
                new CronScheduledTask(JOB_NAME, JOB_GROUP, JOB_NAME, clazz.getName(),
                        calendar.getTime(), null, CronTrigger.MISFIRE_INSTRUCTION_SMART_POLICY,
                        params, cronExpression);
        // Schedule our custom job
        scheduleQuartzJob( cronScheduledTask );
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        // Unregister all the bundle services
        unregisterServices(context);
        unpublishBundleServices();
        // Shutting down log4j in order to avoid memory leaks
        Log4jUtil.shutdown(pluginLoggerContext);
    }

}
