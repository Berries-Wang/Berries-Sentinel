package com.Berries.Wang.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(value = "/sentinel/flow")
public class DebugFlowCodeFactorController {

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

    public Map<String, Object> codeFactorBlockHandler(BlockException e) {
        System.out.println("----> 限流/codeFactorBlockHandler");

        HashMap<String, Object> rsp = new HashMap<>();
        rsp.put("status", 200);
        rsp.put("api", "限流: com.Berries.Wang.controller.DebugFlowCodeFactorController.codeFactor");
        return rsp;
    }
}
