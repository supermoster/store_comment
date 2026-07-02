package com.foodiego.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodiego.dto.Result;
import com.foodiego.entity.Voucher;
import com.foodiego.mapper.VoucherMapper;
import com.foodiego.entity.SeckillVoucher;
import com.foodiego.service.ISeckillVoucherService;
import com.foodiego.service.IVoucherOrderService;
import com.foodiego.service.IVoucherService;
import com.foodiego.utils.RedisConstants;
import com.foodiego.utils.RedisIdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author FoodieGo Team
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplates;


    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);

        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        int time = voucher.getEndTime().getSecond() - voucher.getBeginTime().getSecond();
        // 保存秒杀券库存到Redis中-String（带 TTL）
        stringRedisTemplates.opsForValue()
                .set(RedisConstants.SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString(),
                        time, TimeUnit.SECONDS);
    }


}
