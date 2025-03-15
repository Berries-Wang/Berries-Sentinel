package com.Berries.Wang.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SentinelStuService {

    @SentinelResource(
        value = "com.Berries.Wang.controller.DebugFlowCodeFactorController#/sentinel/flow/_testSentinelByCaller_demo1",
        blockHandler = "codeFactorBlockHandlerCaller")
    public Map<String, Object> _testSentinelByCaller_demo1(String appName) {
        System.out.println("----> 请求/_testSentinelByCaller_demo1");
        HashMap<String, Object> rsp = new HashMap<>();
        rsp.put("status", 200);
        rsp.put("api", "_testSentinelByCaller_demo1");
        return rsp;
    }

    @SentinelResource(
        value = "com.Berries.Wang.controller.DebugFlowCodeFactorController#/sentinel/flow/testSentinelByCaller_demo2",
        blockHandler = "codeFactorBlockHandlerCaller")
    public Map<String, Object> _testSentinelByCaller_demo2(String appName) {
        System.out.println("----> 请求/_testSentinelByCaller_demo2");
        HashMap<String, Object> rsp = new HashMap<>();
        rsp.put("status", 200);
        rsp.put("api", "testSentinelByCaller_demo2");
        return rsp;
    }

    public Map<String, Object> codeFactorBlockHandlerCaller(BlockException e) {
        System.out.println("----> 限流/codeFactorBlockHandler");

        HashMap<String, Object> rsp = new HashMap<>();
        rsp.put("status", 200);
        rsp.put("api", "限流: com.Berries.Wang.controller.DebugFlowCodeFactorController");
        return rsp;
    }
}
