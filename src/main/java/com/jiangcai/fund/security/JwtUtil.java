package com.jiangcai.fund.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT (JSON Web Token) 工具类
 * <p>
 * 负责 JWT 令牌的生成、解析、验证以及提取用户信息。
 * 该类被标记为 Spring 组件，由 Spring 容器管理生命周期。
 * </p>
 *
 * @author jiangcai
 * @version 1.0
 */
@Component
public class JwtUtil {

    /**
     * 签名密钥 (Secret Key)
     * 用于对 JWT 进行签名和验证签名，确保令牌未被篡改。
     * 使用 HMAC-SHA 算法生成，保证密钥长度符合安全要求。
     */
    private final SecretKey secretKey;

    /**
     * 令牌过期时间 (单位：毫秒)
     * 从配置文件注入，控制 Token 的有效时长。
     */
    private final long expiration;

    /**
     * 构造函数
     * <p>
     * 通过 Spring @Value 注解注入配置文件中的密钥字符串和过期时间。
     * 使用 io.jsonwebtoken.security.Keys 工具类将字符串转换为符合 HS256 算法要求的 SecretKey。
     * </p>
     *
     * @param secret     来自配置文件的密钥字符串 (jwt.secret)
     * @param expiration 来自配置文件的过期时间毫秒数 (jwt.expiration)
     */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        // 将字符串密钥转换为安全的 SecretKey 对象，适配 HS256 签名算法
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * 生成 JWT 令牌
     * <p>
     * 根据用户名和用户 ID 构建包含声明 (Claims) 的 Token。
     * </p>
     *
     * @param username 用户名 (将作为 Token 的 Subject)
     * @param userId   用户 ID (将作为自定义 Claim 存入 Token)
     * @return 生成的加密 JWT 字符串
     */
    public String generateToken(String username, Long userId) {
        return Jwts.builder()
                .setSubject(username) // 设置令牌主体（通常为用户名）
                .claim("userId", userId) // 添加自定义声明，存储用户 ID
                .setIssuedAt(new Date()) // 设置令牌签发时间
                .setExpiration(new Date(System.currentTimeMillis() + expiration)) // 设置令牌过期时间
                .signWith(secretKey, SignatureAlgorithm.HS256) // 使用密钥和 HS256 算法进行签名
                .compact(); // 压缩并序列化为字符串
    }

    /**
     * 从 Token 中提取用户名
     *
     * @param token JWT 字符串
     * @return 用户名 (Subject)
     */
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token JWT 字符串
     * @return 用户 ID (从自定义 claim "userId" 中获取)
     */
    public Long extractUserId(String token) {
        return getClaims(token).get("userId", Long.class);
    }

    /**
     * 验证 Token 是否有效
     * <p>
     * 校验逻辑包括：
     * 1. Token 签名是否正确 (由 getClaims 内部解析时验证)
     * 2. Token 是否过期
     * 3. Token 中的用户名是否与当前登录用户一致
     * </p>
     *
     * @param token       待验证的 JWT 字符串
     * @param userDetails Spring Security 用户详情对象
     * @return true 如果验证通过，否则 false
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            // 1. 提取 Token 中的用户名
            String username = extractUsername(token);
            // 2. 校验用户名匹配 且 Token 未过期
            // 注意：如果 Token 过期或签名错误，getClaims 或 isTokenExpired 会抛出异常，被 catch 捕获
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 捕获所有 JWT 相关异常（如签名错误、格式错误、过期等）及参数非法异常
            return false;
        }
    }

    /**
     * 解析 Token 并获取 Claims 主体
     * <p>
     * 此方法会验证签名，如果签名无效或 Token 格式错误，将抛出 JwtException。
     * </p>
     *
     * @param token JWT 字符串
     * @return Claims 对象，包含 Token 中的所有声明信息
     * @throws JwtException 如果 Token 无效或签名不匹配
     */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey) // 设置验证签名所需的密钥
                .build()
                .parseClaimsJws(token) // 解析并验证 JWS (Signed JWT)
                .getBody(); // 获取负载部分 (Claims)
    }

    /**
     * 判断 Token 是否已过期
     *
     * @param token JWT 字符串
     * @return true 如果已过期，false 如果仍在有效期内
     */
    private boolean isTokenExpired(String token) {
        // 获取 Token 中的过期时间字段，并与当前时间比较
        return getClaims(token).getExpiration().before(new Date());
    }
}