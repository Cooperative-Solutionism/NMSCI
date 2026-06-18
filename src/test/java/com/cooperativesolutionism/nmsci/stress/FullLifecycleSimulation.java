package com.cooperativesolutionism.nmsci.stress;

import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPair;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.ByteArrayBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * NMSCI 全生命周期混合压测：每个虚拟用户依次走
 * flow-node-register -> central-pubkey-empower -> transaction-record -> transaction-mount 写链路，
 * 再叠加 consume-chains / returning-flow-rates / flow-nodes / transaction-records 读链路。
 *
 * <p>合法消息（secp256k1 签名 + PoW nonce）由测试夹具 {@link ProtocolMessageBuilder} 现造；
 * 中心密钥默认 {@link TestKeyPairs#CENTRAL}，被压服务必须以同一中心密钥 + trivial 难度（load profile）启动。
 *
 * <p>运行：先以 load profile 启动服务，再 {@code mvnw gatling:test}。
 * 参数（系统属性）：gatling.baseUrl / gatling.users / gatling.duration / gatling.amount。
 */
public class FullLifecycleSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("gatling.baseUrl", "http://localhost:8080");
    private static final int USERS = Integer.getInteger("gatling.users", 50);
    private static final int DURATION_S = Integer.getInteger("gatling.duration", 30);
    private static final long AMOUNT = Long.getLong("gatling.amount", 1200L);

    /** 必须与被压服务（load profile）配置的难度一致；trivial，PoW 1~2 次命中。 */
    private static final int NBITS = 0x20ffffff;

    /** 无状态、可被多线程共享。 */
    private static final ProtocolMessageBuilder BUILDER = new ProtocolMessageBuilder();
    private static final TestKeyPair CENTRAL = TestKeyPairs.CENTRAL;

    public FullLifecycleSimulation() {
        HttpProtocolBuilder httpProtocol = http
                .baseUrl(BASE_URL)
                .contentTypeHeader("application/octet-stream")
                .acceptHeader("application/json");

        // 每个虚拟用户分配一个递增索引，用于派生互不相同的密钥。
        AtomicInteger counter = new AtomicInteger();
        Iterator<Map<String, Object>> vuFeeder = Stream.generate(() -> {
            Map<String, Object> row = new HashMap<>();
            row.put("vuIndex", counter.getAndIncrement());
            return row;
        }).iterator();

        // 每用户初始化：生成 4 个 UUID 与 flow/consume 密钥，存入会话（密钥只派生一次）。
        ChainBuilder initSession = exec(session -> {
            int vu = session.getInt("vuIndex");
            TestKeyPair flowKey = TestKeyPairs.deriveByIndex(vu);
            TestKeyPair consumeKey = TestKeyPairs.deriveByIndex(1_000_000 + vu);
            return session
                    .set("flowNodeId", UUID.randomUUID())
                    .set("empowerId", UUID.randomUUID())
                    .set("recordId", UUID.randomUUID())
                    .set("mountId", UUID.randomUUID())
                    .set("flowKey", flowKey)
                    .set("consumeKey", consumeKey)
                    .set("flowPubkeyHex", ByteArrayUtil.bytesToHex(flowKey.pubkey()))
                    .set("consumePubkeyHex", ByteArrayUtil.bytesToHex(consumeKey.pubkey()));
        });

        // 写链路：消息体在 ByteArrayBody 内惰性构造（含 PoW 挖矿 + 签名），运行在 Gatling 工作线程上。
        ChainBuilder writeChain = exec(
                http("flow-node-register").post("/flow-node-registrations")
                        .body(ByteArrayBody(session -> BUILDER.flowNodeRegister(
                                (UUID) session.get("flowNodeId"),
                                (TestKeyPair) session.get("flowKey"),
                                NBITS)))
                        .check(status().is(201))
                        .check(jsonPath("$.code").is("200"))
        ).exec(
                http("central-pubkey-empower").post("/central-pubkey-empowerments")
                        .body(ByteArrayBody(session -> BUILDER.centralPubkeyEmpower(
                                (UUID) session.get("empowerId"),
                                (TestKeyPair) session.get("flowKey"),
                                CENTRAL)))
                        .check(status().is(201))
                        .check(jsonPath("$.code").is("200"))
        ).exec(
                http("transaction-record").post("/transaction-records")
                        .body(ByteArrayBody(session -> BUILDER.transactionRecord(
                                (UUID) session.get("recordId"),
                                AMOUNT,
                                (TestKeyPair) session.get("consumeKey"),
                                (TestKeyPair) session.get("flowKey"),
                                CENTRAL,
                                NBITS)))
                        .check(status().is(201))
                        .check(jsonPath("$.code").is("200"))
        ).exec(
                // mount 直接引用本会话生成的 recordId（实体 id = 协议消息内自带 UUID），无需解析响应。
                http("transaction-mount").post("/transaction-mounts")
                        .body(ByteArrayBody(session -> BUILDER.transactionMount(
                                (UUID) session.get("mountId"),
                                (UUID) session.get("recordId"),
                                (TestKeyPair) session.get("consumeKey"),
                                (TestKeyPair) session.get("flowKey"),
                                CENTRAL,
                                NBITS)))
                        .check(status().is(201))
                        .check(jsonPath("$.code").is("200"))
        );

        // 读链路：用前面会话产生的 pubkey 命中真实数据，覆盖最贵的聚合/连接查询。
        ChainBuilder readChain = exec(
                http("get-transaction-records").get("/transaction-records")
                        .queryParam("flowNodePubkey", "#{flowPubkeyHex}")
                        .queryParam("page", "0").queryParam("size", "20")
                        .check(status().is(200))
        ).exec(
                http("get-consume-chains").get("/consume-chains")
                        .queryParam("nodePubkey", "#{flowPubkeyHex}")
                        .queryParam("page", "0").queryParam("size", "20")
                        .check(status().is(200))
        ).exec(
                // 仅按 target 查询：source/target 都会被解析为「已注册的流转节点」，而消费节点从不注册，
                // 故携带 sourcePubkey 会 404；目标用已注册的流转节点 pubkey 即可命中聚合查询。
                http("get-returning-flow-rates").get("/returning-flow-rates")
                        .queryParam("targetPubkey", "#{flowPubkeyHex}")
                        .check(status().is(200))
        ).exec(
                http("get-flow-nodes").get("/flow-nodes")
                        .queryParam("page", "0").queryParam("size", "20")
                        .check(status().is(200))
        );

        ScenarioBuilder scn = scenario("NMSCI full lifecycle + reads")
                .feed(vuFeeder)
                .exec(initSession)
                .exec(writeChain)
                .pause(Duration.ofMillis(100))
                .exec(readChain);

        setUp(
                scn.injectOpen(rampUsers(USERS).during(Duration.ofSeconds(DURATION_S)))
        ).protocols(httpProtocol)
                .assertions(
                        // 唯一硬正确性门：任何 4xx/5xx 都判失败（如中心密钥/难度不匹配会立刻暴露）。
                        global().failedRequests().count().is(0L),
                        // 宽松参考阈值，可按环境调整。
                        global().responseTime().percentile(95).lt(2000)
                );
    }
}
