/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 流控规则: 排队等待
 */
public class RateLimiterController implements TrafficShapingController {

    private final int maxQueueingTimeMs;
    private final double count;

    /**
     * 上一次请求通过时间
     */
    private final AtomicLong latestPassedTime = new AtomicLong(-1);

    public RateLimiterController(int timeOut, double count) {
        this.maxQueueingTimeMs = timeOut;
        this.count = count;
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        // Pass when acquire count is less or equal than 0.
        if (acquireCount <= 0) {
            return true;
        }
        // Reject when count is less or equal than 0.
        // Otherwise,the costTime will be max of long and waitTime will overflow in some cases.
        if (count <= 0) {
            return false;
        }

        long currentTime = TimeUtil.currentTimeMillis();
        /**
         * Calculate the interval between every two requests. (计算每两次请求之间的间隔。)
         */
        long costTime = Math.round(1.0 * (acquireCount) / count * 1000);

        /**
         * Expected pass time of this request. (预期本次请求通过时间)
         */
        long expectedTime = costTime + latestPassedTime.get();

        if (expectedTime <= currentTime) {  // 预期时间早于当前时间,立即处理
            // Contention may exist here, but it's okay.
            latestPassedTime.set(currentTime);
            return true;
        } else { // 反之，只能等到未来的某个时间处理了
            /**
             * Calculate the time to wait. 计算等待时间
             */
            long waitTime = costTime + latestPassedTime.get() - TimeUtil.currentTimeMillis();

            /**
             * 等待时间大于在队列中最大等待时间，直接返回： 等待已超时
             */
            if (waitTime > maxQueueingTimeMs) {
                return false;
            } else {
                // 等待未超时，oldTime就是本次请求预期处理时间
                long oldTime = latestPassedTime.addAndGet(costTime);
                try {
                    // 重新计算等待时间
                    waitTime = oldTime - TimeUtil.currentTimeMillis();
                    if (waitTime > maxQueueingTimeMs) {
                        latestPassedTime.addAndGet(-costTime);
                        return false;
                    }
                    // in race condition waitTime may <= 0
                    if (waitTime > 0) { // 阻塞当前线程
                        Thread.sleep(waitTime);
                    }
                    return true;
                } catch (InterruptedException e) {
                }
            }
        }
        return false;
    }

}
