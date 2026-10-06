package com.jobhub.repository.job;

import com.jobhub.entity.job.JobCustomFieldAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JobCustomFieldAnswerRepository extends JpaRepository<JobCustomFieldAnswer, Long> {
    List<JobCustomFieldAnswer> findAllByJobIdOrderByAnswerIdAsc(Long jobId);
    List<JobCustomFieldAnswer> findAllByJobIdIn(Collection<Long> jobIds);
}
