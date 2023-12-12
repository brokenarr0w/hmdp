package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IBlogService blogService;
    @Resource
    IUserService userService;
    @Override
    public Result follow(Long id, boolean isFollow) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 判断是关注或取关
            String key = "follows:" + userId;
        if(isFollow){
            // 关注
            Follow follow = Follow.builder()
                    .userId(userId)
                    .followUserId(id)
                    .build();
            boolean save = save(follow);
            //如果关注成功，将关注用户的id放入redis的set集合
            if (save) {
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        }else{
        //取关
            boolean remove = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, id));
            //取消关注以后将redis里的数据删除
            if(remove){
                stringRedisTemplate.opsForSet()
                        .remove(key,id.toString());
            }

        }
        return Result.ok();


    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        int count = Math.toIntExact(lambdaQuery().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id).count());
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //求交集
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        //解析ID集合
        if(intersect == null){
            return Result.ok();
        }
        List<Long> ids = intersect.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        //查询用户
        List<UserDTO> dtoList = userService.listByIds(ids)
                .stream()
                .map(item -> BeanUtil.copyProperties(item, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(dtoList);
    }
}
