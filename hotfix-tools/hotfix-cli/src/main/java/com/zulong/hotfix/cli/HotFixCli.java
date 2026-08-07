package com.zulong.hotfix.cli;

import com.sun.tools.attach.VirtualMachine;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * hotfix-cli 入口：解析 config → Attach 到目标 JVM → 加载 hotfix-agent。
 *
 * 用法:
 *   java -jar hotfix-cli.jar <pid> <agent.jar> <config.json>
 *
 * 说明:
 *  - 本进程是独立 JVM，通过 Attach API 连接目标 GameServer 进程。
 *  - 运行用户必须与目标进程同用户（或 root），目标 JVM 不能带 -XX:+DisableAttachMechanism。
 *  - cli jar 已内嵌 JDK8 tools.jar 的 attach API 类 + org.json（shade），JDK8 下可直接 java -jar 运行。
 *  - JSON 解析在 cli 完成（cli 不注入目标 JVM，故 org.json 不进入 GameServer 进程）。
 *    cli 解析 config 取出 patchJar，按相对/绝对规则解析为绝对路径后，作为 agentArgs 传给 agent。
 *  - agentJar / config 相对路径以 cli CWD 为基准；patchJar 相对路径以 config 文件所在目录为基准。
 */
public class HotFixCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            err("Usage: java -jar hotfix-cli.jar <pid> <agent.jar> <config.json>");
            System.exit(1);
        }

        String pid = args[0];
        // agentJar 相对路径 → 以 cli CWD 为基准解析为绝对
        String agentJar = new File(args[1]).getAbsoluteFile().toString();
        // config 相对路径 → 以 cli CWD 为基准解析为绝对
        File configFile = new File(args[2]).getAbsoluteFile();
        if (!configFile.isFile()) {
            throw new IOException("config file not found: " + configFile.getAbsolutePath());
        }

        // 解析 config.json（org.json），取出 patchJar 并解析为绝对路径
        String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        JSONObject obj = new JSONObject(json);
        String patchJarRaw;
        try {
            patchJarRaw = obj.getString("patchJar");
        } catch (JSONException e) {
            throw new IOException("missing \"patchJar\" in config: " + configFile.getAbsolutePath(), e);
        }
        File patchJarFile = new File(patchJarRaw);
        if (!patchJarFile.isAbsolute()) {
            // 相对路径以 config 文件所在目录为基准
            File configDir = configFile.getParentFile();
            if (configDir == null) {
                throw new IOException("cannot resolve relative patchJar: config has no parent dir: " + configFile);
            }
            patchJarFile = new File(configDir, patchJarRaw);
        }
        String patchJarAbs = patchJarFile.getAbsoluteFile().toString();

        log("Attaching to pid " + pid + " ...");
        log("Loading agent: " + agentJar);
        log("Config: " + configFile);
        log("Patch jar: " + patchJarAbs);

        // 1. Attach 到目标 JVM
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            // 2. 加载 agent jar；把解析好的 patchJar 绝对路径作为 agentArgs 传给 agentmain
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
