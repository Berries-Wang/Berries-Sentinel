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
 * <p>
 * The principle idea comes from Guava. However, the calculation of Guava is rate-based, which means that we need to
 * translate rate to QPS.(核心思想来自 Guava。然而，Guava 的计算是基于速率的，这意味着我们需要将速率转换为 QPS)
 * <blockquote>
 *     怎么转换的呢?
 * </blockquote>
 * </p>
 *
 * <p>
 * Requests arriving at the pulse may drag down long idle systems even though it has a much larger handling capability
 * in stable period. It usually happens in scenarios that require extra time for initialization, e.g. DB establishes a
 * connection, connects to a remote service, and so on. That’s why we need “warm up”.
 * (在脉冲时刻到达的请求可能会拖垮长时间闲置的系统，尽管它在稳定期具有更大的处理能力。
 * 这种情况通常发生在需要额外时间进行初始化的场景中，例如数据库建立连接、连接到远程服务等。这就是为什么我们需要 '预热'。)
 * <blockquote>
 *     类似于抽奖场景
 * </blockquote>
 * </p>
 *
 * <p>
 * Sentinel's "warm-up" implementation is based on the Guava's algorithm. However, Guava’s implementation focuses on
 * adjusting the request interval, which is similar to leaky bucket. Sentinel pays more attention to controlling the
 * count of incoming requests per second without calculating its interval, which resembles token bucket algorithm.
 * (Sentinel 的 '预热' 实现基于 Guava 的算法。
 * 然而，Guava 的实现侧重于调整请求间隔，这类似于漏桶算法。Sentinel 更注重控制每秒的请求数量，而不计算其间隔，这类似于令牌桶算法。)
 * </p>
 *
 * <p>
 * The remaining tokens in the bucket is used to measure the system utility. Suppose a system can handle b requests per
 * second. Every second b tokens will be added into the bucket until the bucket is full. And when system processes a
 * request, it takes a token from the bucket. The more tokens left in the bucket, the lower the utilization of the
 * system; when the token in the token bucket is above a certain threshold, we call it in a "saturation" state.
 * (桶中剩余的令牌用于衡量系统的利用率。假设一个系统每秒可以处理 b 个请求。每秒会有 b 个令牌被添加到桶中，直到桶满。
 * 当系统处理一个请求时，它会从桶中取出一个令牌。桶中剩余的令牌越多，系统的利用率越低；当令牌桶中的令牌超过某个阈值时，我们称其处于 '饱和' 状态。)
 * </p>
 *
 * <p>
 * Base on Guava’s theory, there is a linear equation we can write this in the form y = m * x + b where y (a.k.a y(x)),
 * or qps(q)), is our expected QPS given a saturated period (e.g. 3 minutes in), m is the rate of change from our cold
 * (minimum) rate to our stable (maximum) rate, x (or q) is the occupied token.(基于 Guava 的理论，
 * 我们可以将其写成一个线性方程 y = m * x + b，
 * 其中 y（也称为 y(x) 或 qps(q)）是在饱和期（例如 3 分钟后）我们预期的 QPS，m 是从冷启动（最小）速率到稳定（最大）速率的变化率，x（或 q）是占用的令牌数。)
 * </p>
 *
 * @author jialiang.linjl
 */
