package com.Berries.Wang.controller;

import com.Berries.Wang.service.SentinelStuService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(value = "/sentinel/flow")
public class DebugFlowCodeFactorController {

    @Resource
    private SentinelStuService sentinelStuService;

    @ResponseBody
    @RequestMapping(value = "/codeFactor", method = RequestMethod.GET)
    @SentinelResource(value = "com.Berries.Wang.controller.DebugFlowCodeFactorController#/sentinel/flow/codeFactor",
        blockHandler = "codeFactorBlockHandler")
    public Map<String, Object> codeFactor() {
        System.out.println("----> 请求/codeFactor");
        HashMap<String, Object> rsp = new HashMap<>();
        rsp.put("status", 200);
        rsp.put("api", "com.Berries.Wang.controller.DebugFlowCodeFactorController.codeFactor");
        return rsp;
    }

    /**
     * 根据调用者进行限流
     */
    @ResponseBody
    @RequestMapping(value = "/_testSentinelByCaller_demo1", method = RequestMethod.GET)
    public Map<String, Object> testSentinelByCaller_demo1(@RequestParam("app") String appName) {
        /**
         * <pre>
         *  当只是对调用者进行限流时，name 不一定得是资源名称,即不是资源名称也可以达到限流目的。
         * </pre>
         */
        if (StringUtil.isNotBlank(appName)) {
            ContextUtil.enter(
                "com.Berries.Wang.controller.DebugFlowCodeFactorController#/sentinel/flow/_testSentinelByCaller_demo1-1",
                appName);
        }

        return sentinelStuService._testSentinelByCaller_demo1(appName);
    }

    /**
     * 根据调用链路限流
     */
    @ResponseBody
    @RequestMapping(value = "/_testSentinelByCaller_demo2", method = RequestMethod.GET)
    public Map<String, Object> testSentinelByCaller_demo2(@RequestParam("app") String appName,
        @RequestParam("caller") String caller) {
        // 注意: name需要配置为 对应的FlowRule 的com.alibaba.csp.sentinel.slots.block.flow.FlowRule.refResource
        ContextUtil.enter("testSentinelByCaller_demo2-Entrance1");

        return sentinelStuService._testSentinelByCaller_demo2(appName);
    }

    public Map<String, Object> codeFactorBlockHandler(BlockException e) {
        System.out.println("----> 限流/codeFactorBlockHandler");

        HashMap<String, Object> rsp = new HashMap<>();
        rsp.put("status", 200);
        rsp.put("api", "限流: com.Berries.Wang.controller.DebugFlowCodeFactorController");
        return rsp;
    }
}
