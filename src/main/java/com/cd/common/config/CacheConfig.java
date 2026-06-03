package com.cd.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 缓存配置。
 *
 * <p>当前用于缓存用户的鉴权信息（角色与权限），避免每个请求都重复查库。
 * 采用写后过期（5 分钟），并在角色/权限发生变更时由相关写操作主动 evict，
 * 兼顾“改权限尽快生效”与“减少数据库压力”。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 缓存名：userId -> SecurityUser（含角色与权限）。 */
    public static final String USER_AUTH_CACHE = "userAuth";

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(USER_AUTH_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000));
        return cacheManager;
    }
}
