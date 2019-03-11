package com.dotcms.job.unlockcontent;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

import com.dotmarketing.business.APILocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.Logger;
import com.liferay.portal.model.User;

/**
 * Checks the dotCMS data source looking for Contenlets that have been locked for more
 * than 1 day. By default, the query used to return locked Contentlets will run the number
 * of times specified in the {@code TOTAL_QUERY_RUNS} variable. There are several
 * configuration parameters that are taken into account when retrieving the data:
 * <ol>
 *     <li>{@code UNLOCK_AFTER_SECONDS}: The number of seconds since the Contentlet was
 *     locked. Usually, 86400 (one day).</li>
 *     <li>{@code SQL_LIMIT_CLAUSE}: The number of locked Contentlets pulled by the query
 *     (the batch size).</li>
 *     <li>{@code THREAD_SLEEP_BETWEEN_UNLOCKS}: The milliseconds that the current thread
 *     will sleep to allow for a successful Contentlet unlock.</li>
 * </ol>
 *
 * @author dotCMS
 * @version 4.3.3
 * @since Feb 26th, 2019
 */
public class UnlockContentTimer implements StatefulJob {

    private static final boolean DONT_RESPECT_FRONTEND_ROLES = false;
    private static final int TOTAL_QUERY_RUNS = 1000;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Logger.info(this, "Timed Content Unlock: ------------------------------------------");
        final String unlockAfter = String.class.cast(context.getMergedJobDataMap().get("UNLOCK_AFTER_SECONDS"));
        final Calendar calendar = Calendar.getInstance();
        final int seconds = Integer.parseInt(unlockAfter);
        calendar.add(Calendar.SECOND, -seconds);
        int totalUnlockedContentlets = 0;
        try {
            final DotConnect db = new DotConnect();
            final int limit = Integer.class.cast(context.getMergedJobDataMap().get("SQL_LIMIT_CLAUSE")).intValue();
            final long threadSleep = Long.class.cast(context.getMergedJobDataMap().get
                    ("THREAD_SLEEP_BETWEEN_UNLOCKS")).longValue();
            final User systemUser = APILocator.getUserAPI().getSystemUser();
            for (int i = 1; i <= TOTAL_QUERY_RUNS; i++) {
                db.setSQL("SELECT identifier, lang, working_inode FROM contentlet_version_info WHERE " +
                        "contentlet_version_info.locked_on < ? AND locked_by IS NOT NULL");
                db.setMaxRows(limit);
                db.addParam(calendar.getTime());
                final List<Map<String, Object>> results = db.loadObjectResults();
                if (results.size() == 0) {
                    break;
                }
                for (final Map<String, Object> map : results) {
                    final Contentlet contentlet = APILocator.getContentletAPI().find(map.get("working_inode")
                            .toString(), systemUser, DONT_RESPECT_FRONTEND_ROLES);
                    APILocator.getContentletAPI().unlock(contentlet, systemUser, DONT_RESPECT_FRONTEND_ROLES);
                    totalUnlockedContentlets++;
                    Thread.sleep(threadSleep);
                }
                Logger.info(this.getClass(), "Timed Content Unlock: Run " + i);
            }
            Logger.info(this.getClass(), "Timed Content Unlock: Unlocked " + totalUnlockedContentlets + " contentlets");
        } catch (final DotDataException e) {
            Logger.error(this.getClass(), e.getMessage(), e);
        } catch (final DotSecurityException e) {
            Logger.error(this.getClass(), e.getMessage(), e);
        } catch (final InterruptedException e) {
            Logger.error(this.getClass(), e.getMessage(), e);
        } finally {
            try {
                DbConnectionFactory.closeConnection();
            } catch (final Exception e) {
                Logger.error(this.getClass(), "Timed Content Unlock: An error occurred when trying to close DB connection.", e);
            }
        }
    }

}
