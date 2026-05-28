---
--- Created by HP.
--- DateTime: 2026/4/10 9:13
--- 优化：先检查重复（轻量操作），再用 decr 原子扣库存

local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 先检查重复下单（SISMEMBER 比 DECR 更轻量）
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 原子扣减库存，如果不够则回滚
local stock = redis.call('decr', stockKey)
if (stock < 0) then
    redis.call('incr', stockKey)
    return 1
end

-- 标记用户已下单
redis.call('sadd', orderKey, userId)
return 0