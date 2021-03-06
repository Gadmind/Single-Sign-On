package com.daop.sso.common.utils.job;

import com.daop.sso.common.schedule.JobInfo;
import com.daop.sso.common.schedule.quartz.QuartzAllowConcurrent;
import com.daop.sso.common.schedule.quartz.QuartzConstants;
import com.daop.sso.common.schedule.quartz.QuartzDisallowConcurrent;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @BelongsProject: springboot_learn
 * @BelongsPackage: com.daop.scheduled.util
 * @Description: 定时任务工具类
 * @DATE: 2020-08-10
 * @AUTHOR: Administrator
 **/
@Component
@Slf4j
public class QuartzUtil {
    private static Scheduler scheduler;

    /**
     * 判断该定时任务是否支持并发
     *
     * @param jobInfo 定时任务信息类
     * @return QuartzAllowConcurrent 支持并发
     * QuartzDisallowConcurrent 不支持并发
     */
    private static Class<? extends Job> getQuartzJobClass(JobInfo jobInfo) {
        boolean concurrent = "0".equals(jobInfo.getJobConcurrent().toString());
        return concurrent ? QuartzAllowConcurrent.class : QuartzDisallowConcurrent.class;
    }

    public static void initScheduleJobs(Scheduler scheduler, List<JobInfo> jobInfoList) throws SchedulerException {
        QuartzUtil.scheduler=scheduler;
        scheduler.clear();
        for (JobInfo jobInfo : jobInfoList) {
            createScheduleJob(jobInfo);
        }
    }

    /**
     * 创建定时任务，定时任务创建
     *
     * @param quartzJobInfo 定时任务信息类
     */
    public static void createScheduleJob(JobInfo quartzJobInfo) {
        try {
            //获取定时任务的执行类
            Class<? extends Job> jobClass = getQuartzJobClass(quartzJobInfo);

            Long jobId = quartzJobInfo.getJobId();
            String jobGroup = quartzJobInfo.getJobGroup();

            //构建定时任务信息
            JobDetail jobInfo = JobBuilder.newJob(jobClass)
                    .withIdentity(getJobKey(jobId, jobGroup))
                    .withDescription(quartzJobInfo.getJobDescription())
                    .build();

            //设置定时任务指定方式
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(quartzJobInfo.getJobCron());
            //设置定时任务错误执行策略
            scheduleBuilder = handleCronScheduleMisfirePolicy(quartzJobInfo, scheduleBuilder);
            //创建定时任务触发器
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(getTriggerKey(jobId, jobGroup))
                    .withSchedule(scheduleBuilder)
                    .build();
            //放入参数，运行时的方法可获取
            jobInfo.getJobDataMap().put(QuartzConstants.TASK_PROPERTIES, quartzJobInfo);
            //判断任务是否存在
            if (scheduler.checkExists(getJobKey(jobId, jobGroup))) {
                //防止创建时存在数据问题 先移除，然后在执行创建操作
                scheduler.deleteJob(getJobKey(jobId, jobGroup));
            }

            //创建定时任务
            scheduler.scheduleJob(jobInfo, trigger);

            //判断任务状态
            if (quartzJobInfo.getJobStatus().equals(QuartzConstants.JobStatus.PAUSE.getValue())) {
                scheduler.pauseJob(getJobKey(jobId, jobGroup));
            }

        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建任务触发对象
     *
     * @param jobId    任务ID
     * @param jobGroup 任务分组
     * @return 触发对象键
     */
    public static TriggerKey getTriggerKey(Long jobId, String jobGroup) {
        return TriggerKey.triggerKey(QuartzConstants.TASK_CLASS_NAME + jobId, jobGroup);
    }


    public static void startJob(JobInfo jobInfo) throws SchedulerException {
        log.info("Job"+jobInfo.getJobId()+"start.");
        scheduler.resumeJob(getJobKey(jobInfo.getJobId(), jobInfo.getJobGroup()));
        log.info("Job"+jobInfo.getJobId()+"start.");
    }

    public static void pauseJob(JobInfo jobInfo) throws SchedulerException {
        log.info("Job"+jobInfo.getJobId()+"pause.");
        scheduler.pauseJob(getJobKey(jobInfo.getJobId(), jobInfo.getJobGroup()));
        log.info("Job"+jobInfo.getJobId()+"paused.");
    }

    public static void deleteJob(JobInfo jobInfo) throws SchedulerException {
        log.info("Job"+jobInfo.getJobId()+"delete.");
        scheduler.deleteJob(getJobKey(jobInfo.getJobId(), jobInfo.getJobGroup()));
        log.info("Job"+jobInfo.getJobId()+"delete finish.");
    }

    /**
     * 构建任务键对象
     *
     * @param jobId    任务ID
     * @param jobGroup 任务分组
     * @return 任务键对象
     */
    public static JobKey getJobKey(Long jobId, String jobGroup) {
        return JobKey.jobKey(QuartzConstants.TASK_CLASS_NAME + jobId, jobGroup);
    }

    /**
     * 定时任务策略
     *
     * @param jobInfo             定时任务信息类
     * @param cronScheduleBuilder 调度器
     * @return 调度器
     */
    public static CronScheduleBuilder handleCronScheduleMisfirePolicy(JobInfo jobInfo, CronScheduleBuilder cronScheduleBuilder) {
        switch (jobInfo.getJobMisfirePolicy()) {
            case QuartzConstants.MISFIRE_DEFAULT:
                return cronScheduleBuilder;
            //忽略错误立即执行
            case QuartzConstants.MISFIRE_IGNORE_MISFIRE:
                return cronScheduleBuilder.withMisfireHandlingInstructionIgnoreMisfires();
            //任务执行一次
            case QuartzConstants.MISFIRE_FIRE_AND_PROCEED:
                return cronScheduleBuilder.withMisfireHandlingInstructionFireAndProceed();
            //放弃执行
            case QuartzConstants.MISFIRE_DO_NOTHING:
                return cronScheduleBuilder.withMisfireHandlingInstructionDoNothing();
            default:
                throw new RuntimeException("定时任务执行错误");
        }
    }
}
