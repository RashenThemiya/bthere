package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_custom_field_answer", uniqueConstraints = @UniqueConstraint(
        name = "uk_job_custom_field_answer", columnNames = {"job_id", "field_id"}))
@Getter
@Setter
@NoArgsConstructor
public class JobCustomFieldAnswer extends CreatedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long answerId;
    private Long jobId;
    private Long fieldId;
    private String fieldCode;
    private String fieldLabel;
    private Integer fieldVersion;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String valueJson;
}
