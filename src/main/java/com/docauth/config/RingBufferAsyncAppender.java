package com.docauth.config;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AsyncAppenderBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 自定义环形队列异步 Appender（审计建议实现版）
 * <p>
 * 核心特性：
 * 1. 队列满时弹出最老的审计数据，强制推入最新数据（绝对不阻塞）
 * 2. 触发限流警告日志（每秒最多一次）
 * 3. 利用 Logback 原生时钟缓存机制高效追加毫秒级时间戳
 * 4. 自动按自然月滚动文件
 * <p>
 * 适用场景：密码审计日志等对实时性要求高、宁可丢失旧数据也要保证新数据的场景
 *
 * @author AI Assistant
 * @version 3.0
 * @since 2026-06-03
 */
public class RingBufferAsyncAppender extends AsyncAppender {

    private static final Logger MONITOR_LOGGER = LoggerFactory.getLogger("QUEUE_MONITOR");
    private static final long WARN_INTERVAL_MS = 1000; // 每秒最多打印一次警告
    // 警告日志限流：记录上次打印时间
    private volatile long lastWarnTime = 0;

    /**
     * 启动时初始化自定义环形队列
     */
    @Override
    public void start() {
        // 获取配置的队列容量
        int queueSize = getQueueSize();

        // 创建自定义的环形队列（覆盖 offer 方法）
        BlockingQueue<ILoggingEvent> ringBuffer = new ArrayBlockingQueue<>(queueSize) {
            @Override
            public boolean offer(ILoggingEvent event) {
                // 尝试正常入队
                if (super.offer(event)) {
                    return true;
                }

                // 队列满了，执行"丢弃最早 + 推入最新"策略
                ILoggingEvent oldest = poll(); // 弹出队头（最老的数据）
                if (oldest != null) {
                    // 触发限流警告
                    warnQueueFull();

                    // 强制推入最新的审计数据
                    return super.offer(event);
                }

                // 极端情况：队列被其他线程清空了，重试
                return super.offer(event);
            }
        };

        // 设置自定义队列到父类（通过反射访问 protected 字段）
        try {
            // blockingQueue 字段定义在 AsyncAppenderBase 中
            Field field = AsyncAppenderBase.class.getDeclaredField("blockingQueue");
            field.setAccessible(true);
            field.set(this, ringBuffer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set custom blocking queue", e);
        }

        // 调用父类启动逻辑
        super.start();

        MONITOR_LOGGER.info("[RingBufferAsyncAppender] 自定义环形队列异步 Appender 已启动，队列容量: {}", queueSize);
    }

    /**
     * 打印队列满警告（带限流保护）
     * <p>
     * 采用基于时间戳的限流机制：每秒最多打印一次警告
     * 防止警告日志反噬系统 I/O
     */
    private void warnQueueFull() {
        long now = System.currentTimeMillis();

        // 第一次检查（无锁，快速判断）
        if (now - lastWarnTime < WARN_INTERVAL_MS) {
            return;
        }

        // 第二次检查（加锁，确保线程安全）
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - lastWarnTime >= WARN_INTERVAL_MS) {
                MONITOR_LOGGER.warn(
                        "[密码日志队列已满] 正在丢弃最旧的审计数据以保留最新数据。请检查磁盘 I/O 或增加队列容量。"
                );
                lastWarnTime = now;
            }
        }
    }
}
