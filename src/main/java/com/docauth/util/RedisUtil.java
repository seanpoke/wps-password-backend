package com.docauth.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
     * 存储对象到Redis（序列化为JSON）
     */
    public void setObject(String key, Object value, long timeout, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, timeout, unit);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("对象序列化失败", e);
        }
    }

    /**
     * 从Redis获取对象（反序列化JSON）
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("对象反序列化失败", e);
        }
    }

    /**
     * 删除Redis中的键
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 重置键的过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return stringRedisTemplate.expire(key, timeout, unit);
    }

    /**
     * 自增计数器（按字符串存储的整数），返回自增后的值；键不存在则从 0 自增到 1
     */
    public long incr(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /**
     * 获取键的剩余过期时间（秒），-1 表示无过期时间，-2 表示键不存在
     */
    public long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

}