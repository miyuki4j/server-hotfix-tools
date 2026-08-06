package com.zulong.hotfix.cli;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;

/**
 * hotfix-cli 入口：Attach 到目标 JVM 并加载 hotfix-agent。
 *
 * 用法:
 *   java -jar hotfix-cli.jar <pid> <agent.jar> [config.json]
 *
 * 说明:
 *  - 本进程是独立 JVM，通过 Attach API 连接目标 GameServer 进程。
 *  - 运行用户必须与目标进程同用户（或 root），目标 JVM 不能带 -XX:+DisableAttachMechanism。
 *  - cli jar 已内嵌 JDK8 tools.jar 的 attach API 类（shade），因此 JDK8 下可直接 java -jar 运行。
 *  - agentJar 和 config 路径若为相对路径，cli 会以自身 CWD（用户敲命令时的目录）为基准解析为绝对路径
 *    后再传给 agent，保证 agent 端能正确解析相对 patchJar（以 config 文件所在目录为基准）。
 */
public class HotFixCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar hotfix-cli.jar <pid> <agent.jar> [config.json]");
            System.exit(1);
        }

        String pid = args[0];
        // 相对路径 → 以 cli CWD 为基准解析为绝对路径
        String agentJar = new File(args[1]).getAbsoluteFile().toString();
        String config = args.length > 2
                ? new File(args[2]).getAbsoluteFile().toString()
                : "";

        System.out.println("[hotfix-cli] Attaching to pid " + pid + " ...");

        // 1. Attach 到目标 JVM
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            // 2. 加载 agent jar 到目标 JVM 内
            //    config 路径会作为 agentArgs 传给 agentmain(String, Instrumentation)
            System.out.println("[hotfix-cli] Loading agent: " + agentJar);
            if (!config.isEmpty()) {
                System.out.println("[hotfix-cli] Config: " + config);
            }
            vm.loadAgent(agentJar, config);
            System.out.println("[hotfix-cli] Hotfix applied successfully.");
        } finally {
            // 3. 断开连接
            vm.detach();
        }
    }
}