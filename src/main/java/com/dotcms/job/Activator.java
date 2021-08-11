package com.dotcms.job;

import com.dotmarketing.loggers.Log4jUtil;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.quartz.CronScheduledTask;
import com.dotmarketing.servlets.InitServlet;
import com.dotmarketing.util.Logger;
import com.google.common.collect.ImmutableList;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.osgi.framework.BundleContext;
import org.quartz.CronTrigger;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dotcms.job.UnlockContentTimer.SQL_LIMIT_CLAUSE;
import static com.dotcms.job.UnlockContentTimer.THREAD_SLEEP_BETWEEN_UNLOCKS;
import static com.dotcms.job.UnlockContentTimer.UNLOCK_AFTER_SECONDS;

/**
 * This OSGi Plugin will automatically unlock content that has been locked for X amount of time. You can specify the
 * duration to wait before unlocking by modifying the {@code UNLOCK_AFTER_SECONDS} property in the {@code
 * plugin.properties} file found under the {@code src/main/resources/} directory. You can also modify the {@code
 * CRON_EXPRESSION} expression of the cron job for the job itself.
 *
 * @author dotCMS
 * @since Aug 10, 2021
 */
public class Activator extends GenericBundleActivator {

    public final static String JOB_NAME = "Unlocks locked content based on a timer";
    public final static String JOB_GROUP = "OSGi Jobs";
    public final static String JOB_CLASS = "com.dotcms.job.UnlockContentTimer";

    private final List<Class> overriddenClasses = ImmutableList.of(UnlockContentTimer.class);
    private final ClassLoader webappClassLoader = InitServlet.class.getClassLoader();
    private final ClassLoader bundleClassLoader = this.getClass().getClassLoader();
    private final ClassReloadingStrategy classReloadingStrategy = ClassReloadingStrategy.fromInstalledAgent();
    private LoggerContext pluginLoggerContext;

    /**
     * This is the staring point for all OSGi plugins in dotCMS. This is where all the setup and basic configuration
     * routines must be called.
     *
     * @param context The OSGi {@link BundleContext} object.
     *
     * @throws Exception An error occurred during the plugin's initialization.
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        // Initializing log4j
        final LoggerContext dotcmsLoggerContext = Log4jUtil.getLoggerContext();
        // Initialing the log4j context of this plugin based on the dotCMS logger context
        this.pluginLoggerContext = (LoggerContext) LogManager.getContext(this.getClass().getClassLoader(), false,
                dotcmsLoggerContext, dotcmsLoggerContext.getConfigLocation());
        // Initializing services
        initializeServices(context);
        injectClassesIntoDotCMS();
        final Map<String, Object> jobParams = new HashMap<>();
        jobParams.put(UNLOCK_AFTER_SECONDS, OSGiPluginProperties.getProperty(UNLOCK_AFTER_SECONDS, "86400"));
        jobParams.put(SQL_LIMIT_CLAUSE, OSGiPluginProperties.getProperty(SQL_LIMIT_CLAUSE, "1000"));
        jobParams.put(THREAD_SLEEP_BETWEEN_UNLOCKS, OSGiPluginProperties.getProperty(THREAD_SLEEP_BETWEEN_UNLOCKS,
                "50"));
        final String cronExpression = OSGiPluginProperties.getProperty("CRON_EXPRESSION");
        // Wait until the next minute before firing the first time
        final Calendar startTime = Calendar.getInstance();
        startTime.add(Calendar.SECOND, 0);
        // Creating the custom Quartz Job
        final CronScheduledTask cronScheduledTask = new CronScheduledTask(JOB_NAME, JOB_GROUP, JOB_NAME, JOB_CLASS,
                startTime.getTime(), null, CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW, jobParams, cronExpression);
        Logger.info(this.getClass().getName(), "Adding UnlockContentTimer Job to Quartz");
        // Schedule the custom job
        scheduleQuartzJob(cronScheduledTask);
    }

    /**
     * Allows developers to correctly stop/un-register/remove Services and other utilities when an OSGi Plugin is
     * stopped.
     *
     * @param context The OSGi {@link BundleContext} object.
     *
     * @throws Exception An error occurred during the plugin's stop.
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        removeClassesFromDotCMS();
        // Unregister all the bundle services
        unregisterServices(context);
        // Shutting down log4j in order to avoid memory leaks
        Log4jUtil.shutdown(this.pluginLoggerContext);
    }

    /**
     * Injects one or more classes used by this plugin into the dotCMS context. That is, the classes in this bundle are
     * added to the dotCMS Class Loader.
     */
    protected void injectClassesIntoDotCMS() {
        for (final Class clazz : this.overriddenClasses) {
            Logger.info(this.getClass().getName(), String.format("Injecting class '%s' into classloader '%s'", clazz
                            .getName(), this.webappClassLoader));
            Logger.debug(this.getClass().getName(), "Bundle classloader: " + this.bundleClassLoader);
            Logger.debug(this.getClass().getName(), "dotCMS classloader: " + this.webappClassLoader);
            ByteBuddyAgent.install();
            new ByteBuddy().rebase(clazz, ClassFileLocator.ForClassLoader.of(this.bundleClassLoader)).name(clazz
                    .getName()).make().load(this.webappClassLoader, this.classReloadingStrategy);
        }
    }


    /**
     * Removes one or more classes used by this plugin from the dotCMS context. That is, the classes in this bundle are
     * removed from the dotCMS Class Loader.
     */
    protected void removeClassesFromDotCMS() throws Exception {
        for (final Class clazz : this.overriddenClasses) {
            Logger.info(this.getClass().getName(), String.format("Removing class '%s' from classloader '%s'", clazz
                    .getName(), this.webappClassLoader));
            try {
                this.classReloadingStrategy.reset(ClassFileLocator.ForClassLoader.of(this.webappClassLoader), clazz);
            } catch (final Exception e) {
                Logger.debug(this.getClass().getName(),
                        String.format("Error setting class '%s' back into dotCMS classloader", clazz.getName()));
            }
        }
    }

}
