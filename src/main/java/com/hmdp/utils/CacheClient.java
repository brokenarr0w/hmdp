package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author 湛蓝之翼
 * @version 1.0
 * @description 缓存工具类
 * @date 2023/11/25 13:08
 */
@Component
public class CacheClient {


    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将键值对及过期时间存入redis
     * @param key 存入redis的key
     * @param value 存入redis的值
     * @param time 过期时间
     * @param unit 过期时间单位
     * @param <T> value的类型
     */
    public <T> void set(String key, T value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    /**
     * 将键值对及逻辑过期时间存入redis
     * @param key 存入redis的key
     * @param value 存入redis的值
     * @param time 逻辑过期时间
     * @param unit 逻辑过期时间单位
     * @param <T> value的类型
     */
    public <T> void setWithLogicalExpire(String key, T value, Long time, TimeUnit unit){
        // 设置逻辑过期时间
        RedisData data = new RedisData();
        data.setData(value);
        data.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(data));
    }

    /**
     * 缓存空对象来解决缓存穿透问题
     * @param keyPrefix redis的键前缀
     * @param id 存入redis的键
     * @param type 存入redis的值类型
     * @param dbFallback 数据库查询语句函数
     * @param time 过期时间
     * @param unit 过期时间单位
     * @return 查询到的值
     * @param <T> 值的类型
     * @param <R> id的类型
     */
    public <T,R> T queryWithPassThrough(String keyPrefix, R id, Class<T> type, Function<R,T> dbFallback,Long time, TimeUnit unit){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if(CharSequenceUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }
        if(json != null) {
            return null;
        }
        // 数据库查询
        T t = dbFallback.apply(id);
        if(t == null){
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        this.set(key,t,time,unit);
        return t;
    }
    //新线程
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期时间解决缓存击穿
     * @param keyPrefix 存入redis的键前缀
     * @param lockPrefix 互斥锁前缀
     * @param id 键
     * @param type 值类型
     * @param dbFallback 数据库查询函数
     * @param time 过期时间
     * @param unit 过期时间类型
     * @return 查到的值
     * @param <T> 值类型
     * @param <R> id类型
     */

    public <T,R> T queryWithLogicalExpire(String keyPrefix,String lockPrefix,R id,Class<T> type,Function<R,T> dbFallback,Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json)){
            //不存在，直接返回。
            return null;
        }
        //命中，需要先把json反序列化成对象
        RedisData data = new RedisData();
        T bean = JSONUtil.toBean((JSONObject) data.getData(), type);
        LocalDateTime expireTime = data.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //如果数据未过期,直接返回
            return bean;
        }
        String lockKey = lockPrefix + id;
        //获取互斥锁
        boolean isLock = tryLock(lockKey);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    T t = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,t,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        return bean;
    }

    /**
     * 释放互斥锁
     * @param lockKey 互斥锁的redis键
     */

    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    /**
     * 获取互斥锁
     * @param lockKey 互斥锁的redis键
     * @return 获取成功与否
     */
    private boolean tryLock(String lockKey) {
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.MINUTES));
    }
}
