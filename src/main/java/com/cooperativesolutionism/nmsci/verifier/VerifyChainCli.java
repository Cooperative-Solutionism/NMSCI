package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 独立链验证 CLI：第三方仅凭落盘的 {@code blk*.dat}（以及可选的源码包/源码哈希/中心公钥/起始锚点），
 * 在不运行本服务、不接触数据库的前提下离线核验整条链。退出码 0=通过，1=不通过，2=用法错误。
 *
 * <pre>
 * 用法: VerifyChainCli [datDir] [选项]
 *   datDir                     区块目录（含 blkNNNNNNNN.dat），默认 ./file/dat
 *   --central-pubkey &lt;v&gt;        期望中心压缩公钥（66位hex 或 base64），逐块比对
 *   --source-zip &lt;path&gt;         源码包路径，取其单次 SHA-256 作为期望源码哈希逐块比对
 *   --source-hash &lt;hex&gt;         直接给定期望源码哈希（64位hex），与 --source-zip 二选一
 *   --starting-prev-hash &lt;hex&gt;  首块应衔接的前区块 id（64位hex 锚点）
 *   --no-stateful              关闭有状态回放（默认开启）
 *   --help                     打印帮助
 * </pre>
 */
public final class VerifyChainCli {

    private VerifyChainCli() {
    }

    public static void main(String[] args) {
        try {
            Options parsed = parse(args);
            if (parsed.help) {
                System.out.println(usage());
                return;
            }

            ChainVerificationResult result = run(parsed);
            System.out.println(result.render());
            System.exit(result.ok() ? 0 : 1);
        } catch (UsageException e) {
            System.err.println("用法错误: " + e.getMessage());
            System.err.println();
            System.err.println(usage());
            System.exit(2);
        }
    }

    static ChainVerificationResult run(Options parsed) {
        VerifierOptions.Builder optionsBuilder = VerifierOptions.builder()
                .includeStatefulReplay(parsed.stateful);
        if (parsed.expectedCentralPubkey != null) {
            optionsBuilder.expectedCentralPubkey(parsed.expectedCentralPubkey);
        }
        if (parsed.expectedSourceHashHex != null) {
            optionsBuilder.expectedSourceHashHex(parsed.expectedSourceHashHex);
        }
        if (parsed.startingPreviousHash != null) {
            optionsBuilder.startingPreviousHash(parsed.startingPreviousHash);
        }

        try {
            List<ParsedBlock> blocks = DatBlockReader.readDirectory(parsed.datDir);
            return new ChainVerifier().verify(blocks, optionsBuilder.build());
        } catch (BlockFormatException e) {
            return ChainVerificationResult.parseFailure(e.getMessage());
        }
    }

    static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> options.help = true;
                case "--no-stateful" -> options.stateful = false;
                case "--central-pubkey" -> options.expectedCentralPubkey = parsePubkey(requireValue(args, ++i, arg));
                case "--source-hash" -> options.expectedSourceHashHex = requireHex(requireValue(args, ++i, arg), 64, arg);
                case "--source-zip" -> options.expectedSourceHashHex = sourceZipHash(requireValue(args, ++i, arg));
                case "--starting-prev-hash" ->
                        options.startingPreviousHash = ByteArrayUtil.hexToBytes(requireHex(requireValue(args, ++i, arg), 64, arg));
                default -> {
                    if (arg.startsWith("-")) {
                        throw new UsageException("未知选项: " + arg);
                    }
                    if (options.datDirSet) {
                        throw new UsageException("多余的位置参数: " + arg);
                    }
                    options.datDir = Path.of(arg);
                    options.datDirSet = true;
                }
            }
        }
        return options;
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new UsageException("选项 " + flag + " 缺少参数值");
        }
        return args[index];
    }

    private static byte[] parsePubkey(String value) {
        if (value.length() == 66 && value.chars().allMatch(VerifyChainCli::isHexChar)) {
            return ByteArrayUtil.hexToBytes(value);
        }
        try {
            return ByteArrayUtil.base64ToBytes(value);
        } catch (IllegalArgumentException e) {
            throw new UsageException("--central-pubkey 既非66位hex也非合法base64: " + value);
        }
    }

    private static String requireHex(String value, int expectedLength, String flag) {
        if (value.length() != expectedLength || !value.chars().allMatch(VerifyChainCli::isHexChar)) {
            throw new UsageException(flag + " 需为 " + expectedLength + " 位十六进制字符串");
        }
        return value.toLowerCase();
    }

    private static String sourceZipHash(String path) {
        Path zipPath = Path.of(path);
        if (!Files.isRegularFile(zipPath)) {
            throw new UsageException("--source-zip 文件不存在: " + path);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(zipPath);
        } catch (IOException e) {
            throw new UncheckedIOException("读取源码包失败: " + path, e);
        }
        return ByteArrayUtil.bytesToHex(Sha256Util.digest(bytes));
    }

    private static boolean isHexChar(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static String usage() {
        return """
                用法: VerifyChainCli [datDir] [选项]
                  datDir                     区块目录（含 blkNNNNNNNN.dat），默认 ./file/dat
                  --central-pubkey <v>       期望中心压缩公钥（66位hex 或 base64），逐块比对
                  --source-zip <path>        源码包路径，取其单次 SHA-256 作为期望源码哈希逐块比对
                  --source-hash <hex>        期望源码哈希（64位hex），与 --source-zip 二选一
                  --starting-prev-hash <hex> 首块应衔接的前区块 id（64位hex 锚点）
                  --no-stateful              关闭有状态回放（默认开启）
                  --help                     打印帮助
                退出码: 0=通过, 1=不通过, 2=用法错误""";
    }

    /** 解析后的命令行选项（包级可见以便测试）。 */
    static final class Options {
        Path datDir = Path.of("file", "dat");
        boolean datDirSet;
        byte[] expectedCentralPubkey;
        String expectedSourceHashHex;
        byte[] startingPreviousHash;
        boolean stateful = true;
        boolean help;
    }

    private static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }
}
