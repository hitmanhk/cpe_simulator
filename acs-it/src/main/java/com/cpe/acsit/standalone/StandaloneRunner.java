package com.cpe.acsit.standalone;

import com.cpe.acsit.acs.AcsServer;
import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.acs.CwmpRpc;
import com.cpe.acsit.xmpp.ClientSession;
import com.cpe.acsit.xmpp.StanzaView;
import com.cpe.acsit.xmpp.XmppTestServer;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Standalone ACS + XMPP counterpart for live message testing of the simulator.
 * Starts both servers as a long-running process, prints every Inform/XMPP
 * session event as it happens, and offers an interactive console for driving
 * RPCs and connection requests by hand.
 * <p>
 * Usage:
 * <pre>
 *   mvn -f acs-it/pom.xml compile exec:java
 *   # 或打包后：
 *   java --enable-preview -jar acs-it/target/acs-it-runner.jar [--acs-port 7547] [--xmpp-port 5222] [--domain iot.example.com]
 * </pre>
 * Then point the simulator (GUI config page or CLI start) at
 * {@code http://127.0.0.1:7547/} (ACS) and 127.0.0.1:5222 (XMPP, NAT 模式).
 */
public class StandaloneRunner {

    private static final PrintStream OUT = System.out;

    public static void main(String[] args) throws Exception {
        int acsPort = intArg(args, "--acs-port", 7547);
        int xmppPort = intArg(args, "--xmpp-port", 5222);
        String domain = strArg(args, "--domain", "iot.example.com");
        String xmppPassword = strArg(args, "--xmpp-password", "secret");

        AcsServer acs = new AcsServer(acsPort);
        acs.start();
        XmppTestServer xmpp = new XmppTestServer(domain, xmppPort).expectedPassword(xmppPassword);
        xmpp.start();

        banner(acs, xmpp, domain);

        // 实时事件打印
        acs.onInform(StandaloneRunner::printInform);
        acs.onMessage(StandaloneRunner::printNonInformMessage);
        Thread.ofVirtual().name("xmpp-online-printer").start(() -> {
            while (true) {
                try {
                    ClientSession session = xmpp.awaitOnline(java.time.Duration.ofDays(365));
                    OUT.printf("[XMPP] ONLINE  #%d jid=%s mechanism=%s%n",
                            session.id(), session.jid(), session.mechanism());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable t) {
                    return; // 服务器关闭
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            acs.stop();
            xmpp.stop();
        }));

        console(acs, xmpp);
    }

    private static void banner(AcsServer acs, XmppTestServer xmpp, String domain) {
        OUT.println("""
                ================================================================
                 ACS 集成测试对端（standalone）
                ----------------------------------------------------------------
                 ACS  (CWMP) : %s
                 XMPP        : 127.0.0.1:%d  domain=%s
                ----------------------------------------------------------------
                 模拟器指向：
                   public 模式: acs-url 填上面的 ACS 地址
                   nat    模式: 再填 xmpp-host=127.0.0.1 xmpp-port=%d
                                 xmpp-domain / xmpp-password 与这里一致
                 输入 help 查看交互命令。
                ================================================================""".formatted(
                acs.getUrl(), xmpp.getPort(), domain, xmpp.getPort()));
    }

    private static void printInform(CwmpMessage inform) {
        OUT.printf("[ACS] Inform   serial=%s events=%s params=%d%n",
                inform.serialNumber(), inform.eventCodes(), inform.parameterValues().size());
    }

    private static void printNonInformMessage(CwmpMessage message) {
        switch (message.method()) {
            case "Inform", "" -> {
                // Inform 单独打印；empty POST 太吵不打印
            }
            case "TransferComplete" -> OUT.printf("[ACS] TransferComplete key=%s fault=%s%n",
                    message.field("CommandKey"), message.body().first("FaultStruct") != null
                            ? message.body().first("FaultStruct").textOf("FaultCode") : "?");
            default -> {
                if (message.isFault()) {
                    OUT.printf("[ACS] Fault      code=%d string=%s%n",
                            message.faultCode(), message.faultString());
                } else {
                    OUT.printf("[ACS] %-12s (rpc response)%n", message.method());
                }
            }
        }
    }

    // ---------------- interactive console ----------------

    private static void console(AcsServer acs, XmppTestServer xmpp) {
        Scanner scanner = new Scanner(System.in);
        OUT.print("acs-it> ");
        OUT.flush();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                try {
                    if (!dispatch(line, acs, xmpp)) {
                        break;
                    }
                } catch (Exception e) {
                    OUT.println("错误: " + e.getMessage());
                }
            }
            OUT.print("acs-it> ");
            OUT.flush();
        }
        // stdin EOF（后台/管道/nohup 运行）：保持服务存活直至外部终止
        OUT.println("stdin 已关闭，ACS/XMPP 服务继续运行（Ctrl+C 或 kill 停止）");
        try {
            new java.util.concurrent.CountDownLatch(1).await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        acs.stop();
        xmpp.stop();
    }

    /** @return false to quit */
    private static boolean dispatch(String line, AcsServer acs, XmppTestServer xmpp) throws Exception {
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();
        switch (cmd) {
            case "quit", "exit" -> {
                return false;
            }
            case "help", "?" -> printHelp();
            case "gpv" -> {
                requireArgs(parts, 2, "gpv <参数路径...>");
                String[] paths = java.util.Arrays.copyOfRange(parts, 1, parts.length);
                printRpcResult(acs.getParameterValuesRaw(paths), "GetParameterValues");
            }
            case "spv" -> {
                requireArgs(parts, 2, "spv <名称>=<值> [...]");
                Map<String, String> params = new java.util.LinkedHashMap<>();
                for (int i = 1; i < parts.length; i++) {
                    int eq = parts[i].indexOf('=');
                    if (eq <= 0) {
                        throw new IllegalArgumentException("格式应为 名称=值: " + parts[i]);
                    }
                    params.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
                }
                printRpcResult(acs.setParameterValues(params), "SetParameterValues");
            }
            case "gpn" -> {
                requireArgs(parts, 2, "gpn <路径> [nextlevel]");
                boolean nextLevel = parts.length > 2 && Boolean.parseBoolean(parts[2]);
                printRpcResult(acs.queueRpc(
                        CwmpRpc.getParameterNames(CwmpRpc.nextId(), parts[1], nextLevel)),
                        "GetParameterNames");
            }
            case "methods" ->
                printRpcResult(acs.queueRpc(CwmpRpc.getRpcMethods(CwmpRpc.nextId())), "GetRPCMethods");
            case "reboot" ->
                printRpcResult(acs.queueRpc(CwmpRpc.reboot(CwmpRpc.nextId(), "cli-reboot")), "Reboot");
            case "factoryreset" ->
                printRpcResult(acs.queueRpc(CwmpRpc.factoryReset(CwmpRpc.nextId())), "FactoryReset");
            case "download" -> {
                requireArgs(parts, 2, "download <url> [fileType]");
                String fileType = parts.length > 2 ? parts[2] : "1 Firmware Upgrade Image";
                printRpcResult(acs.queueRpc(CwmpRpc.download(CwmpRpc.nextId(),
                        "cli-dl", parts[1], fileType, 0, 0)), "Download");
            }
            case "connreq" -> {
                List<ClientSession> online = xmpp.sessions().stream()
                        .filter(s -> s.state() == ClientSession.State.ONLINE).toList();
                if (online.isEmpty()) {
                    OUT.println("没有 ONLINE 的 XMPP 会话（nat/xmpp-only 模式上线后再试）");
                    break;
                }
                int index = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                ClientSession session = online.get(Math.min(index, online.size() - 1));
                OUT.printf("向 %s 发送 TR-069 回连…%n", session.jid());
                xmpp.sendConnectionRequest(session, "acs", "acs")
                        .thenAccept(stanza -> OUT.printf("[XMPP] 回连应答: type=%s%n", stanza.attr("type")))
                        .exceptionally(t -> {
                            OUT.println("回连失败: " + t.getMessage());
                            return null;
                        });
            }
            case "sessions" -> {
                if (xmpp.sessions().isEmpty()) {
                    OUT.println("（无 XMPP 会话）");
                }
                for (ClientSession s : xmpp.sessions()) {
                    OUT.printf("  #%d %-12s jid=%s mechanism=%s pings=%d%n",
                            s.id(), s.state(), s.jid(), s.mechanism(), s.pingCount());
                }
            }
            case "informs" -> {
                List<CwmpMessage> all = acs.getAllMessages();
                all.stream().filter(m -> "Inform".equals(m.method()))
                        .skip(Math.max(0, all.stream().filter(m -> "Inform".equals(m.method())).count() - 10))
                        .forEach(m -> OUT.printf("  serial=%s events=%s%n",
                                m.serialNumber(), m.eventCodes()));
            }
            case "auth" -> {
                requireArgs(parts, 2, "auth none|basic|digest-md5|digest-sha256");
                switch (parts[1].toLowerCase()) {
                    case "none" -> acs.noAuth();
                    case "basic" -> acs.useBasicAuth("admin", "admin");
                    case "digest-md5" -> acs.useDigestAuth("admin", "admin", "MD5");
                    case "digest-sha256" -> acs.useDigestAuth("admin", "admin", "SHA-256");
                    default -> throw new IllegalArgumentException("未知认证模式: " + parts[1]);
                }
                OUT.println("ACS 认证模式已切换（admin/admin）");
            }
            default -> OUT.println("未知命令: " + cmd + "（输入 help 查看命令列表）");
        }
        return true;
    }

    private static void printRpcResult(CompletableFuture<CwmpMessage> future, String name) {
        OUT.println(name + " 已排队，将在下一次 CWMP 会话下发（设备周期 inform 或回连触发）…");
        future.orTimeout(120, TimeUnit.SECONDS)
                .thenAccept(response -> {
                    if (response.isFault()) {
                        OUT.printf("[RPC] %s → Fault %d (%s)%n",
                                name, response.faultCode(), response.faultString());
                    } else if (!response.parameterValues().isEmpty()) {
                        OUT.printf("[RPC] %s → %d 个参数:%n", name, response.parameterValues().size());
                        response.parameterValues().forEach((k, v) -> OUT.printf("    %s = %s%n", k, v));
                    } else if (!response.methodList().isEmpty()) {
                        OUT.printf("[RPC] %s → %s%n", name, response.methodList());
                    } else if (!response.parameterNames().isEmpty()) {
                        OUT.printf("[RPC] %s → %d 个参数名:%n", name, response.parameterNames().size());
                        response.parameterNames().keySet().stream().limit(30)
                                .forEach(n -> OUT.println("    " + n));
                    } else {
                        OUT.printf("[RPC] %s → %s%n", name, response.method());
                    }
                })
                .exceptionally(t -> {
                    OUT.printf("[RPC] %s → 超时/失败: %s%n", name, t.getMessage());
                    return null;
                });
        OUT.print("acs-it> ");
        OUT.flush();
    }

    private static void printHelp() {
        OUT.println("""
                命令:
                  gpv <路径...>           GetParameterValues（打印响应参数）
                  spv <名称>=<值> [...]   SetParameterValues
                  gpn <路径> [true|false] GetParameterNames（第二参数=NextLevel）
                  methods                 GetRPCMethods（列出设备支持的方法）
                  reboot                  重启设备（设备稍后回 1 BOOT）
                  factoryreset            恢复出厂（设备稍后回 1 BOOT）
                  download <url>          下发固件下载任务
                  connreq [序号]          通过 XMPP 向在线设备发 TR-069 回连
                  sessions                列出 XMPP 会话
                  informs                 最近 10 条 Inform
                  auth <模式>             ACS 认证: none|basic|digest-md5|digest-sha256
                  quit                    退出
                提示: RPC 需等待设备开启 CWMP 会话（周期 inform 或 connreq 触发）。""");
    }

    private static void requireArgs(String[] parts, int min, String usage) {
        if (parts.length < min) {
            throw new IllegalArgumentException("用法: " + usage);
        }
    }

    private static int intArg(String[] args, String name, int defaultValue) {
        String value = strArg(args, name, null);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private static String strArg(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
