package com.example.google.config.jwt;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.example.google.config.oAuth.CustomOAuthDetail;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

	private final String SECRET_KEY;
	private final String COOKIE_REFRESH_TOKEN_KEY;
	private final Long ACCESS_TOKEN_EXPIRE_LENGTH = 1000L * 60 * 60;        // 1hour
	private final Long REFRESH_TOKEN_EXPIRE_LENGTH = 1000L * 60 * 60 * 24 * 7;    // 1week
	private final String AUTHORITIES_KEY = "role";

	public JwtTokenProvider(
		@Value("${app.auth.token.secret_key}") String secretKey,
		@Value("${app.auth.token.refresh-cookie-key}") String cookieRefreshTokenKey
	) {
		this.SECRET_KEY = secretKey;
		this.COOKIE_REFRESH_TOKEN_KEY = cookieRefreshTokenKey;
	}

	// 엑세스 토큰을 생성하는 부분
	public String createAccessToken(Authentication authentication) {

		// 만료시간 정해지는 부분
		Date now = new Date();
		Date vaildity = new Date(now.getTime() + ACCESS_TOKEN_EXPIRE_LENGTH);
		// Authentication 객체에서 유저정보 가져오기
		CustomOAuthDetail userDetails = (CustomOAuthDetail)authentication.getPrincipal();
		// 필요한 정보만 가져오기
		String userId = userDetails.getName();
		String role = authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.collect(Collectors.joining(","));
		// 엑세스 토큰 생성
		return Jwts.builder()
			.signWith(SignatureAlgorithm.HS512, SECRET_KEY)
			.setSubject(userId)
			.claim(AUTHORITIES_KEY, role)
			.setIssuer("debrains")
			.setIssuedAt(now)
			.setExpiration(vaildity)
			.compact();
	}

	// 리플레쉬 토쿤을 생성하는 부분
	public String createRefreshToken(Authentication authentication, HttpServletResponse response) {

		// 만료시간 정해지는 부분
		Date now = new Date();
		Date vaildity = new Date(now.getTime() + REFRESH_TOKEN_EXPIRE_LENGTH);
		// 리플래쉬 토큰 생성
		String refreshToken = Jwts.builder()
			.signWith(SignatureAlgorithm.HS512, SECRET_KEY)
			.setIssuer("debrains")
			.setIssuedAt(now)
			.setExpiration(vaildity)
			.compact();

		// TODO 리플래쉬 토큰 저장
		/////////////////////

		/////////////////////
		return refreshToken;
	}

	// 토큰에서 인가정보 얻어오는 함수
	public Authentication getAuthentication(String token) {

		Claims claims = parseClaims(token);

		String userid = claims.getSubject();

		Collection<? extends GrantedAuthority> authorities =
			Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());

		CustomOAuthDetail principal = new CustomOAuthDetail(Long.valueOf(userid), "", authorities);

		return new UsernamePasswordAuthenticationToken(principal, "", authorities);
	}

	// 토큰의 유효성을 체크하는 부분
	public boolean vailableToken(String accessToken) {
		try {
			Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(accessToken);
			return true;
		} catch (ExpiredJwtException e) {
			log.debug("만료된 JWT 토큰입니다.");
		} catch (UnsupportedJwtException e) {
			log.debug("지원되지 않는 JWT 토큰입니다.");
		} catch (IllegalStateException e) {
			log.debug("잘못된 토큰입니다.");
		}
		return false;
	}

	// Access Token 만료시 갱신때 사용할 정보를 얻기 위해 Claim 리턴
	private Claims parseClaims(String accessToken) {
		try {
			return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(accessToken).getBody();
		} catch (ExpiredJwtException e) {
			return e.getClaims();
		}
	}

}
