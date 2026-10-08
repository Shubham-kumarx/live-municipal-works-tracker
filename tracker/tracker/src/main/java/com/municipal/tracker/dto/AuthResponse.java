package com.municipal.tracker.dto;

import com.municipal.tracker.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    @JsonIgnore
    private String token;
    private String email;
    private String fullName;
    private Role role;
    private Long wardId;
    private String message;
}
