package com.zulong.hotfix.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * hotfix-config.json 配置。
 *
 * 配置极简，只有一个字段:
 * <pre>{ "patchJar": "/opt/gameserver/hotfix/hotfix-patch.jar" }</pre>
 *
 * 为保持 agent jar 零依赖（不引入第三方 JSON 库），这里用极简解析提取 patchJar 值。
 */
public class HotFixConfig {

    private final String patchJar;

    private HotFixConfig(String patchJar) {
        this.patchJar = patchJar;
    }

    public String getPatchJar() {
        return patchJar;
    }

    /**
     * @param configPath cli 通过 agentArgs 传入的配置文件路径
     */
    public static HotFixConfig load(String configPath) throws IOException {
        if (configPath == null || configPath.isEmpty()) {
            throw new IOException("agentArgs (config path) is empty");
        }
        File file = new File(configPath);
        if (!file.isFile()) {
            throw new IOException("config file not found: " + file.getAbsolutePath());
        }
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String patchJar = extractStringValue(json, "patchJar");
        if (patchJar == null || patchJar.isEmpty()) {
            throw new IOException("missing \"patchJar\" in config: " + file.getAbsolutePath());
        }
        return new HotFixConfig(patchJar);
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
