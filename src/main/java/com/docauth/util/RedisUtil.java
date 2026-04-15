package com.docauth.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    /**
     * 存储字符串到Redis
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }
    
    /**
     * 获取Redis中的字符串
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }
    
    /**
     * 删除Redis中的键
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }
    
    /**
     * 检查键是否存在
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }
    
    /**
     * 重置键的过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, timeout, unit));
    }
}