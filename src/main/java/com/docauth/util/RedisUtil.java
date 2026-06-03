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
     * 检查键是否存在
     */
    public boolean exists(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 重置键的过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return stringRedisTemplate.expire(key, timeout, unit);
    }

    /**
     * 将对象添加到Set中
     */
    public void addToSet(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForSet().add(key, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("对象序列化失败", e);
        }
    }

    /**
     * 获取Set中的所有对象
     */
    public <T> java.util.Set<T> getSetMembers(String key, Class<T> clazz) {
        java.util.Set<String> members = stringRedisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        try {
            java.util.Set<T> result = new java.util.HashSet<>();
            for (String json : members) {
                result.add(objectMapper.readValue(json, clazz));
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("对象反序列化失败", e);
        }
    }

    /**
     * 设置Set的过期时间
     */
    public boolean expireSet(String key, long timeout, TimeUnit unit) {
        return stringRedisTemplate.expire(key, timeout, unit);
    }
}