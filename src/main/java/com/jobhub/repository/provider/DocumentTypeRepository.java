package com.jobhub.repository.provider;

import com.jobhub.entity.provider.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<DocumentType> findAllByStatusOrderByNameAsc(String status);
}
