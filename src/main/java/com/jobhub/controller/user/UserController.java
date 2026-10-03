package com.jobhub.controller.user;

import com.jobhub.dto.auth.CurrentUserResponse;
import com.jobhub.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(new CurrentUserResponse(
                user.id(),
                user.username(),
                user.email(),
                user.status(),
                user.roles()
        ));
    }
}
