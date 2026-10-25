package com.jobhub.repository.auth;
import com.jobhub.entity.auth.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserDeviceRepository extends JpaRepository<UserDevice,Long> {
    List<UserDevice> findAllByDeviceToken(String token);
    List<UserDevice> findAllByUserIdAndStatusOrderByDeviceIdDesc(Long userId,String status);
}
