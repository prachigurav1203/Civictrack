package com.civictrack.dto;

import com.civictrack.model.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$";
    public static final String PASSWORD_MSG = "must be 8-100 characters with an upper-case letter, a lower-case letter and a digit";
    public static final String PHONE_REGEX = "^$|^[0-9+\\-() ]{7,20}$";

    // ------------------------------------------------------------- auth / users
    public record LoginRequest(@NotBlank @Email @Size(max = 150) String email, @NotBlank @Size(max = 100) String password) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @Pattern(regexp = PHONE_REGEX, message = "invalid phone number") String phone) {}

    public record OfficerRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @Pattern(regexp = PHONE_REGEX, message = "invalid phone number") String phone,
            @NotNull Long departmentId) {}

    public record OfficerUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = PHONE_REGEX, message = "invalid phone number") String phone,
            @NotNull Long departmentId) {}

    public record PasswordResetRequest(@NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String newPassword) {}

    public record ActiveRequest(boolean active) {}

    public record UserResponse(Long id, String name, String email, String phone, Role role,
                               Long departmentId, String departmentName, boolean active, LocalDateTime createdAt) {
        public static UserResponse from(User u) {
            Department d = u.getDepartment();
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(),
                    d == null ? null : d.getId(), d == null ? null : d.getName(), u.isActive(), u.getCreatedAt());
        }
    }

    public record AuthResponse(String token, UserResponse user) {}

    // -------------------------------------------------------------- departments
    public record DepartmentRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}

    public record DepartmentResponse(Long id, String name, String description) {
        public static DepartmentResponse from(Department d) { return new DepartmentResponse(d.getId(), d.getName(), d.getDescription()); }
    }

    public record CategoryMappingRequest(@NotNull Long departmentId) {}

    public record CategoryMappingResponse(Category category, Long departmentId, String departmentName) {}

    // --------------------------------------------------------- complaint actions
    public record AssignRequest(@NotNull Long departmentId, @NotNull Long officerId, @Size(max = 1000) String remarks) {}

    public record RemarksRequest(@Size(max = 1000) String remarks) {}

    public record PriorityRequest(@NotNull Priority priority, @Size(max = 1000) String remarks) {}

    public record VerifyRequest(@NotNull Boolean approve, @Size(max = 1000) String remarks) {}

    public record FeedbackRequest(@Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {}

    // ---------------------------------------------------------------- responses
    public record HistoryResponse(ComplaintStatus status, String remarks, String changedBy, String changedByRole, LocalDateTime changedAt) {}

    public record ResolutionResponse(Long id, String remarks, String submittedBy, LocalDateTime submittedAt,
                                     ResolutionStatus status, String verifiedBy, LocalDateTime verifiedAt,
                                     String adminRemarks, List<String> photos) {}

    public record FeedbackResponse(int rating, String comment, LocalDateTime createdAt) {}

    public record ComplaintResponse(
            Long id, String trackingId, String title, String description,
            Category category, Priority priority, ComplaintStatus status,
            Double latitude, Double longitude, String address,
            String imageUrl, List<String> photos,
            String citizenName, Long departmentId, String departmentName,
            Long suggestedDepartmentId, String suggestedDepartmentName,
            Long officerId, String officerName,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime resolvedAt,
            LocalDateTime acceptedAt, LocalDateTime slaDueAt, boolean overdue, LocalDateTime escalatedAt,
            List<HistoryResponse> history, List<ResolutionResponse> resolutions, FeedbackResponse feedback) {}

    public record DuplicateResponse(Long id, String trackingId, String title, ComplaintStatus status, Category category,
                                    long distanceMeters, int similarityPercent, LocalDateTime createdAt) {}

    public record NotificationResponse(Long id, NotificationType type, String message, Long complaintId,
                                       String trackingId, boolean read, LocalDateTime createdAt) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record GeoPlace(String displayName, double lat, double lon, String type) {}
}
