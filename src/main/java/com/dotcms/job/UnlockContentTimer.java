package com.dotcms.job;

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
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * This Quartz Job takes care of unlocking Contentlets that have been locked for X amount of time. All the configuration
 * properties are passed down to this class by the {@link OSGiPluginProperties} utility class. This is what the Job does
 * in order to unlock the appropriate Contentlets:
 * <ul>
 *     <li>Executes a raw SQL query that will return the Contentlet working Inodes who have been locked by the time
 *     indicated in the {@code UNLOCK_AFTER_SECONDS} property. The default value is {@code 86400} seconds, that is,
 *     1 day.</li>
 *     <li>Based on such an Inode, Contentlet is both retrieved and unlocked via the Content API.</li>
 *     <li>Just as a safety precaution, the Job will sleep for the number of milliseconds specified via the
 *     {@code THREAD_SLEEP_BETWEEN_UNLOCKS} property in order to let dotCMS appropriately update the Contenlet. The
 *     default value is {@code 50} milliseconds.</li>
 *     <li>The unlock process is carried out in batches whose size is specified by the {@code SQL_LIMIT_CLAUSE}
 *     property. The default batch size is {@code 1000} items.</li>
 * </ul>
 * By default, the Unlock Content Job will look for 1,000 Contentlets up to 1,000 times. This means that the Job unlocks
 * up to 1,000,0000 Contentlets in every execution. Therefore, if more than 1 million Contentlets are locked, the rest
 * of them will need to wait for the next time the Job runs.
 *
 * @author dotCMS
 * @since Aug 10, 2021
 */
public class UnlockContentTimer implements StatefulJob {

    public static final String UNLOCK_AFTER_SECONDS = "UNLOCK_AFTER_SECONDS";
    public static final String SQL_LIMIT_CLAUSE = "SQL_LIMIT_CLAUSE";
    public static final String THREAD_SLEEP_BETWEEN_UNLOCKS = "THREAD_SLEEP_BETWEEN_UNLOCKS";

    private static final boolean DONT_RESPECT_FRONTEND_ROLES = Boolean.FALSE;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        Logger.info(this, "dotCMS Timed Unlock Job: ------------------------------------------");
        final JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        final String unlockAfter = dataMap.getString(UNLOCK_AFTER_SECONDS).toString();
        final Calendar lockedDate = Calendar.getInstance();
        final int seconds = Integer.parseInt(unlockAfter);
        lockedDate.add(Calendar.SECOND, -seconds);
        String contentletInode = StringPool.BLANK;
        try {
            final DotConnect db = new DotConnect();
            final User systemUser = APILocator.getUserAPI().getSystemUser();
            final int sqlLimitClause = Integer.parseInt(dataMap.getString(SQL_LIMIT_CLAUSE).toString());
            final long threadSleep = Integer.parseInt(dataMap.getString(THREAD_SLEEP_BETWEEN_UNLOCKS).toString());
            int unlockedContentlets = 0;
            for (int batch = 1; batch <= 1000; batch++) {
                db.setSQL("SELECT working_inode FROM contentlet_version_info WHERE locked_on < ? AND locked_by IS NOT" +
                        " NULL");
                db.setMaxRows(sqlLimitClause);
                db.addParam(lockedDate.getTime());
                final List<Map<String, Object>> contentletList = db.loadObjectResults();
                if (contentletList.isEmpty()) {
                    break;
                }
                for (final Map<String, Object> contentletData : contentletList) {
                    if (!UtilMethods.isSet(contentletData.get("working_inode"))) {
                        continue;
                    }
                    contentletInode = contentletData.get("working_inode").toString();
                    final Contentlet contentlet = APILocator.getContentletAPI().find(contentletInode, systemUser,
                            DONT_RESPECT_FRONTEND_ROLES);
                    APILocator.getContentletAPI().unlock(contentlet, systemUser, DONT_RESPECT_FRONTEND_ROLES);
                    unlockedContentlets++;
                    Thread.sleep(threadSleep);
                }
                Logger.info(this.getClass(), "dotCMS Timed Unlock Job: Run #" + batch);
            }
            Logger.info(this.getClass(), "dotCMS Timed Unlock Job: Unlocked " + unlockedContentlets + " contentlets");
        } catch (final DotDataException e) {
            Logger.error(this.getClass(), String.format("An error occurred when updating data in Contentlet '%s': " +
                    "%s", contentletInode, e.getMessage()), e);
        } catch (final DotSecurityException e) {
            Logger.error(this.getClass(), String.format("An permissions error occurred when updating data in " +
                    "Contentlet '%s': %s", contentletInode, e.getMessage()), e);
        } catch (final InterruptedException e) {
            Logger.error(this.getClass(), String.format("An error occurred during the Thread Sleep phase when " +
                    "updating data in Contentlet '%s': %s", contentletInode, e.getMessage()), e);
        } finally {
            try {
                DbConnectionFactory.closeSilently();
            } catch (final Exception e) {
                Logger.error(this.getClass(), String.format("dotCMS Timed Unlock Job: DB connection could not be " +
                        "closed: %s", e.getMessage()), e);
            }
        }
    }

}
