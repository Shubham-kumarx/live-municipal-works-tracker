package com.municipal.tracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {
    private final String cookieName;
    private final boolean secure;
    private final Duration maxAge;

    public AuthCookieService(@Value("${app.security.cookie.name:municipal_auth}") String cookieName,
            @Value("${app.security.cookie.secure:false}") boolean secure,
            @Value("${jwt.expiration}") long expirationMillis) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.maxAge = Duration.ofMillis(expirationMillis);
    }

    public ResponseCookie authenticationCookie(String token) {
        return baseCookie(token, maxAge);
    }

    public ResponseCookie expiredCookie() {
        return baseCookie("", Duration.ZERO);
    }

    private ResponseCookie baseCookie(String value, Duration age) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true).secure(secure).sameSite("Strict")
                .path("/").maxAge(age).build();
    }
}
