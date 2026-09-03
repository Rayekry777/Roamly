package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.VoucherOrder;
import com.ray.exception.BusinessException;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.SeckillVoucherService;
import com.ray.service.VoucherOrderService;
import com.ray.utils.generator.RedisIdWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Redis Stream 异步落库的秒杀订单实现。 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements VoucherOrderService {
    private static final String ORDER_STREAM = "stream.orders";
    private static final String ORDER_GROUP = "g1";
    private static final String ORDER_CONSUMER = "c1";
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>();

    static {
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua-dev/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final SeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redis;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService orderExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voucher-order-consumer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(true);

    public VoucherOrderServiceImpl(
            SeckillVoucherService seckillVoucherService,
            RedisIdWorker redisIdWorker,
            RedissonClient redissonClient,
            StringRedisTemplate redis,
            CurrentUserProvider currentUserProvider,
            PlatformTransactionManager transactionManager) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.redissonClient = redissonClient;
        this.redis = redis;
        this.currentUserProvider = currentUserProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    void startConsumer() {
        initializeGroup();
        orderExecutor.submit(this::consumeOrders);
    }

    @PreDestroy
    void stopConsumer() {
        running.set(false);
        orderExecutor.shutdownNow();
    }

    /** 校验资格并将秒杀订单写入 Redis Stream。 */
    @Override
    public Long createSeckillOrder(Long voucherId) {
        Long userId = currentUserProvider.requireUserId();
        long orderId = redisIdWorker.nextId("order");
        Long result = redis.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                Long.toString(orderId));
        if (result == null) throw new BusinessException(500, "SECKILL_UNAVAILABLE", "秒杀服务暂时不可用");
        if (result == 1) throw BusinessException.conflict("VOUCHER_OUT_OF_STOCK", "库存不足");
        if (result == 2) throw BusinessException.conflict("VOUCHER_ALREADY_ORDERED", "不能重复下单");
        initializeGroup();
        return orderId;
    }

    @SuppressWarnings("unchecked") // Spring Data Redis 的泛型 StreamOffset 以 varargs 暴露，调用点会产生不可避免的数组警告。
    private void consumeOrders() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                        .read(
                                Consumer.from(ORDER_GROUP, ORDER_CONSUMER),
                                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                StreamOffset.create(ORDER_STREAM, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) continue;
                process(records.getFirst());
            } catch (Exception exception) {
                if (!running.get() || Thread.currentThread().isInterrupted()) return;
                if (isMissingGroup(exception)) initializeGroup();
                else {
                    log.error("[秒杀订单] 消费订单失败", exception);
                    consumePending();
                }
            }
        }
    }

    @SuppressWarnings("unchecked") // 同上，类型由 StringRedisTemplate 和 String 类型的 stream key 共同保证。
    private void consumePending() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                        .read(
                                Consumer.from(ORDER_GROUP, ORDER_CONSUMER),
                                StreamReadOptions.empty().count(1),
                                StreamOffset.create(ORDER_STREAM, ReadOffset.from("0-0")));
                if (records == null || records.isEmpty()) return;
                process(records.getFirst());
            } catch (Exception exception) {
                if (!running.get() || Thread.currentThread().isInterrupted()) return;
                log.error("[秒杀订单] 处理待确认订单失败", exception);
                return;
            }
        }
    }

    private void process(MapRecord<String, Object, Object> record) {
        VoucherOrder order = mapOrder(record.getValue());
        createOrder(order);
        redis.opsForStream().acknowledge(ORDER_STREAM, ORDER_GROUP, record.getId());
    }

    private VoucherOrder mapOrder(Map<Object, Object> values) {
        return new VoucherOrder()
                .setId(asLong(values.get("id")))
                .setUserId(asLong(values.get("userId")))
                .setVoucherId(asLong(values.get("voucherId")));
    }

    private Long asLong(Object value) {
        return Long.valueOf(String.valueOf(value));
    }

    private void createOrder(VoucherOrder order) {
        RLock lock = redissonClient.getLock("lock:order:" + order.getUserId());
        if (!lock.tryLock()) {
            log.warn("[秒杀订单] 用户订单正在处理，用户ID={}", order.getUserId());
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (query().eq("user_id", order.getUserId())
                                .eq("voucher_id", order.getVoucherId())
                                .count()
                        > 0) {
                    log.warn("[秒杀订单] 忽略重复订单，用户ID={}，优惠券ID={}", order.getUserId(), order.getVoucherId());
                    return;
                }
                boolean deducted = seckillVoucherService
                        .update()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id", order.getVoucherId())
                        .gt("stock", 0)
                        .update();
                if (!deducted) {
                    log.warn("[秒杀订单] 数据库库存不足，优惠券ID={}", order.getVoucherId());
                    return;
                }
                save(order);
            });
        } finally {
            lock.unlock();
        }
    }

    private void initializeGroup() {
        try {
            redis.opsForStream().createGroup(ORDER_STREAM, ReadOffset.from("0-0"), ORDER_GROUP);
            log.info("[秒杀订单] 消费组初始化完成，stream={}，group={}", ORDER_STREAM, ORDER_GROUP);
        } catch (Exception exception) {
            if (!contains(exception, "BUSYGROUP")) log.debug("[秒杀订单] 流尚未创建，将在首单后重试");
        }
    }

    private boolean isMissingGroup(Throwable throwable) {
        return contains(throwable, "NOGROUP") || contains(throwable, "NO such key");
    }

    private boolean contains(Throwable throwable, String expected) {
        for (Throwable current = throwable; current != null; current = current.getCause())
            if (current.getMessage() != null && current.getMessage().contains(expected)) return true;
        return false;
    }
}
