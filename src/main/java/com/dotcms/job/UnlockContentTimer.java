package com.dotcms.job;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

import com.dotmarketing.business.APILocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.Logger;
import com.liferay.portal.model.User;


public class UnlockContentTimer implements StatefulJob {

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {

		Logger.info(this, "Timed Unlock: ------------------------------------------");

		String unlockAfter = OSGiPluginProperties.getProperty("UNLOCK_AFTER_SECONDS");

		Calendar cal = Calendar.getInstance();

		int seconds = Integer.parseInt(unlockAfter);

		cal.add(Calendar.SECOND, -seconds);



		int count = 0;

		try {
			DotConnect db = new DotConnect();
			
			
			int limit  = Integer.parseInt(OSGiPluginProperties.getProperty("SQL_LIMIT_CLAUSE", "1000"));
			long threadSleep  = Integer.parseInt(OSGiPluginProperties.getProperty("THREAD_SLEEP_BETWEEN_UNLOCKS", "50"));
			for(int i=0;i<1000;i++){
				db.setSQL("select identifier, lang, working_inode from contentlet_version_info where contentlet_version_info.locked_on < ? and locked_by is not null");
				db.setMaxRows(limit);
				db.addParam(cal.getTime());
				User system = APILocator.getUserAPI().getSystemUser();
				List<Map<String, Object>> results = db.loadObjectResults();
				if(results.size()==0)break;

				for (Map<String, Object> map : results) {
					Contentlet c = APILocator.getContentletAPI().find(map.get("working_inode").toString(), system, false);
					APILocator.getContentletAPI().unlock(c, system, false);
					count++;
					Thread.sleep(threadSleep);
				}
				
				Logger.info(this.getClass(), "Timed Unlock: run " + i);
			}
			Logger.info(this.getClass(), "Timed Unlock: Unlocked " + count + " contentlets");
			
			
			
		} catch (DotDataException e) {
			Logger.error(this.getClass(), e.getMessage(), e);
		} catch (DotSecurityException e) {
			Logger.error(this.getClass(), e.getMessage(), e);
		} catch (InterruptedException e) {
			Logger.error(this.getClass(), e.getMessage(), e);
			e.printStackTrace();
		} finally {
			try {
				DbConnectionFactory.closeConnection();
			} catch (Exception e) {
				Logger.error(this.getClass(), "Timed Unlock: died trying to close db connection", e);
			}
		}



	}

}