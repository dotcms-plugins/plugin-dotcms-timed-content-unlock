package com.dotcms.job.unlockcontent;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.quartz.CronTrigger;

import org.osgi.framework.BundleContext;

import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.quartz.CronScheduledTask;

public class Activator extends GenericBundleActivator {

    public final static String JOB_NAME = "Unlock locked content based on a timer";

    public final static String JOB_GROUP = "OSGi Jobs";



    public void start ( BundleContext context ) throws Exception {

    	
    	Class<UnlockContentTimer> clazz = com.dotcms.job.unlockcontent.UnlockContentTimer.class;

        String cron = OSGiPluginProperties.getProperty("CRON_EXPRESSION");
    	
        //Initializing services...
        initializeServices ( context );
        publishBundleServices(context);
        Map<String, Object> params = new HashMap<String, Object>();

        
    	String unlock = OSGiPluginProperties.getProperty("UNLOCK_AFTER_SECONDS");
        int limit  = Integer.parseInt(OSGiPluginProperties.getProperty("SQL_LIMIT_CLAUSE", "1000"));
        long threadSleep  = Integer.parseInt(OSGiPluginProperties.getProperty("THREAD_SLEEP_BETWEEN_UNLOCKS", "50"));
    	
    	params.put("UNLOCK_AFTER_SECONDS", unlock);
    	params.put("SQL_LIMIT_CLAUSE", limit);
    	params.put("THREAD_SLEEP_BETWEEN_UNLOCKS", threadSleep);
    	// give us a minute before we fire the first time
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, 0);

        //Creating our custom Quartz Job
        CronScheduledTask cronScheduledTask =
                new CronScheduledTask( JOB_NAME, JOB_GROUP, JOB_NAME, clazz.getName(),
                        cal.getTime(), null, CronTrigger.MISFIRE_INSTRUCTION_SMART_POLICY,
                        params, cron );

        //Schedule our custom job
        scheduleQuartzJob( cronScheduledTask );
        
    }

    public void stop ( BundleContext context ) throws Exception {
        //Unregister all the bundle services
    	
        unregisterServices( context );
        unpublishBundleServices();
    }

}