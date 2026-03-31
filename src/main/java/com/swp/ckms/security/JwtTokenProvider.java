package com.swp.ckms.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.accessExpiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Long userId, String role,
                                Long storeId, Long coordinatorId, String scope) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", new String[]{role})
                .claim("scope", scope);

        if (storeId != null) {
            builder.claim("storeId", storeId);
        }
        if (coordinatorId != null) {
            builder.claim("coordinatorId", coordinatorId);
        }

        return builder
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        return getClaimsFromToken(token).get("userId", Long.class);
    }

    public Long getStoreIdFromToken(String token) {
        return getClaimsFromToken(token).get("storeId", Long.class);
    }

    public Long getCoordinatorIdFromToken(String token) {
        return getClaimsFromToken(token).get("coordinatorId", Long.class);
    }

    public String getScopeFromToken(String token) {
        return getClaimsFromToken(token).get("scope", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // --- Aliases requested by user for JwtService standard ---
    public String generateAccessToken(com.swp.ckms.entity.User user) {
        Long storeId = user.getStore() != null ? user.getStore().getStoreId() : null;
        Long coordinatorId = user.getKitchen() != null ? user.getKitchen().getKitchenId() : null;
        
        // Single Kitchen Refactor: Default to ID 1 for relevant roles
        if (coordinatorId == null && user.getRole() != null) {
            String role = user.getRole().getRoleName().toUpperCase();
            if (role.equals("COORDINATOR") || role.equals("KITCHEN_STAFF") || role.equals("MANAGER")) {
                coordinatorId = 1L;
            }
        }

        String scope = user.getRole() != null ? user.getRole().getRoleName() : "USER";
        return generateToken(user.getUsername(), user.getUserId(), scope, storeId, coordinatorId, scope);
    }

    public String generateRefreshToken(com.swp.ckms.entity.User user) {
        return java.util.UUID.randomUUID().toString();
    }

    public String extractUsername(String token) {
        return getUsernameFromToken(token);
    }
}
