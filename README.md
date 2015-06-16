# Timed Unlocking of Content

Users are forgetful.  They start to change a piece of content and then leave it, forgetting to unlock it.  This leads to orphan content that can only be unlocked by an administrator.

This bundle plugin provides a plugin that will automatically unlock content that has been locked for X amount of time.  You can specify the duration to wait before unlocking by modifying the UNLOCK_AFTER_SECONDS property in the plugin.properties file found under the src/main/resources directory.  You can also modify the CRON_EXPRESSION expression of the cron job for the job itself (keep in mind quartz takes seconds as the first arg - see 
http://www.quartz-scheduler.org/documentation/quartz-1.x/tutorials/crontrigger for more information.

## To Configure Build
Edit the properties in the `src/resources/plugin.properties` file to suit your needs. After that, from the root of the plugin directory, run `./gradlew jar` from the command line.  You can then drop the resulting `./build/libs/*.jar` into your felix dir or upload it into your running dotCMS via the dynamic plugins screen.

## Other thoughts:
On really large sites, you might want to drop an index on the contentlet_version_info.locked_on field which is used by the plugin to query for locked content.

