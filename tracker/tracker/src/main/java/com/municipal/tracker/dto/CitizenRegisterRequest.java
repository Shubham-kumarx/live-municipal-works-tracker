package com.municipal.tracker.dto;

import com.municipal.tracker.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CitizenRegisterRequest {
    @NotBlank private String fullName;
    @NotBlank @Email private String email;
    @NotBlank @Size(min = 6) private String password;
    @NotBlank private String phone;
    @NotNull(message = "Ward is required") private Long wardId;

    public RegisterRequest toRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName(fullName); request.setEmail(email); request.setPassword(password);
        request.setPhone(phone); request.setWardId(wardId); request.setRole(Role.CITIZEN);
        return request;
    }
}
