package com.swp.ckms.service;

import com.swp.ckms.entity.RefreshToken;
import java.util.Optional;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(Long userId);
    RefreshToken verifyExpiration(RefreshToken token);
    int deleteByUserId(Long userId);
    void deleteByToken(String token);
    Optional<RefreshToken> findByToken(String token);
}
