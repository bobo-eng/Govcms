package gov.cms.admin.scheduler;

import gov.cms.admin.service.PublishExecutor;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class PublishQuartzJob implements Job {

  public static final String JOB_DATA_PUBLISH_JOB_ID = "publishJobId";
  public static final String JOB_DATA_ENVIRONMENT = "environment";

  private final PublishExecutor publishExecutor;

  public PublishQuartzJob(PublishExecutor publishExecutor) {
    this.publishExecutor = publishExecutor;
  }

  @Override
  public void execute(JobExecutionContext context) {
    JobDataMap data = context.getMergedJobDataMap();
    Long jobId = data.getLong(JOB_DATA_PUBLISH_JOB_ID);
    String environment = data.getString(JOB_DATA_ENVIRONMENT);
    publishExecutor.execute(jobId, environment);
  }
}
