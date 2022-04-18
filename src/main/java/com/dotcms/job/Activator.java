package com.dotcms.job;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.core.util.CronExpression;
import org.osgi.framework.BundleContext;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.util.Logger;

/**
 * This OSGi Plugin will automatically unlock content that has been locked for X amount of time. You
 * can specify the duration to wait before unlocking by modifying the {@code UNLOCK_AFTER_SECONDS}
 * property in the {@code
 * plugin.properties} file found under the {@code src/main/resources/} directory. You can also
 * modify the {@code
 * CRON_EXPRESSION} expression of the cron job for the job itself.
 *
 * @author dotCMS
 * @since Aug 10, 2021
 */
public class Activator extends GenericBundleActivator {

    public final static String JOB_NAME = "Unlocks locked content based on a timer";
    public final static String JOB_GROUP = "OSGi Jobs";
    public final static String JOB_CLASS = "com.dotcms.job.UnlockContentTimer";


    private final Runnable runner = new UnlockContentTimer();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    /**
     * This is the staring point for all OSGi plugins in dotCMS. This is where all the setup and basic
     * configuration routines must be called.
     *
     * @param context The OSGi {@link BundleContext} object.
     *
     * @throws Exception An error occurred during the plugin's initialization.
     */
    @Override
    public void start(final BundleContext context) throws Exception {

        CronExpression cron = new CronExpression(OSGiPluginProperties.getProperty("CRON_EXPRESSION", "0 15 * * * ?")) ;
        
        Instant now = Instant.now();
        
        Instant previousRun = cron.getPrevFireTime(Date.from(now)).toInstant();
        Instant nextRun = cron.getNextValidTimeAfter(Date.from(now)).toInstant();

        long delay = Duration.between(previousRun, now).getSeconds();
        long runEvery = Duration.between(previousRun, nextRun).getSeconds();
        
        Logger.info(this.getClass(), "Starting Content Unlock Job. Runs every:" + runEvery + " seconds. Next run @ " + nextRun);
        
        scheduler.scheduleAtFixedRate(runner, delay, runEvery, TimeUnit.SECONDS);



    }

    /**
     * Allows developers to correctly stop/un-register/remove Services and other utilities when an OSGi
     * Plugin is stopped.
     *
     * @param context The OSGi {@link BundleContext} object.
     *
     * @throws Exception An error occurred during the plugin's stop.
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        
        Logger.info(this.getClass(), "Stopping Content Unlock Job");
        scheduler.shutdownNow();
    }



}
