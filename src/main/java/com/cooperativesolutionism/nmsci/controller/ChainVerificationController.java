package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.dto.ChainVerificationSummaryDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.verifier.BlockFormatException;
import com.cooperativesolutionism.nmsci.verifier.ChainVerificationResult;
import com.cooperativesolutionism.nmsci.verifier.ChainVerifier;
import com.cooperativesolutionism.nmsci.verifier.DatBlockReader;
import com.cooperativesolutionism.nmsci.verifier.ParsedBlock;
import com.cooperativesolutionism.nmsci.verifier.VerifierOptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 只读链完整性自检端点：对本节点落盘的 {@code blk*.dat} 重新解析并核验（帧格式、哈希链衔接、各区块中心签名、
 * 默克尔根、每条消息的 PoW/成员签名/中心签名，以及可选的有状态回放）。
 *
 * <p>每个区块的中心签名一律在其自身区块头公钥下验证，因此对中心公钥轮换/版本升级后的多代链仍稳健；
 * 故本端点不强制「区块头中心公钥等于本节点配置」与源码哈希一致——这类带信任锚的更严格独立核验请用离线 CLI
 * {@link com.cooperativesolutionism.nmsci.verifier.VerifyChainCli}。响应附带本节点配置的中心公钥与源码哈希供运维参照。
 *
 * <p>该端点对全部 {@code .dat} 做完整解析+逐块验证+有状态回放，开销随链长线性增长。为避免成为无鉴权的 CPU/IO 放大
 * 入口，结果按「目录指纹 + stateful」缓存并以单航道串行化（见 {@link ChainVerificationCache}）：链在出块间隔内不变时
 * 直接复用上次结果，并发请求合并到同一次计算。网络层鉴权/限流仍建议由反向代理/防火墙在部署层承担。
 */
@RestController
@RequestMapping("/verify")
public class ChainVerificationController {

    private final NmsciProperties nmsciProperties;
    private final ChainVerifier chainVerifier = new ChainVerifier();
    private final ChainVerificationCache verificationCache = new ChainVerificationCache();

    public ChainVerificationController(NmsciProperties nmsciProperties) {
        this.nmsciProperties = nmsciProperties;
    }

    @GetMapping("/chain")
    public ResponseResult<ChainVerificationSummaryDTO> verifyChain(
            @RequestParam(name = "stateful", required = false, defaultValue = "true") boolean stateful
    ) {
        Path datDir = Path.of(System.getProperty("user.dir"))
                .resolve(nmsciProperties.getFileRootDir())
                .resolve(nmsciProperties.getFileDatDir());

        ChainVerificationResult result = verificationCache.get(
                fingerprint(datDir),
                stateful,
                () -> computeVerification(datDir, stateful));

        String configuredCentralPubkeyHex =
                ByteArrayUtil.bytesToHex(ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPubkeyBase64()));

        return ResponseResult.success(ChainVerificationSummaryDTO.from(
                result,
                datDir.toString(),
                stateful,
                configuredCentralPubkeyHex,
                nmsciProperties.getSourceCodeZipHash()
        ));
    }

    private ChainVerificationResult computeVerification(Path datDir, boolean stateful) {
        VerifierOptions options = VerifierOptions.builder()
                .includeStatefulReplay(stateful)
                .build();
        try {
            List<ParsedBlock> blocks = DatBlockReader.readDirectory(datDir);
            return chainVerifier.verify(blocks, options);
        } catch (BlockFormatException | UncheckedIOException e) {
            // 结构性解析失败或读取 .dat 的 IO 错误，均作为结构性失败回传，避免逃逸为通用 500
            return ChainVerificationResult.parseFailure(e.getMessage());
        }
    }

    /**
     * 计算 dat 目录的轻量指纹（blk*.dat 的「文件名:大小:修改时间」拼接），远比完整解析+回放便宜。
     * 目录不存在返回稳定标记 "absent"（空链结果可缓存复用）；列目录 IO 失败返回 {@code null}（本次不缓存）。
     *
     * <p>正确性锚点是「大小」：{@code appendBlock} 以 APPEND 写入非空区块（每块至少一个区块头 229 字节），
     * 故每次追加都会使对应 .dat 文件大小严格增大，与文件系统 mtime 精度无关，保证「链一变指纹必变」；
     * mtime 仅作为极少见「原地改写」场景的额外不一致信号。
     */
    private static String fingerprint(Path datDir) {
        if (!Files.isDirectory(datDir)) {
            return "absent";
        }
        try (Stream<Path> paths = Files.list(datDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(ChainVerificationController::isDatFile)
                    .map(ChainVerificationController::describe)
                    .sorted()
                    .collect(Collectors.joining("|"));
        } catch (IOException | UncheckedIOException e) {
            return null;
        }
    }

    private static boolean isDatFile(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(BlockConstants.DAT_FILE_PREFIX) && name.endsWith(BlockConstants.DAT_FILE_SUFFIX);
    }

    private static String describe(Path path) {
        try {
            return path.getFileName() + ":" + Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
