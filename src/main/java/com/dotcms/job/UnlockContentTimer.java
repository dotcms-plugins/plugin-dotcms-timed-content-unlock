package com.dotcms.job;

import com.dotcms.business.CloseDB;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.model.User;
import com.liferay.util.StringPool;
import io.vavr.control.Try;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * This Job takes care of unlocking Contentlets that have been locked for X amount of time.
 * All the configuration properties are passed down to this class by the
 * {@link OSGiPluginProperties} utility class. This is what the Job does in order to unlock the
 * appropriate Contentlets:
 * <ul>
 * <li>Executes a raw SQL query that will return the Contentlet working Inodes who have been locked
 * by the time indicated in the {@code UNLOCK_AFTER_SECONDS} property. The default value is
 * {@code 86400} seconds, that is, 1 day.</li>
 * <li>Based on such an Inode, Contentlet is both retrieved and unlocked via the Content API.</li>
 * <li>Just as a safety precaution, the Job will sleep for the number of milliseconds specified via
 * the {@code THREAD_SLEEP_BETWEEN_UNLOCKS} property in order to let dotCMS appropriately update the
 * Contenlet. The default value is {@code 50} milliseconds.</li>
 * <li>The unlock process is carried out in batches whose size is specified by the
 * {@code SQL_LIMIT_CLAUSE} property. The default batch size is {@code 1000} items.</li>
 * </ul>
 * By default, the Unlock Content Job will look for 1,000 Contentlets up to 1,000 times. This means
 * that the Job unlocks up to 1,000,0000 Contentlets in every execution. Therefore, if more than 1
 * million Contentlets are locked, the rest of them will need to wait for the next time the Job
 * runs.
 *
 * @author dotCMS
 * @since Aug 10, 2021
 */
public class UnlockContentTimer implements Runnable {

    public static final String UNLOCK_AFTER_SECONDS = "UNLOCK_AFTER_SECONDS";
    public static final String SQL_LIMIT_CLAUSE = "SQL_LIMIT_CLAUSE";
    public static final String THREAD_SLEEP_BETWEEN_UNLOCKS = "THREAD_SLEEP_BETWEEN_UNLOCKS";


    final int unlockAfterSeconds =
                    Try.of(() -> Integer.parseInt(OSGiPluginProperties.getProperty("UNLOCK_AFTER_SECONDS"))).getOrElse(86400);
    final int sqlLimit = Try.of(() -> Integer.parseInt(OSGiPluginProperties.getProperty("SQL_LIMIT_CLAUSE"))).getOrElse(1000);
    final int threadSleepBetweenLocks = Try
                    .of(() -> Integer.parseInt(OSGiPluginProperties.getProperty("THREAD_SLEEP_BETWEEN_UNLOCKS"))).getOrElse(200);

    @CloseDB
    @Override
    public void run() {

        
        Logger.info(this.getClass(), "UnlockContentTimer : ------------------------------------------");
        final String oldestServer = Try.of(() -> APILocator.getServerAPI().getOldestServer()).getOrElse("notIt");
        if (!oldestServer.equals(APILocator.getServerAPI().readServerId())) {
            
            Logger.info(this.getClass(), "Not the oldest server in cluster, skipping UnlockContentTimer");
            return;
        }


        Logger.info(this.getClass(), "UnlockContentTimer : ------------------------------------------");

        final Calendar lockedDate = Calendar.getInstance();

        lockedDate.add(Calendar.SECOND, -unlockAfterSeconds);
        String contentletInode = StringPool.BLANK;
        try {
            final DotConnect db = new DotConnect();
            int unlockedContentlets = 0;
            for (int batch = 1; batch <= 1000; batch++) {
                db.setSQL("SELECT working_inode FROM contentlet_version_info WHERE locked_on < ? AND locked_by IS NOT NULL");
                db.setMaxRows(sqlLimit);
                db.addParam(lockedDate.getTime());
                final List<Map<String, Object>> contentletList = db.loadObjectResults();
                DbConnectionFactory.closeSilently();
                if(contentletList.isEmpty()) {
                    break;
                }
                Logger.info(this.getClass(), "Run # : " + batch);
                Logger.info(this.getClass(), "Found : " + contentletList.size() + " contentlets to unlock.");
                

                
                
                for (final Map<String, Object> contentletData : contentletList) {
                    contentletInode = (String) contentletData.get("working_inode");
                    if (unlockContentlet(contentletInode)) {
                        unlockedContentlets++;
                    }
                    Thread.sleep(threadSleepBetweenLocks);

                }
                
            }
            Logger.info(this.getClass(), "dotCMS Timed Unlock Job: Unlocked " + unlockedContentlets + " contentlets");
        } catch (final Exception e) {
            Logger.error(this.getClass(),
                            String.format("An error occurred unlocking content '%s': %s", contentletInode, e.getMessage()), e);
        } finally {
            DbConnectionFactory.closeSilently();

        }
    }

    @CloseDB
    final boolean unlockContentlet(final String inode) {
        if (inode == null) {
            return false;
        }
        final User systemUser = APILocator.systemUser();
        try {
            APILocator.getContentletAPI().unlock(APILocator.getContentletAPI().find(inode, systemUser, false), systemUser, false);
        } catch (Exception e) {
            Logger.warn(this.getClass(),
                            String.format("An error occurred unlocking content inode: '%s': %s", inode, e.getMessage()), e);
            return false;
        }
        return true;


    }



}
