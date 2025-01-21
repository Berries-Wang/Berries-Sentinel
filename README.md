# Berries-Sentinel
## 总体架构
在 Sentinel 里面，所有的资源都对应一个资源名称以及一个 Entry。Entry 可以通过对主流框架的适配自动创建，也可以通过注解的方式或调用 API 显式创建；每一个 Entry 创建的时候，同时也会创建一系列功能插槽（slot chain）。这些插槽有不同的职责，例如:

- NodeSelectorSlot 负责收集资源的路径，并将这些资源的调用路径，以树状结构存储起来，用于根据调用路径来限流降级；
- ClusterBuilderSlot 则用于存储资源的统计信息以及调用者信息，例如该资源的 RT, QPS, thread count 等等，这些信息将用作为多维度限流，降级的依据；
- StatisticSlot 则用于记录、统计不同纬度的 runtime 指标监控信息；
- FlowSlot 则用于根据预设的限流规则以及前面 slot 统计的状态，来进行流量控制；
- AuthoritySlot 则根据配置的黑白名单和调用来源信息，来做黑白名单控制；
- DegradeSlot 则通过统计信息以及预设的规则，来做熔断降级；
- SystemSlot 则通过系统的状态，例如 load1 等，来控制总的入口流量；

![Sentinel实现](./002.IMGS/sentinel-slot-chain-architecture.png)

Sentinel 将 ProcessorSlot 作为 SPI 接口进行扩展（1.7.2 版本以前 SlotChainBuilder 作为 SPI），使得 Slot Chain 具备了扩展的能力。您可以自行加入自定义的 slot 并编排 slot 间的顺序，从而可以给 Sentinel 添加自定义的功能。

![Slots拓展](./002.IMGS/46783631-93324d00-cd5d-11e8-8ad1-a802bcc8f9c9.png)


## 日志查阅
```log
    # 日志 ~/logs/csp/${appName}-metrics.log.xxx
    
    |--timestamp-|------date time----|--resource-|p |block|s |e|rt
    1529998904000|2018-06-26 15:41:44|hello world|20|0    |20|0|0
    1529998905000|2018-06-26 15:41:45|hello world|20|5579 |20|0|728
    1529998906000|2018-06-26 15:41:46|hello world|20|15698|20|0|0
    1529998907000|2018-06-26 15:41:47|hello world|20|19262|20|0|0
    1529998908000|2018-06-26 15:41:48|hello world|20|19502|20|0|0
    1529998909000|2018-06-26 15:41:49|hello world|20|18386|20|0|0

    # p 代表通过的请求, block 代表被阻止的请求, s 代表成功执行完成的请求个数, e 代表用户自定义的异常, rt 代表平均响应时长。
```


## 参考
1. [Sentinel Implementation](https://sentinelguard.io/zh-cn/docs/basic-implementation.html)
