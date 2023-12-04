package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author 湛蓝之翼
 * @version 1.0
 * @description 全局唯一ID生成器
 * @date 2023/12/4 10:37
 */
@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1701388800L;
    private static final int BIT_COUNT = 32;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix){
        //1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long time = nowEpochSecond - BEGIN_TIMESTAMP;
        //2. 生成序列号
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3. 拼接并返回
        return time << BIT_COUNT | increment;
    }

}