public class WarmUpController implements TrafficShapingController {
    /**
     * <p> ## 来自: guava: SmoothRateLimiter.java </p>
     * <p>How is the RateLimiter designed, and why?</p>
     *
     * <p>The primary feature of a RateLimiter is its "stable rate", the maximum rate that it should
     * allow in normal conditions. This is enforced by "throttling" incoming requests as needed. For
     * example, we could compute the appropriate throttle time for an incoming request, and make the
     * calling thread wait for that time.(RateLimiter 的主要特性是它的 '稳定速率'，即在正常情况下允许的最大速率。
     * 这是通过根据需要 '限流' 传入的请求来实现的。例如，我们可以计算传入请求的适当限流时间，并让调用线程等待该时间。)</p>
     *
     * <p>The simplest way to maintain a rate of QPS is to keep the timestamp of the last granted
     * request, and ensure that (1/QPS) seconds have elapsed since then. For example, for a rate of
     * QPS=5 (5 tokens per second), if we ensure that a request isn't granted earlier than 200ms after
     * the last one, then we achieve the intended rate. If a request comes and the last request was
     * granted only 100ms ago, then we wait for another 100ms. At this rate, serving 15 fresh permits
     * (i.e. for an acquire(15) request) naturally takes 3 seconds.(维持 QPS 速率的最简单方法是记录最后一个被允许请求的时间戳，
     * 并确保自那时起已经过了 (1/QPS) 秒。例如，对于 QPS=5 的速率（每秒 5 个令牌），
     * 如果我们确保一个请求不会在上一个请求之后 200 毫秒内被允许，那么我们就达到了预期的速率。
     * 如果一个请求到达时，上一个请求仅在 100 毫秒前被允许，那么我们会再等待 100 毫秒。按照这个速率，
     * 提供 15 个新的许可（即对于 acquire(15) 请求）自然需要 3 秒钟。)</p>
     *
     * <p>It is important to realize that such a RateLimiter has a very superficial memory of the past:
     * it only remembers the last request. What if the RateLimiter was unused for a long period of
     * time, then a request arrived and was immediately granted? This RateLimiter would immediately
     * forget about that past underutilization. This may result in either underutilization or
     * overflow, depending on the real world consequences of not using the expected rate.(重要的是要认识到，这样的 RateLimiter 对过去的记忆非常浅：
     * 它只记得最后一个请求。如果 RateLimiter 长时间未被使用，然后一个请求到达并立即被允许，会发生什么？
     * 这个 RateLimiter 会立即忘记过去的未充分利用。这可能导致未充分利用或溢出，具体取决于未使用预期速率的实际后果。)</p>
     *
     * <p>Past underutilization could mean that excess resources are available. Then, the RateLimiter
     * should speed up for a while, to take advantage of these resources. This is important when the
     * rate is applied to networking (limiting bandwidth), where past underutilization typically
     * translates to "almost empty buffers", which can be filled immediately.(过去的未充分利用可能意味着有额外的资源可用。
     * 因此，RateLimiter 应该暂时加速，以利用这些资源。
     * 这在速率应用于网络（限制带宽）时尤为重要，因为过去的未充分利用通常意味着 '几乎空的缓冲区'，这些缓冲区可以立即被填满。)</p>
     *
     * <p>On the other hand, past underutilization could mean that "the server responsible for handling
     * the request has become less ready for future requests", i.e. its caches become stale, and
     * requests become more likely to trigger expensive operations (a more extreme case of this
     * example is when a server has just booted, and it is mostly busy with getting itself up to
     * speed).(另一方面，过去的未充分利用可能意味着 '负责处理请求的服务器对未来请求的准备程度降低'，
     * 即其缓存变得过时，请求更有可能触发昂贵的操作（这种情况的一个更极端的例子是服务器刚刚启动，大部分时间都忙于使自己达到正常速度）。)</p>
     *
     * <p>To deal with such scenarios, we add an extra dimension, that of "past underutilization",
     * modeled by "storedPermits" variable. This variable is zero when there is no underutilization,
     * and it can grow up to maxStoredPermits, for sufficiently large underutilization. So, the
     * requested permits, by an invocation acquire(permits), are served from:(为了处理这种情况，
     * 我们增加了一个额外的维度，即 '过去的未充分利用'，通过 'storedPermits' 变量来建模。当没有未充分利用时，该变量为零；
     * 在未充分利用足够大时，它可以增长到 maxStoredPermits。因此，通过调用 acquire(permits) 请求的许可将从以下来源提供：)</p>
     *
     * <p> - stored permits (if available)</p>
     *
     * <p> - fresh permits (for any remaining permits)</p>
     *
     * <p> How this works is best explained with an example:</p>
     *
     * <p> For a RateLimiter that produces 1 token per second, every second that goes by with the
     * RateLimiter being unused, we increase storedPermits by 1. Say we leave the RateLimiter unused
     * for 10 seconds (i.e., we expected a request at time X, but we are at time X + 10 seconds before
     * a request actually arrives; this is also related to the point made in the last paragraph), thus
     * storedPermits becomes 10.0 (assuming maxStoredPermits >= 10.0). At that point, a request of
     * acquire(3) arrives. We serve this request out of storedPermits, and reduce that to 7.0 (how
     * this is translated to throttling time is discussed later). Immediately after, assume that an
     * acquire(10) request arriving. We serve the request partly from storedPermits, using all the
     * remaining 7.0 permits, and the remaining 3.0, we serve them by fresh permits produced by the
     * rate limiter.(对于一个每秒生成 1 个令牌的 RateLimiter，每经过一秒且 RateLimiter 未被使用，我们将 storedPermits 增加 1。
     * 假设我们将 RateLimiter 闲置了 10 秒（即我们预期在时间 X 有一个请求，
     * 但在请求实际到达之前已经到了时间 X + 10 秒；这也与上一段提到的观点相关），
     * 因此 storedPermits 变为 10.0（假设 maxStoredPermits >= 10.0）。此时，一个 acquire(3) 请求到达。
     * 我们从 storedPermits 中提供这个请求，并将其减少到 7.0（如何将其转换为限流时间将在后面讨论）。
     * 紧接着，假设一个 acquire(10) 请求到达。
     * 我们部分从 storedPermits 中提供该请求，使用所有剩余的 7.0 个许可，剩余的 3.0 个许可则由 RateLimiter 新生成的许可提供。)</p>
     *
     * <p>We already know how much time it takes to serve 3 fresh permits: if the rate is
     * "1 token per second", then this will take 3 seconds. But what does it mean to serve 7 stored
     * permits? As explained above, there is no unique answer. If we are primarily interested to deal
     * with underutilization, then we want stored permits to be given out /faster/ than fresh ones,
     * because underutilization = free resources for the taking. If we are primarily interested to
     * deal with overflow, then stored permits could be given out /slower/ than fresh ones. Thus, we
     * require a (different in each case) function that translates storedPermits to throttling time.
     * (我们已经知道提供 3 个新许可需要多少时间：如果速率是 '每秒 1 个令牌'，那么这将需要 3 秒。
     * 但是，提供 7 个存储的许可意味着什么？如上所述，没有唯一的答案。
     * 如果我们主要关注处理未充分利用的情况，那么我们希望存储的许可比新许可分发得更快，因为未充分利用 = 可用的免费资源。
     * 如果我们主要关注处理溢出的情况，那么存储的许可可能比新许可分发得更慢。
     * 因此，我们需要一个（在不同情况下不同的）函数，将 storedPermits 转换为限流时间。)</p>
     *
     * <p>This role is played by storedPermitsToWaitTime(double storedPermits, double permitsToTake). The
     * underlying model is a continuous function mapping storedPermits (from 0.0 to maxStoredPermits)
     * onto the 1/rate (i.e. intervals) that is effective at the given storedPermits. "storedPermits"
     * essentially measure unused time; we spend unused time buying/storing permits. Rate is
     * "permits / time", thus "1 / rate = time / permits". Thus, "1/rate" (time / permits) times
     * "permits" gives time, i.e., integrals on this function (which is what storedPermitsToWaitTime()
     * computes) correspond to minimum intervals between subsequent requests, for the specified number
     * of requested permits.(这个角色由 storedPermitsToWaitTime(double storedPermits, double permitsToTake) 扮演。
     * 底层模型是一个连续函数，将 storedPermits（从 0.0 到 maxStoredPermits）映射到在给定 storedPermits 下有效的 1/rate（即间隔）。
     * 'storedPermits' 本质上是衡量未使用的时间；我们通过未使用的时间来购买/存储许可。
     * 速率是 'permits / time'，因此 "1 / rate = time / permits"。因此， "1/rate" (time / permits）乘以 'permits' 得到时间，
     * 即该函数的积分（即 storedPermitsToWaitTime() 计算的内容）对应于指定数量的请求许可之间的最小间隔。)</p>
     *
     * <p>Here is an example of storedPermitsToWaitTime: If storedPermits == 10.0, and we want 3 permits,
     * we take them from storedPermits, reducing them to 7.0, and compute the throttling for these as
     * a call to storedPermitsToWaitTime(storedPermits = 10.0, permitsToTake = 3.0), which will
     * evaluate the integral of the function from 7.0 to 10.0.(以下是 storedPermitsToWaitTime 的一个示例：
     * 如果 storedPermits == 10.0，并且我们想要 3 个许可，我们从 storedPermits 中取出它们，将其减少到 7.0，
     * 并通过调用 storedPermitsToWaitTime(storedPermits = 10.0, permitsToTake = 3.0) 来计算这些许可的限流时间，
     * 该函数将计算从 7.0 到 10.0 的函数积分。)</p>
     *
     * <p>Using integrals guarantees that the effect of a single acquire(3) is equivalent to {
     * acquire(1); acquire(1); acquire(1); }, or { acquire(2); acquire(1); }, etc, since the integral
     * of the function in [7.0, 10.0] is equivalent to the sum of the integrals of [7.0, 8.0], [8.0,
     * 9.0], [9.0, 10.0] (and so on), no matter what the function is. This guarantees that we handle
     * correctly requests of varying weight (permits), /no matter/ what the actual function is - so we
     * can tweak the latter freely. (The only requirement, obviously, is that we can compute its
     * integrals).(使用积分可以保证，单个 acquire(3) 的效果等同于 {acquire(1); acquire(1); acquire(1); }，
     * 或者 {acquire(2); acquire(1); } 等，
     * 因为函数在 [7.0, 10.0] 区间内的积分等同于 [7.0, 8.0]、[8.0, 9.0]、[9.0, 10.0]（以此类推）区间内积分的总和，无论函数是什么。
     * 这保证了我们能够正确处理不同权重（许可数）的请求，无论实际函数是什么——因此我们可以自由调整后者。
     * （显然，唯一的要求是我们能够计算其积分）。)</p>
     *
     * <p> Note well that if, for this function, we chose a horizontal line, at height of exactly (1/QPS),
     * then the effect of the function is non-existent: we serve storedPermits at exactly the same
     * cost as fresh ones (1/QPS is the cost for each). We use this trick later.
     * (请注意，如果对于这个函数，我们选择一条水平线，其高度恰好为 (1/QPS)，那么该函数的效果将不存在：
     * 我们以与新许可完全相同的成本提供存储的许可（每个许可的成本为 1/QPS）。我们稍后会使用这个技巧。)</p>
     *
     * <p> If we pick a function that goes /below/ that horizontal line, it means that we reduce the area
     * of the function, thus time. Thus, the RateLimiter becomes /faster/ after a period of
     * underutilization. If, on the other hand, we pick a function that goes /above/ that horizontal
     * line, then it means that the area (time) is increased, thus storedPermits are more costly than
     * fresh permits, thus the RateLimiter becomes /slower/ after a period of underutilization.
     * (如果我们选择一个低于该水平线的函数，这意味着我们减少了函数的面积，从而减少了时间。
     * 因此，RateLimiter 在一段时间的未充分利用后会变得更快。
     * 另一方面，如果我们选择一个高于该水平线的函数，那么这意味着面积（时间）增加了，
     * 因此存储的许可比新许可更昂贵，因此 RateLimiter 在一段时间的未充分利用后会变得更慢。)</p>
     *
     * <p> Last, but not least: consider a RateLimiter with rate of 1 permit per second, currently
     * completely unused, and an expensive acquire(100) request comes. It would be nonsensical to just
     * wait for 100 seconds, and /then/ start the actual task. Why wait without doing anything? A much
     * better approach is to /allow/ the request right away (as if it was an acquire(1) request
     * instead), and postpone /subsequent/ requests as needed. In this version, we allow starting the
     * task immediately, and postpone by 100 seconds future requests, thus we allow for work to get
     * done in the meantime instead of waiting idly.(最后但同样重要的是：
     * 考虑一个速率为每秒 1 个许可的 RateLimiter，当前完全未被使用，然后一个昂贵的 acquire(100) 请求到来。
     * 仅仅等待 100 秒然后才开始实际任务是毫无意义的。为什么要无所事事地等待？
     * 更好的方法是立即允许该请求（就像它是一个 acquire(1) 请求一样），并根据需要推迟后续请求。
     * 在这个版本中，我们允许立即开始任务，并将未来的请求推迟 100 秒，从而允许在此期间完成工作，而不是闲置等待。)</p>
     *
     * <p>This has important consequences: it means that the RateLimiter doesn't remember the time of the
     * _last_ request, but it remembers the (expected) time of the _next_ request. This also enables
     * us to tell immediately (see tryAcquire(timeout)) whether a particular timeout is enough to get
     * us to the point of the next scheduling time, since we always maintain that. And what we mean by
     * "an unused RateLimiter" is also defined by that notion: when we observe that the
     * "expected arrival time of the next request" is actually in the past, then the difference (now -
     * past) is the amount of time that the RateLimiter was formally unused, and it is that amount of
     * time which we translate to storedPermits. (We increase storedPermits with the amount of permits
     * that would have been produced in that idle time). So, if rate == 1 permit per second, and
     * arrivals come exactly one second after the previous, then storedPermits is _never_ increased --
     * we would only increase it for arrivals _later_ than the expected one second.(这有重要的后果：
     * 这意味着 RateLimiter 不会记住 上一次 请求的时间，而是记住 下一次 请求的（预期）时间。
     * 这也使我们能够立即判断（参见 tryAcquire(timeout)）某个特定的超时是否足以让我们达到下一个调度时间点，
     * 因为我们始终维护这一点。我们所说的 '未使用的 RateLimiter' 也是由这个概念定义的：
     * 当我们观察到 '下一次请求的预期到达时间' 实际上在过去时，那么差值（现在 - 过去）就是 RateLimiter 正式未使用的时间量，
     * 我们将这个时间量转换为 storedPermits。（我们根据在该空闲时间内本应生成的许可数量来增加 storedPermits）。
     * 因此，如果速率 == 每秒 1 个许可，并且请求在前一个请求之后恰好一秒到达，
     * 那么 storedPermits 永远不会增加——我们只会在请求到达时间晚于预期的一秒时增加它。)</p>
     *
     * <p> This implements the following function where coldInterval = coldFactor * stableInterval.</p>
     *
     * <pre>
     *          ^ throttling(限流)
     *          |
     *    cold  +                  /
     * interval |                 /.
     *          |                / .
     *          |               /  .   ← "warmup period" is the area of the trapezoid
     *          |              /   .     between thresholdPermits and maxPermits
     *          |             /    .      (预热期是 thresholdPermits 和 maxPermits
     *          |            /     .        之间梯形的面积。)
     *          |           /      .
     *   stable +----------/  WARM .
     * interval |          .   UP  .
     *          |          . PERIOD.
     *          |          .       .
     *        0 +----------+-------+--------------→ storedPermits(累积的令牌)
     *          0 thresholdPermits maxPermits
     * </pre>
     *<ol>
     *     <li>thresholdPermits: </li>
     *</ol>
     * <p>Before going into the details of this particular function, let's keep in mind the basics:(在深入探讨这个特定函数的细节之前，让我们先记住一些基础知识：)</p>
     *
     * <ol>
     *   <li>The state of the RateLimiter (storedPermits) is a vertical line in this figure.(RateLimiter（storedPermits）的状态在这个图中是一条垂直线。)
     *   <li>When the RateLimiter is not used, this goes right (up to maxPermits)(当不使用 RateLimiter 时，它会向右移动（直到 maxPermits）)
     *   <li>When the RateLimiter is used, this goes left (down to zero), since if we have
     *       storedPermits, we serve from those first (当使用速率限制器时，它会向左移动（降至零），因为如果我们存储了许可证，我们会从最先存储的许可证开始提供服务)
     *   <li>When _unused_, we go right at a constant rate! The rate at which we move to the right is
     *       chosen as maxPermits / warmupPeriod. This ensures that the time it takes to go from 0 to
     *       maxPermits is equal to warmupPeriod.(当 未使用 时，我们以恒定速率向右移动！向右移动的速率选择为 maxPermits / warmupPeriod。这确保了从 0 到 maxPermits 所需的时间等于 warmupPeriod。)
     *   <li> When _used_, the time it takes, as explained in the introductory class note, is equal to
     *       the integral of our function, between X permits and X-K permits, assuming we want to
     *       spend K saved permits.（当 使用 时，所需的时间(预热时间???)（如入门课程笔记中所述）等于我们函数在 X 许可和 X-K 许可之间的积分，假设我们要消耗 K 个已存储的许可。）
     * </ol>
     *
     * <p>In summary, the time it takes to move to the left (spend K permits), is equal to the area of
     * the function of width == K. (总结来说，向左移动（消耗 K 个许可）所需的时间等于宽度为 K 的函数的面积。)</p>
     *
     * <p>Assuming we have saturated demand, the time to go from maxPermits to thresholdPermits is
     * equal to warmupPeriod. And the time to go from thresholdPermits to 0 is warmupPeriod/2. (The
     * reason that this is warmupPeriod/2 is to maintain the behavior of the original implementation
     * where coldFactor was hard coded as 3.)(假设我们有饱和需求，从 maxPermits 到 thresholdPermits 的时间等于 warmupPeriod。
     * 而从 thresholdPermits 到 0 的时间是 warmupPeriod/2。
     * （之所以是 warmupPeriod/2，是为了保持原始实现的行为，其中 coldFactor 被硬编码为 3。）)</p>
     *
     * <p>It remains to calculate thresholdsPermits and maxPermits.(接下来需要计算 thresholdPermits 和 maxPermits)</p>
     *
     * <ul>
     *   <li>The time to go from thresholdPermits to 0 is equal to the integral of the function
     *       between 0 and thresholdPermits. This is thresholdPermits * stableIntervals. By (5) it is
     *       also equal to warmupPeriod/2. Therefore (从 thresholdPermits 到 0 的时间等于函数在 0 到 thresholdPermits 之间的积分。
     *       这是 thresholdPermits * stableIntervals。根据 (5)，它也等于 warmupPeriod/2。因此)
     *       <blockquote>
     *       thresholdPermits = 0.5 * warmupPeriod / stableInterval
     *       </blockquote>
     *   <li>The time to go from maxPermits to thresholdPermits is equal to the integral of the
     *       function between thresholdPermits and maxPermits. This is the area of the pictured
     *       trapezoid, and it is equal to 0.5 * (stableInterval + coldInterval) * (maxPermits -
     *       thresholdPermits). It is also equal to warmupPeriod, so
     *       (从 maxPermits 到 thresholdPermits 的时间等于函数在 thresholdPermits 到 maxPermits 之间的积分。
     *       这是图中梯形的面积，等于 0.5 * (stableInterval + coldInterval) * (maxPermits - thresholdPermits)
     *       。它也等于 warmupPeriod，因此)
     *       <blockquote>
     *       maxPermits = thresholdPermits + 2 * warmupPeriod / (stableInterval + coldInterval)
     *       </blockquote>
     * </ul>
     */
    protected double count;
    protected int warningToken = 0;
    // The slope(斜率) of the line from the stable interval (when permits == 0), to the cold interval (when permits == maxPermits)
    protected double slope;
    protected AtomicLong storedTokens = new AtomicLong(0);
    protected AtomicLong lastFilledTime = new AtomicLong(0);
    private int coldFactor;
    private int maxToken;

