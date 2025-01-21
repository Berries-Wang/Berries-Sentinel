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
package com.alibaba.csp.sentinel.util;

import java.util.concurrent.TimeUnit;

/**
 * Provides millisecond-level time of OS.(提供操作系统毫秒级别的时间)
 *
 * <p>为什么要启动一个线程来记录系统当前时间?</p>
 * <pre>
 *     Sentinel 用于高并发系统
 * 原因: 确保系统时间的实时性和准确性
 *                    +      +
 *                    +      +--> 保证同一时刻，所有的线程拿到的时间都是一样的
 *                    +---> 直接获取，不用再去计算了
 * ## 性能优化
 *     1.  减少系统调用开销 (避免每个线程都要获取一次当前系统时间--尤其是在高并发的时候)
 *     2.  高并发支持: 在高并发场景下，多个线程可能同时获取系统时间，导致竞争。独立线程记录时间后，其他线程可以直接读取缓存的时间戳，减少竞争，提升性能
 * ## 一致性保障
 *      1. 同一时刻的所有线程拿到的时间都是一样的，不存在差异。
 *
 * </pre>
 *
 * <code>
 *   <pre>
 *     // jvm.cpp
 *    JVM_LEAF(jlong, JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored))
 *           JVMWrapper("JVM_CurrentTimeMillis");
 *           return os::javaTimeMillis();
 *    JVM_END
 *
 *     // time.h
 *     jlong os::javaTimeMillis() {
 *        timeval time;
 *        // 调用操作系统接口
 *        int status = gettimeofday(&time, NULL);
 *        assert(status != -1, "linux error");
 *        return jlong(time.tv_sec) * 1000  +  jlong(time.tv_usec / 1000);
 *      }
 *   </pre>
 * </code>
 *
 * @author qinan.qn
 */
public final class TimeUtil {

    private static volatile long currentTimeMillis;

    static {
        currentTimeMillis = System.currentTimeMillis();
        Thread daemon = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    currentTimeMillis = System.currentTimeMillis();
                    try {
                        // sleep 1ms
                        TimeUnit.MILLISECONDS.sleep(1);
                    } catch (Throwable e) {

                    }
                }
            }
        });
        daemon.setDaemon(true);
        daemon.setName("sentinel-time-tick-thread");
        daemon.start();
    }

    public static long currentTimeMillis() {
        return currentTimeMillis;
    }
}
