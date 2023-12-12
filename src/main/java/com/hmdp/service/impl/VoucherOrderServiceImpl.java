package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    ISeckillVoucherService seckillVoucherService;
    @Resource
    RedisIdWorker redisIdWorker;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        //3. 判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        //4. 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        //5. 扣除库存
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                //使用CAS法来解决超卖问题
                .gt("stock",0)
                .update();
        if (!update) {
            return Result.fail("执行更新库存sql失败");
        }
        Long userId = UserHolder.getUser().getId();
        //@Transactional 自调用(实际上是目标对象内的方法调用目标对象的另一个方法)在运行时不会导致实际的事务
        synchronized (userId.toString().intern()) {
            //通过AOP代理拿到代理对象解决自循环事务
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //根据订单ID和用户ID一人一单判断
        Long userId = UserHolder.getUser().getId();
        //intern方法会在new字符串之前会在常量池寻找，如果找到值一样的字符串就直接返回

            Long order = this.query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            if (order > 0) {
                return Result.fail("您已用过优惠券");
            }
            //6. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();

            voucherOrder.setUserId(userId);

            voucherOrder.setVoucherId(voucherId);

            long nextId = redisIdWorker.nextId("order");
            voucherOrder.setId(nextId);
            save(voucherOrder);
            //7. 返回订单ID
            return Result.ok(nextId);
        }
        //锁先释放，事务再提交之前的时间有可能会有并发问题

}
