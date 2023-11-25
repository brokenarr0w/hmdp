package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Blog;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {
    @Resource
    RedisTemplate<String,String> redisTemplate;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Test
    void testStr(){
        Blog blog = Blog.builder()
                .id(1L)
                .content("ss")
                .images("100")
                .title("00")
                .build();
        stringRedisTemplate.opsForValue().set("user::100",JSONUtil.toJsonStr(blog));
        String s = stringRedisTemplate.opsForValue().get("user::100");
        System.out.println(s);
    }
}