    public WarmUpController(double count, int warmUpPeriodInSec, int coldFactor) {
        construct(count, warmUpPeriodInSec, coldFactor);
    }

    public WarmUpController(double count, int warmUpPeriodInSec) {
        construct(count, warmUpPeriodInSec, 3);
    }

    private void construct(double count, int warmUpPeriodInSec, int coldFactor) {

        if (coldFactor <= 1) {
            throw new IllegalArgumentException("Cold factor should be larger than 1");
        }

        this.count = count;

        this.coldFactor = coldFactor;

        // thresholdPermits = 0.5 * warmupPeriod / stableInterval.
        // warningToken = 100;
        warningToken = (int)(warmUpPeriodInSec * count) / (coldFactor - 1);
        // / maxPermits = thresholdPermits + 2 * warmupPeriod /
        // (stableInterval + coldInterval)
        // maxToken = 200
        maxToken = warningToken + (int)(2 * warmUpPeriodInSec * count / (1.0 + coldFactor));

        // slope = (coldIntervalMicros - stableIntervalMicros) / (maxPermits - thresholdPermits);
        slope = (coldFactor - 1.0) / count / (maxToken - warningToken);

    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        long passQps = (long)node.passQps();

        long previousQps = (long)node.previousPassQps();
        syncToken(previousQps);

        // 开始计算它的斜率: 如果进入了警戒线，开始调整他的qps
        long restToken = storedTokens.get();
        if (restToken >= warningToken) {
            long aboveToken = restToken - warningToken;
            // 消耗的速度要比warning快，但是要比慢:  current interval = restToken*slope+1/count
            double warningQps = Math.nextUp(1.0 / (aboveToken * slope + 1.0 / count));
            if (passQps + acquireCount <= warningQps) {
                return true;
            }
        } else {
            if (passQps + acquireCount <= count) {
                return true;
            }
        }

        return false;
    }

    protected void syncToken(long passQps) {
        long currentTime = TimeUtil.currentTimeMillis();
        currentTime = currentTime - currentTime % 1000;
        long oldLastFillTime = lastFilledTime.get();
        if (currentTime <= oldLastFillTime) {
            return;
        }

        long oldValue = storedTokens.get();
        long newValue = coolDownTokens(currentTime, passQps);

        if (storedTokens.compareAndSet(oldValue, newValue)) {
            long currentValue = storedTokens.addAndGet(0 - passQps);
            if (currentValue < 0) {
                storedTokens.set(0L);
            }
            lastFilledTime.set(currentTime);
        }

    }

    private long coolDownTokens(long currentTime, long passQps) {
        long oldValue = storedTokens.get();
        long newValue = oldValue;

        // 添加令牌的判断前提条件: 当令牌的消耗程度远远低于警戒线的时候
        if (oldValue < warningToken) {
            newValue = (long)(oldValue + (currentTime - lastFilledTime.get()) * count / 1000);
        } else if (oldValue > warningToken) {
            if (passQps < (int)count / coldFactor) {
                newValue = (long)(oldValue + (currentTime - lastFilledTime.get()) * count / 1000);
            }
        }
        return Math.min(newValue, maxToken);
    }

}
