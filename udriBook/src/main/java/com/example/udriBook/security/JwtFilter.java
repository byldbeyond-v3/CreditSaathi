package com.example.udriBook.security;

import com.example.udriBook.service.RedisSessionService;
import com.example.udriBook.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisSessionService redisSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                // STEP 1: Validate JWT cryptographically (CPU-only, ~0.1ms, no DB hit)
                if (jwtUtil.isTokenValid(jwt)) {

                    // STEP 2: Check Redis blacklist for revoked tokens (~0.5ms)
                    if (!redisSessionService.isTokenBlacklisted(jwt)) {
                        username = jwtUtil.extractUsername(jwt);
                    } else {
                        logger.warn("Revoked token used");
                    }
                } else {
                    logger.warn("Invalid or expired JWT token");
                }
            } catch (Exception e) {
                logger.error("Error validating JWT token", e);
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    username, null, new ArrayList<>());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
        filterChain.doFilter(request, response);
    }

    // Public endpoints that do NOT require a JWT token.
    // Keep this list as narrow as possible — every extra entry is a potential
    // security hole.
    private static final java.util.List<String> PUBLIC_PATHS = java.util.List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/phone/",
            "/api/users/profile/pincode-lookup"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
