package com.micro.userservice.service;
import com.micro.userservice.repository.UserInfoRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Component
public class JwtService {
    public static final String SECRET = "5367566859703373367639792F423F452848284D6251655468576D5A71347437";

//    This function generates a JWT token using the username provided.

    public String generateToken(String username, Collection<? extends GrantedAuthority> authorities, String uuid) {
        Map<String, Object> claims = new HashMap<>();
        String roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        claims.put("role", roles);
        return createToken(claims, username,uuid);
    }

// This function generates a JWT token using the username and additional claims provided.
//    Claims are additional information that can be included in the token. For example, you can add roles.

    private String createToken(Map<String, Object> claims, String username,String uuid) {
        claims.put("username", username); // Optionally store username as a claim
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(uuid)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // Token valid for 1 hour
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    //    This function retrieves the signing key used to sign the JWT token.
//    The signing key is a secret key that is used to ensure the integrity of the token.
    private Key getSignKey() {
        // Decode the base64 encoded secret key
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        // Create a signing key using the decoded bytes
        return Keys.hmacShaKeyFor(keyBytes);
    }

//    This function extracts the username from the JWT token.
//    It uses the `extractClaim` method to get the subject (username) from the token's claims.
//    The `extractClaim` method takes a token and a function that defines how to extract the desired claim from the token's claims.
//    The second parameter is a function that extracts the subject (username) from the claims by using `Claims::getSubject` which is a method reference to the `getSubject` method of the `Claims` interface.

    public String extractUUID(String token) {
        return  extractClaim(token, Claims::getSubject);

    }




    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    //    This function extracts the expiration date from the JWT token.
//    It uses the `extractClaim` method to get the expiration date from the token's claims.
//    The second parameter is a function that extracts the expiration date from the claims by using `Claims::getExpiration`, which is a method reference to the `getExpiration` method of the `Claims` interface.
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    //    This function extracts a specific claim from the JWT token.
//    It takes a token and a function that defines how to extract the desired claim from the token's claims.
//    The claimsResolver is a function that takes the `Claims` object and returns the desired claim.It works by first extracting all claims from the token using `extractAllClaims`, and then applying the provided function to those claims to get the specific claim value.
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    //   This function extracts all claims from the JWT token.
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}