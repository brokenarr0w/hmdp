package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送验证码
     * @param phone 手机号
     * @param session session对象
     * @return 操作信息（成功与否）
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
            if (RegexUtils.isPhoneInvalid(phone)) {
                return Result.fail("手机号格式不正确");
            }
            String random = RandomUtil.randomNumbers(6);
            stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,random,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);


            log.info("手机验证码为{}",random);
            return Result.ok();

        }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }
        // 获取redis的验证码
        String str = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(str == null || !str.equals(code)){
            return Result.fail("验证码不正确");
        }
        User user = query().eq("phone", phone).one();
        if(user == null){
            user = createUserByPhone(phone);
        }
        // 生成uuid作为token
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        //将user转换成hashmap
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions
                        .create()
                        .setIgnoreNullValue(false)
                        .setFieldValueEditor((name,value) -> value.toString())
        );
        //将user放入redis中并设置有效期
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,userMap);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL,TimeUnit.SECONDS);
        return Result.ok(token);
    }

    @Override
    public Result getUserById(Long id) {
        User user = getById(id);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);
        return Result.ok(userDTO);

    }

    private User createUserByPhone(String phone) {
        User user = User
                .builder()
                .phone(phone)
                .nickName("user_" + RandomUtil.randomNumbers(10))
                .build();
        save(user);
        return user;
    }
}
