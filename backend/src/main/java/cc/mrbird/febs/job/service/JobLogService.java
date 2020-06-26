package cc.mrbird.febs.job.service;

import cc.mrbird.febs.common.domain.QueryRequest;
import cc.mrbird.febs.job.domain.JobLog;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;


public interface JobLogService extends IService<JobLog> {
    /**
     * 查询任务日志
     * @param request
     * @param jobLog
     * @return
     */
    IPage<JobLog> findJobLogs(QueryRequest request, JobLog jobLog);

    /**
     * 保存任务日志
     * @param log
     */
    void saveJobLog(JobLog log);

    /**
     * 删除任务日志
     * @param jobLogIds
     */
    void deleteJobLogs(String[] jobLogIds);
}
