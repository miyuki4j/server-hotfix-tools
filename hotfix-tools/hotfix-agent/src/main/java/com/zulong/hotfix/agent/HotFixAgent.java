package com.zulong.hotfix.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * hotfix-agent 核心：自动区分新老类并完成热更新。
 * <p>
 * 流程:
 * 1. 解析 hotfix-config.json，拿到 patch.jar 路径
 * 2. 扫描 patch.jar 中所有 .class 文件
 * 3. 用 Instrumentation.getAllLoadedClasses() 区分新老类
 * - 已加载 → 旧类 → redefineClasses 替换方法体
 * - 未加载 → 新类 → appendToSystemClassLoaderSearch 后由 AppClassLoader 加载
 * 4. 新类预加载（提前暴露编译/ linkage 错误）
 * 5. 旧类从 jar 读 bytes，redefineClasses 直接替换
 * <p>
 * 注意：agentmain 的 stdout 输出在目标 JVM（GameServer）的控制台。
 */
public class HotFixAgent {

    public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
        log("=== Hotfix Agent Start ===");

        HotFixConfig config = HotFixConfig.load(agentArgs);
        File patchJar = new File(config.getPatchJar());
        if (!patchJar.isFile()) {
            throw new FileNotFoundException("patch jar not found: " + patchJar.getAbsolutePath());
        }
        log("Patch jar: " + patchJar.getAbsolutePath());

        // 1. 扫描 jar 中所有 class 文件, 拿到全限定类名列表
        List<String> allClassNames = scanJarClassNames(patchJar);
        log("Found " + allClassNames.size() + " classes in jar.");

        // 2. 用 getAllLoadedClasses() 区分新老类
        //    纯查询, 不触发任何类加载行为（Class.forName 会有副作用, 不可用）
        Set<String> loadedNames = new HashSet<>();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            loadedNames.add(c.getName());
        }

        List<String> newClasses = new ArrayList<>();
        List<String> oldClasses = new ArrayList<>();
        for (String name : allClassNames) {
            if (loadedNames.contains(name)) {
                oldClasses.add(name);
            } else {
                newClasses.add(name);
            }
        }

        log("New classes (" + newClasses.size() + "): " + newClasses);
        log("Old classes (" + oldClasses.size() + "): " + oldClasses);

        // 3. append patch.jar 到 AppClassLoader 搜索路径
        //    必须在 redefine 之前: redefine 验证字节码时可能需要加载被引用的新类
        inst.appendToSystemClassLoaderSearch(new JarFile(patchJar));
        log("Appended patch.jar to system classloader.");

        // 4. 新类: 预加载（显式触发, 提前发现错误; 否则等旧类方法体调用时才懒加载）
        for (String name : newClasses) {
            try {
                Class.forName(name, true, ClassLoader.getSystemClassLoader());
                log("  Loaded new class: " + name);
            } catch (Throwable e) {
                err("  Failed to load new class: " + name + " - " + e);
                throw e;
            }
        }

        // 5. 旧类: 从 jar 读取 bytes, 用 redefineClasses 直接替换
        //    JVMTI 限制: 只能改方法体/常量池, 不能加方法/字段/改签名/改继承
        if (!oldClasses.isEmpty()) {
            List<ClassDefinition> defs = new ArrayList<>();
            for (String name : oldClasses) {
                Class<?> clz = Class.forName(name, false, ClassLoader.getSystemClassLoader());
                byte[] bytes = readClassFromJar(patchJar, name);
                defs.add(new ClassDefinition(clz, bytes));
                log("  Redefining: " + name + " (" + bytes.length + " bytes)");
            }
            inst.redefineClasses(defs.toArray(new ClassDefinition[0]));
            log("Redefine done for " + defs.size() + " classes.");
        }

        log("=== Hotfix Agent Done ===");
    }

    /**
     * 扫描 jar 中所有 .class 文件, 返回全限定类名列表
     */
    private static List<String> scanJarClassNames(File jarFile) throws IOException {
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.contains("module-info")) {
                    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    names.add(className);
                }
            }
        }
        return names;
    }

    /**
     * 从 jar 中读取指定 class 的字节码
     */
    private static byte[] readClassFromJar(File jarFile, String className) throws IOException {
        String entryName = className.replace('.', '/') + ".class";
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new FileNotFoundException("Class not found in jar: " + entryName);
            }
            try (InputStream is = jar.getInputStream(entry)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                return bos.toByteArray();
            }
        }
    }

    private static void log(String msg) {
        System.out.println("[hotfix-agent] " + msg);
    }

    private static void err(String msg) {
        System.err.println("[hotfix-agent] " + msg);
    }
}
