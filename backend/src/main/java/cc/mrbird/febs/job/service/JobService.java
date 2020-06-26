package cc.mrbird.febs.job.service;

import cc.mrbird.febs.common.domain.QueryRequest;
import cc.mrbird.febs.job.domain.Job;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;


public interface JobService extends IService<Job> {

    Job findJob(Long jobId);

    IPage<Job> findJobs(QueryRequest request, Job job);

    void createJob(Job job);

    void updateJob(Job job);

    void deleteJobs(String[] jobIds);

    int updateBatch(String jobIds, String status);

    /**
     * 启动任务
     * @param jobIds
     */
    void run(String jobIds);

    /**
     * 停止任务
     * @param jobIds
     */
    void pause(String jobIds);

    /**
     * 恢复任务
     * @param jobIds
     */
    void resume(String jobIds);

}
