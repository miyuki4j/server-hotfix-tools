package com.zulong.hotfix.cli;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;

/**
 * hotfix-cli 入口：Attach 到目标 JVM → 加载 hotfix-agent。
 *
 * 用法:
 *   java -jar hotfix-cli.jar <pid> <agent.jar> <patch.jar>
 *
 * 说明:
 *  - 本进程是独立 JVM，通过 Attach API 连接目标 GameServer 进程。
 *  - 运行用户必须与目标进程同用户（或 root），目标 JVM 不能带 -XX:+DisableAttachMechanism。
 *  - cli jar 已内嵌 JDK8 tools.jar 的 attach API 类（shade），JDK8 下可直接 java -jar 运行。
 *  - 第 3 个参数直接是 patch.jar 路径（相对路径以 cli CWD 为基准解析为绝对），
 *    作为 agentArgs 传给 agent。不再需要 hotfix-config.json。
 */
public class HotFixCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            err("Usage: java -jar hotfix-cli.jar <pid> <agent.jar> <patch.jar>");
            System.exit(1);
        }

        String pid = args[0];
        // agentJar / patchJar 相对路径 → 以 cli CWD 为基准解析为绝对
        String agentJar = new File(args[1]).getAbsoluteFile().toString();
        String patchJarAbs = new File(args[2]).getAbsoluteFile().toString();

        log("Attaching to pid " + pid + " ...");
        log("Loading agent: " + agentJar);
        log("Patch jar: " + patchJarAbs);

        // 1. Attach 到目标 JVM
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            // 2. 加载 agent jar；把 patchJar 绝对路径作为 agentArgs 传给 agentmain
            vm.loadAgent(agentJar, patchJarAbs);
            log("Hotfix applied successfully.");
        } finally {
            // 3. 断开连接
            vm.detach();
        }
    }

    private static void log(String msg) {
        System.out.println("[hotfix-cli] " + msg);
    }

    private static void err(String msg) {
        System.err.println("[hotfix-cli] " + msg);
    }
}
