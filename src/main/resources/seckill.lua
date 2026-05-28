---
--- Created by HP.
--- DateTime: 2026/4/10 9:13

--- 优惠券id
local voucherId = ARGV[1]
--- 用户id
local userId = ARGV[2]
--- 订单id
local orderId = ARGV[3]

-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 判断库存是否充足
local stock = tonumber(redis.call('get', stockKey))
if (not stock or stock <= 0) then
    --库存不足
    return 1
end

-- 判断用户是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    --用户重复下单
    return 2
end

-- 扣减库存,添加订单
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
-- 发送消息到Redis消息队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0