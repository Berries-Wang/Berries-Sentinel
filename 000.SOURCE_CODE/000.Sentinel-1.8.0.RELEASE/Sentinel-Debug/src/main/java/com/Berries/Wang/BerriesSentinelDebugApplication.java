package com.Berries.Wang;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class BerriesSentinelDebugApplication {
    public static void main(String[] args) {
        init_FlowRules();
        SpringApplication.run(BerriesSentinelDebugApplication.class, args);
        System.out.println("Hello World!");
    }

    private static void init_FlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        {
            FlowRule rule = new FlowRule();
            rule.setResource("com.Berries.Wang.controller.DebugFlowCodeFactorController#/sentinel/flow/codeFactor");
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            // Set limit QPS to 20.
            rule.setCount(99);
            rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP);
            rule.setWarmUpPeriodSec(20);
            rules.add(rule);
        }

        {
            /*
             * 根据调用者进行流控
             */
            FlowRule _testSentinelByCaller_demo1 = new FlowRule();
            _testSentinelByCaller_demo1.setResource(
                "com.Berries.Wang.controller.DebugFlowCodeFactorController#/sentinel/flow/_testSentinelByCaller_demo1");
            _testSentinelByCaller_demo1.setLimitApp("BerriesSentinelDebugApplication"); // 将调用方 BerriesSentinelDebugApplication 添加到限流规则里
            _testSentinelByCaller_demo1.setGrade(RuleConstant.FLOW_GRADE_QPS);
            _testSentinelByCaller_demo1.setCount(2);
            _testSentinelByCaller_demo1.setStrategy(RuleConstant.STRATEGY_DIRECT);
            _testSentinelByCaller_demo1.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            rules.add(_testSentinelByCaller_demo1);
        }
        {
            /*
             * 根据调用链路流控
             */
            FlowRule _testSentinelByCaller_demo2 = new FlowRule();
            _testSentinelByCaller_demo2.setResource(
                "com.Berries.Wang.controller.DebugFlowCodeFactorController#/sentinel/flow/testSentinelByCaller_demo2");
            _testSentinelByCaller_demo2.setRefResource("testSentinelByCaller_demo2-Entrance1");
            _testSentinelByCaller_demo2.setGrade(RuleConstant.FLOW_GRADE_QPS);
            _testSentinelByCaller_demo2.setCount(2);
            _testSentinelByCaller_demo2.setStrategy(RuleConstant.STRATEGY_CHAIN);
            rules.add(_testSentinelByCaller_demo2);
        }

        FlowRuleManager.loadRules(rules);
    }
}
