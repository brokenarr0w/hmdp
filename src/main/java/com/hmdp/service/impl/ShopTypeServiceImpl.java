package com.hmdp.service.impl;

import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String shopTypeJson = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_TYPE_KEY);
        if(StrUtil.isNotBlank(shopTypeJson)){
            List<ShopType> shopType = JSONUtil.parseArray(shopTypeJson).toList(ShopType.class);
            return Result.ok(shopType);
        }else{
            QueryWrapper<ShopType> shopTypeQueryWrapper = new QueryWrapper<>();
            shopTypeQueryWrapper.orderByDesc("sort");
            List<ShopType> list = list(shopTypeQueryWrapper);
            if(list == null){
                return Result.fail("这个商店种类不存在");
            }
            String value = JSONUtil.toJsonStr(list);
            stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_TYPE_KEY,value);
            return Result.ok(list);
        }

    }
}
