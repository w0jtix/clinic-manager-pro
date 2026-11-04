package com.clinic.clinicmanager.config.security.jwt;

import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {
    public static final String JWT_TYPE = "Bearer";
    public static final String COMPANY_MODE_CLAIM = "companyMode";
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}")
    private int jwtExpirationMs;

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return generateJwtToken(userPrincipal.getUsername(), false);
    }

    public String generateJwtToken(String username, boolean companyMode) {
        JwtBuilder builder = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs));
        if (companyMode) {
            builder.claim(COMPANY_MODE_CLAIM, true);
        }
        return builder.signWith(SignatureAlgorithm.HS512, jwtSecret).compact();
    }

    public boolean isCompanyModeToken(String token) {
        return isCompanyMode(Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token).getBody());
    }

    public static boolean isCompanyMode(Claims claims) {
        return Boolean.TRUE.equals(claims.get(COMPANY_MODE_CLAIM, Boolean.class));
    }

    public long getRemainingExpirationMs(String token) {
        Date expiration = Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token).getBody().getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }

    public Claims parseValidClaims(String authToken) {
        try {
            return Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken).getBody();
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }

        return null;
    }
}
