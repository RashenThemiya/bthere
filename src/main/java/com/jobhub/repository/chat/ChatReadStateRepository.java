package com.jobhub.repository.chat;

import com.jobhub.entity.chat.ChatReadState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, Long> {
    Optional<ChatReadState> findByJobIdAndUserId(Long jobId, Long userId);

    List<ChatReadState> findAllByJobId(Long jobId);
}
