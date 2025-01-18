package com.Berries.Wang;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class BerriesSentinelDebugApplication {
    public static void main(String[] args) {
        init_FlowRules();
        SpringApplication.run(BerriesSentinelDebugApplication.class, args);
        System.out.println("Hello World!");
    }

    private static void init_FlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        rule.setResource("com.Berries.Wang.controller.DebugFlowCodeFactorController#/sentinel/flow/codeFactor");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // Set limit QPS to 20.
        rule.setCount(99);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP);
        rule.setWarmUpPeriodSec(20);
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }
}
