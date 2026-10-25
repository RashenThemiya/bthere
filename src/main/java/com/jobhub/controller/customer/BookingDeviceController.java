package com.jobhub.controller.customer;
import com.jobhub.entity.auth.UserDevice;
import com.jobhub.repository.auth.*;
import com.jobhub.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/me/booking-devices")
public class BookingDeviceController {
    private final UserDeviceRepository devices;
    private final UserRepository users;
    public record Registration(@NotBlank @Size(max=255) String token,
                               @NotBlank @Pattern(regexp="ANDROID|IOS") String platform,
                               @Size(max=100) String deviceName) {}
    public record Registered(Long deviceId) {}
    @PostMapping @Transactional
    public Registered register(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Registration input) {
        users.lockById(user.id()).orElseThrow();
        var matching=devices.findAllByDeviceToken(input.token());
        matching.forEach(d -> d.setStatus("INACTIVE"));
        var device=matching.stream().filter(d -> user.id().equals(d.getUserId())).findFirst().orElseGet(UserDevice::new);
        device.setUserId(user.id());device.setDeviceToken(input.token());device.setPlatform(input.platform());
        device.setDeviceName(input.deviceName());device.setStatus("ACTIVE");device.setLastActiveAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        devices.saveAndFlush(device);
        var active=devices.findAllByUserIdAndStatusOrderByDeviceIdDesc(user.id(),"ACTIVE");
        // Bound fanout and retain the most recently registered devices.
        active.stream().filter(d -> !d.getDeviceId().equals(device.getDeviceId())).skip(9).forEach(d -> d.setStatus("INACTIVE"));
        return new Registered(device.getDeviceId());
    }
    @DeleteMapping("/{id}") @Transactional
    public void unregister(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) {
        var d=devices.findById(id).filter(v -> user.id().equals(v.getUserId()))
                .orElseThrow(() -> new com.jobhub.exception.ResourceNotFoundException("Device not found"));
        d.setStatus("INACTIVE");
    }
}
