package com.municipal.tracker.dto;

import com.municipal.tracker.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CitizenRegisterRequest {
    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must be at most 255 characters")
    private String fullName;
    @NotBlank(message = "Email is required") @Email(message = "Please provide a valid email")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters")
    private String password;
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must contain exactly 10 digits")
    private String phone;
    @NotNull(message = "Ward is required") @Positive(message = "Ward id must be positive")
    private Long wardId;

    public RegisterRequest toRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName(fullName); request.setEmail(email); request.setPassword(password);
        request.setPhone(phone); request.setWardId(wardId); request.setRole(Role.CITIZEN);
        return request;
    }
}
