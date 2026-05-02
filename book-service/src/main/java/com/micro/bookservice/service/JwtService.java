package com.micro.bookservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtService {

    @Value("${jwt.secret}")
    private String SECRET;
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


    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    public String extractUUID(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    public String extractFirstName(String token) {
        return extractClaim(token, claims -> claims.get("firstName", String.class));
    }
    public String extractLastName(String token) {
        return extractClaim(token, claims -> claims.get("lastName", String.class));
    }


    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
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

    public Boolean validateToken(String token) {
        try {
            // This will throw an exception if the signature is invalid or token is malformed
            Claims claims = extractAllClaims(token);

            // Additional validation: token not expired and has a username
            return !isTokenExpired(token) && claims.get("username", String.class) != null;
        } catch (Exception e) {
            // Token is invalid: bad signature, malformed, expired, etc.
            return false;
        }
    }

}