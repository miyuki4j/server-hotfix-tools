package com.zulong.hotfix.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * hotfix-config.json 配置。
 *
 * 配置极简，只有一个字段:
 * <pre>{ "patchJar": "hotfix-patch.jar" }</pre>
 *
 * patchJar 路径支持相对或绝对：
 * <ul>
 *   <li>绝对路径（如 {@code /opt/gameserver/hotfix/hotfix-patch.jar} 或 {@code C:/hotfix/hotfix-patch.jar}）：原样使用</li>
 *   <li>相对路径（如 {@code hotfix-patch.jar} 或 {@code ../hotfix/hotfix-patch.jar}）：以 config 文件所在目录为基准解析，
 *       因此只要三个 jar + config 放在同一目录，就可直接写 {@code "patchJar": "hotfix-patch.jar"}，
 *       无需关心目标 JVM（GameServer）从哪个目录启动</li>
 * </ul>
 *
 * 为保持 agent jar 零依赖（不引入第三方 JSON 库），这里用极简解析提取 patchJar 值。
 */
public class HotFixConfig {

    private final File configFile;
    /** 解析后的绝对路径（相对路径已拼到 config 父目录）。 */
    private final String patchJar;

    private HotFixConfig(File configFile, String patchJar) {
        this.configFile = configFile;
        this.patchJar = patchJar;
    }

    /** 解析后的 patchJar 绝对路径。 */
    public String getPatchJar() {
        return patchJar;
    }

    /** config 文件对象（绝对路径 File），便于日志/调试。 */
    public File getConfigFile() {
        return configFile;
    }

    /**
     * @param configPath cli 通过 agentArgs 传入的配置文件路径。
     *                   cli 端已转为绝对路径（基准=cli CWD），此处保留对相对路径的兼容
     *                   （以 target JVM CWD 解析，仅作 fallback）。
     */
    public static HotFixConfig load(String configPath) throws IOException {
        if (configPath == null || configPath.isEmpty()) {
            throw new IOException("agentArgs (config path) is empty");
        }
        // config 文件：转绝对（防御性；正常由 cli 预先转好）
        File configFile = new File(configPath).getAbsoluteFile();
        if (!configFile.isFile()) {
            throw new IOException("config file not found: " + configFile.getAbsolutePath());
        }
        String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        String patchJarRaw = extractStringValue(json, "patchJar");
        if (patchJarRaw == null || patchJarRaw.isEmpty()) {
            throw new IOException("missing \"patchJar\" in config: " + configFile.getAbsolutePath());
        }

        // patchJar 相对/绝对判断与解析
        File patchJarFile = new File(patchJarRaw);
        if (!patchJarFile.isAbsolute()) {
            File configDir = configFile.getParentFile();
            if (configDir == null) {
                throw new IOException(
                        "cannot resolve relative patchJar, config has no parent dir: " + configFile);
            }
            patchJarFile = new File(configDir, patchJarRaw);
        }
        String patchJarAbs = patchJarFile.getAbsoluteFile().toString();
        return new HotFixConfig(configFile, patchJarAbs);
    }

    /**
     * 从 JSON 文本中提取指定 key 的字符串值（仅支持扁平结构的字符串字段）。
     */
    private static String extractStringValue(String json, String key) {
        String quotedKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(quotedKey);
        if (keyIdx < 0) {
            return null;
        }
        int colonIdx = json.indexOf(':', keyIdx + quotedKey.length());
        if (colonIdx < 0) {
            return null;
        }
        int i = colonIdx + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                // 处理转义字符（如 \" 和 \\）
                sb.append(json.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') {
                return sb.toString();
            }
            sb.append(c);
            i++;
        }
        return null;
    }
}