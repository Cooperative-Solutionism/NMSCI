package com.cooperativesolutionism.nmsci.tool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 升级前置只读检测 CLI：在部署新版本之前，连上数据库只读核验「旧版本已受理消息是否全部落块」
 * （待落块消息 {@code is_in_block=false} 计数为 0），用于版本割接前的安全过渡判定。
 *
 * <p>采用与 {@code VerifyChainCli} 一致的纯 {@code main()} 形态：<b>不启动 Spring 上下文、不触发 Flyway/JPA</b>，
 * 构造上即为只读（不会因启动而误跑迁移、也不会以新版本实体校验旧库 schema）。无 web 端口，跑完即退。
 *
 * <pre>
 * 用法: UpgradePreflightCli [选项]
 *   --db-url &lt;jdbc&gt;        数据库 JDBC URL，默认取环境变量 DB_URL
 *   --db-user &lt;user&gt;       数据库用户，默认取环境变量 DB_USERNAME
 *   --settle-seconds &lt;n&gt;   稳定窗秒数：等待后复查，确认待落块仍为 0 且无新消息进入（默认 0，仅单次快照）
 *   --help                打印帮助
 * 数据库密码只从环境变量 DB_PASSWORD 读取（不作为命令行参数，避免进程表泄露）。
 * 退出码: 0=GO（可割接）, 1=NO-GO, 2=用法/连接错误
 * </pre>
 *
 * <p>注意：本工具只判定「旧消息是否已全部落块」。入口冻结需运维在网络层完成（关闭/隔离对外端口或反代层拒写），
 * 否则应配合 {@code --settle-seconds} 用稳定窗排除竞态。难度/基线等其它升级前置见 README「区块版本升级与重部署流程」。
 */
public final class UpgradePreflightCli {

    private UpgradePreflightCli() {
    }

    public static void main(String[] args) {
        Options options;
        try {
            options = parse(args);
        } catch (UsageException e) {
            System.err.println("用法错误: " + e.getMessage());
            System.err.println();
            System.err.println(usage());
            System.exit(2);
            return;
        }
        if (options.help) {
            System.out.println(usage());
            return;
        }

        try {
            Result result = run(options);
            System.out.println(result.render());
            System.exit(result.go() ? 0 : 1);
        } catch (PreflightException e) {
            System.err.println("检测失败: " + e.getMessage());
            System.exit(2);
        }
    }

