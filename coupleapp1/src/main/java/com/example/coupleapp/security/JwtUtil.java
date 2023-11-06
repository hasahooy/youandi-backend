package com.example.coupleapp.security;

import com.example.coupleapp.dto.TokenDTO;
import com.example.coupleapp.entity.RefreshTokenEntity;
import com.example.coupleapp.repository.RefreshTokenRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.awt.image.Kernel;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    public static final String ACCESS_TOKEN = "Access_Token";
    public static final String REFRESH_TOKEN = "Refresh_Token";
    // 사용자 권한 값의 KEY
    public static final String AUTHORIZATION_KEY = "auth";
    // Token 식별자
    public static final String BEARER_PREFIX = "Bearer ";

    // Token 만료 시간
    private static final long ACCESS_TIME =  24 * 60 * 60 * 1000L;// 24시간
    private static final long REFRESH_TIME =  7*24*60 * 60 * 1000L;// 일주일

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.secret}")
    private String secretKey;
    private Key key;
    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

    @PostConstruct
    public void init() {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        key = Keys.hmacShaKeyFor(bytes);
    }

    public TokenDTO creatAllToken(String email, Long memberId) {
        return new TokenDTO(createToken(email,memberId,"Access"),createToken(email,memberId,"Refresh"));
    }


    // Token 생성
    public String createToken(String email,Long memberId, String type) {
        Date date = new Date();
        Claims claims = Jwts.claims()
                .setSubject(email)
                .setId(String.valueOf(memberId));
        long time = type.equals("Access") ? ACCESS_TIME : REFRESH_TIME;

        return BEARER_PREFIX +
                Jwts.builder()
                        .setClaims(claims)
                        .setExpiration(new Date(date.getTime() + time))
                        .setIssuedAt(date) // 발급일
                        .signWith(key, signatureAlgorithm)
                        .compact();
    }
    // header 에서 JWT 가져오기
    public String getHeaderToken(HttpServletRequest request,String type) {
        String bearerToken = type.equals("Access") ? request.getHeader(ACCESS_TOKEN) : request.getHeader(REFRESH_TOKEN);
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    // 토큰 검증
    public boolean tokenValidation(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException | SignatureException e) {
            log.error("Invalid JWT signature, 유효하지 않는 JWT 서명 입니다.");
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token, 만료된 JWT token 입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token, 지원되지 않는 JWT 토큰 입니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims is empty, 잘못된 JWT 토큰 입니다.");
        }
        return false;
    }

    //토큰에서 사용자 정보 가져오기
    public Claims getUserInfoFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    //refreshToken 토큰 검증
    public Boolean refreshTokenValidation(String token) {
        //1차 토큰 검증
        if(!tokenValidation(token)) return false;

        Claims info = getUserInfoFromToken(token);

        // DB에 저장한 토큰 비교
        Optional<RefreshTokenEntity> refreshToken = refreshTokenRepository.findByEmail(info.getSubject());

        return refreshToken.isPresent() && token.equals(refreshToken.get().getRefreshToken().substring(7));
    }

    public Long getMemberId(String jwtToken) {
        Claims claims = getUserInfoFromToken(jwtToken);
        return Long.parseLong(claims.getSubject());
    }
}
