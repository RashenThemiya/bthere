package com.jobhub.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserStatusRequest(@NotBlank String status) {}
