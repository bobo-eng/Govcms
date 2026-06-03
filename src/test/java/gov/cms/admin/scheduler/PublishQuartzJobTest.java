package gov.cms.admin.scheduler;

import gov.cms.admin.service.PublishExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishQuartzJobTest {

  @Mock
  private PublishExecutor publishExecutor;

  @Mock
  private JobExecutionContext jobExecutionContext;

  @InjectMocks
  private PublishQuartzJob publishQuartzJob;

  @Test
  void execute_shouldDelegateToPublishExecutor() {
    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(PublishQuartzJob.JOB_DATA_PUBLISH_JOB_ID, 1L);
    jobDataMap.put(PublishQuartzJob.JOB_DATA_ENVIRONMENT, "staging");

    when(jobExecutionContext.getMergedJobDataMap()).thenReturn(jobDataMap);

    publishQuartzJob.execute(jobExecutionContext);

    verify(publishExecutor).execute(1L, "staging");
  }
}
