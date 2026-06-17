package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
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

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 只读链完整性自检端点：对本节点落盘的 {@code blk*.dat} 重新解析并核验（帧格式、哈希链衔接、各区块中心签名、
 * 默克尔根、每条消息的 PoW/成员签名/中心签名，以及可选的有状态回放）。
 *
 * <p>每个区块的中心签名一律在其自身区块头公钥下验证，因此对中心公钥轮换/版本升级后的多代链仍稳健；
 * 故本端点不强制「区块头中心公钥等于本节点配置」与源码哈希一致——这类带信任锚的更严格独立核验请用离线 CLI
 * {@link com.cooperativesolutionism.nmsci.verifier.VerifyChainCli}。响应附带本节点配置的中心公钥与源码哈希供运维参照。
 */
@RestController
@RequestMapping("/verify")
public class ChainVerificationController {

    private final NmsciProperties nmsciProperties;
    private final ChainVerifier chainVerifier = new ChainVerifier();

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

        VerifierOptions options = VerifierOptions.builder()
                .includeStatefulReplay(stateful)
                .build();

        ChainVerificationResult result;
        try {
            List<ParsedBlock> blocks = DatBlockReader.readDirectory(datDir);
            result = chainVerifier.verify(blocks, options);
        } catch (BlockFormatException | UncheckedIOException e) {
            // 结构性解析失败或读取 .dat 的 IO 错误，均作为结构性失败回传，避免逃逸为通用 500
            result = ChainVerificationResult.parseFailure(e.getMessage());
        }

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
}