    static Result run(Options options) {
        String url = resolve(options.dbUrl, "DB_URL");
        String user = resolve(options.dbUser, "DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (url == null || user == null || password == null) {
            throw new PreflightException(
                    "缺少数据库连接信息：需 --db-url/--db-user（或环境变量 DB_URL/DB_USERNAME）与环境变量 DB_PASSWORD");
        }

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            Snapshot snapshotA = snapshot(connection);
            Snapshot snapshotB = null;
            if (options.settleSeconds > 0) {
                sleep(options.settleSeconds);
                snapshotB = snapshot(connection);
            }
            return evaluate(snapshotA, snapshotB);
        } catch (SQLException e) {
            throw new PreflightException("数据库访问失败: " + e.getMessage());
        }
    }

    /** 纯判定逻辑（包级可见以便单测）：snapshotB 为 null 表示未做稳定窗复检。 */
    static Result evaluate(Snapshot snapshotA, Snapshot snapshotB) {
        if (snapshotA.pending() != 0) {
            return new Result(false, snapshotA, snapshotB,
                    "仍有 " + snapshotA.pending() + " 条未落块消息（is_in_block=false）。先冻结入口并让旧版本出块到 0，再重试。");
        }
        if (snapshotB != null) {
            if (snapshotB.pending() != 0) {
                return new Result(false, snapshotA, snapshotB,
                        "复查出现 " + snapshotB.pending() + " 条新的未落块消息——入口未真正冻结。");
            }
            if (snapshotA.total() != snapshotB.total()
                    || snapshotA.maxConfirmTimestamp() != snapshotB.maxConfirmTimestamp()) {
                return new Result(false, snapshotA, snapshotB, "稳定窗内有新消息进入——入口未真正冻结，不能割接。");
            }
        }
        return new Result(true, snapshotA, snapshotB,
                "全部已受理消息均已落块（pending=0" + (snapshotB != null ? "，稳定窗内无新消息" : "") + "）。可割接部署新版本。");
    }

    private static Snapshot snapshot(Connection connection) throws SQLException {
        long pending = queryLong(connection, "select count(*) from msg_abstracts where is_in_block = false");
        long total = queryLong(connection, "select count(*) from msg_abstracts");
        long maxConfirm = queryLong(connection, "select coalesce(max(confirm_timestamp), 0) from msg_abstracts");
        long tipHeight = queryLong(connection, "select coalesce(max(height), -1) from block_infos");
        return new Snapshot(pending, total, maxConfirm, tipHeight);
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PreflightException("等待稳定窗时被中断");
        }
    }

    private static String resolve(String argValue, String envName) {
        if (argValue != null && !argValue.isBlank()) {
            return argValue;
        }
        String env = System.getenv(envName);
        return (env != null && !env.isBlank()) ? env : null;
    }

    static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> options.help = true;
                case "--db-url" -> options.dbUrl = requireValue(args, ++i, arg);
                case "--db-user" -> options.dbUser = requireValue(args, ++i, arg);
                case "--settle-seconds" -> options.settleSeconds = parseNonNegativeInt(requireValue(args, ++i, arg), arg);
                default -> throw new UsageException("未知选项: " + arg);
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

    private static int parseNonNegativeInt(String value, String flag) {
        try {
            int n = Integer.parseInt(value);
            if (n < 0) {
                throw new UsageException(flag + " 需为非负整数: " + value);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new UsageException(flag + " 需为非负整数: " + value);
        }
    }

    private static String usage() {
        return """
                用法: UpgradePreflightCli [选项]
                  --db-url <jdbc>        数据库 JDBC URL，默认取环境变量 DB_URL
                  --db-user <user>       数据库用户，默认取环境变量 DB_USERNAME
                  --settle-seconds <n>   稳定窗秒数：等待后复查，确认待落块仍为 0 且无新消息进入（默认 0）
                  --help                打印帮助
                数据库密码只从环境变量 DB_PASSWORD 读取（不作为命令行参数）。
                退出码: 0=GO（可割接）, 1=NO-GO, 2=用法/连接错误""";
    }

    /** 解析后的命令行选项（包级可见以便测试）。 */
    static final class Options {
        String dbUrl;
        String dbUser;
        int settleSeconds;
        boolean help;
    }

    /** 一次只读快照：待落块数、消息总数、最大入账时间、链尾高度。 */
    record Snapshot(long pending, long total, long maxConfirmTimestamp, long tipHeight) {
    }

    /** 检测结果；snapshotB 为 null 表示未做稳定窗复检。 */
    record Result(boolean go, Snapshot snapshotA, Snapshot snapshotB, String message) {
        String render() {
            StringBuilder sb = new StringBuilder();
            sb.append(go ? "GO" : "NO-GO").append(": ").append(message).append('\n');
            sb.append(renderSnapshot("快照A", snapshotA));
            if (snapshotB != null) {
                sb.append('\n').append(renderSnapshot("快照B", snapshotB));
            }
            return sb.toString();
        }

        private static String renderSnapshot(String label, Snapshot s) {
            return "  " + label + ": pending=" + s.pending()
                    + " total=" + s.total()
                    + " max_confirm=" + s.maxConfirmTimestamp()
                    + " tip=" + s.tipHeight();
        }
    }

    static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    static final class PreflightException extends RuntimeException {
        PreflightException(String message) {
            super(message);
        }
    }
}
