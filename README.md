# server-hotfix-tools

Java 服务器代码热更新工具——基于 **ClassLoader + Instrumentation** 方案，支持：

- 旧类**只改方法体逻辑**（不加方法/字段/不改签名）→ `redefineClasses` 直接替换字节码
- **新增类** → `appendToSystemClassLoaderSearch` + AppClassLoader 加载，旧类方法体可直接调用

> 详细技术原理与约束见 [`技术需求.MD`](技术需求.MD)。

## 模块

| 模块 | 职责 |
|------|------|
| `hotfix-cli` | Attach 入口：连接目标 JVM 并加载 agent。**已 shade 内嵌 tools.jar**，JDK8 下 `java -jar` 直接可用 |
| `hotfix-agent` | 热更核心：扫 patch.jar → `getAllLoadedClasses()` 区分新老类 → 加载新类 + redefine 旧类 |
| `hotfix-demo` | 模拟 GameServer + patch 源码，用于端到端验证 |

## 环境要求

- **JDK 1.8**（开发与运行均用 1.8.0_201 验证通过）
- **Maven 3.x**
- 目标 GameServer 进程不能带 `-XX:+DisableAttachMechanism`
- hotfix-cli 运行用户须与目标进程同用户（或 root）

## 构建

### 1. 一次性：把 tools.jar 装入本地 Maven 仓库

`hotfix-cli` 内嵌了 JDK8 `tools.jar` 的 attach API 类。由于 maven-shade-plugin 不打包 `system` scope 依赖，需先把 tools.jar 作为普通 artifact 装入本地仓库（**每台构建机各执行一次**）：

```bash
mvn install:install-file -Dfile=%JAVA_HOME%/lib/tools.jar \
    -DgroupId=com.sun -DartifactId=tools -Dversion=1.8 -Dpackaging=jar -DgeneratePom=true
```

> Linux/Mac 用 `$JAVA_HOME/lib/tools.jar`。

### 2. 打包

```bash
mvn package -DskipTests
```

产物：

- `hotfix-cli/target/hotfix-cli.jar`（shade 后约 7.8MB，自包含）
- `hotfix-agent/target/hotfix-agent.jar`
- `hotfix-demo/target/hotfix-demo.jar`

## 使用流程

### 开发机：制作 hotfix-patch.jar

把新增类 + 修改后的旧类（只改方法体）编译进**同一个 jar**：

```bash
javac -d /tmp/classes NewSkillHelper.java SkillModule.java
jar cf hotfix-patch.jar -C /tmp/classes .
```

新老类的区分由 agent 自动完成，无需在 config 里标注。

### 服务器：执行热更

目录布局（路径需为 ASCII，见「常见问题」）：

```
/opt/gameserver/
├── GameServer.jar
└── hotfix/
    ├── hotfix-cli.jar
    ├── hotfix-agent.jar
    ├── hotfix-patch.jar
    └── hotfix-config.json
```

`hotfix-config.json` 中 `patchJar` 支持**相对或绝对路径**：

```jsonc
// 方式 A：相对路径（推荐，三个 jar + config 同目录时最省事）
{ "patchJar": "hotfix-patch.jar" }

// 方式 B：绝对路径
{ "patchJar": "/opt/gameserver/hotfix/hotfix-patch.jar" }
```

> **路径解析规则**：相对路径以 **config 文件所在目录**为基准解析（不是目标 JVM 的工作目录）。
> 因此只要 `hotfix-cli.jar` / `hotfix-agent.jar` / `hotfix-patch.jar` / `hotfix-config.json` 放在同一目录，
> 无论 GameServer 从哪里启动都能正确找到。cli 传给它的 `agent.jar` / `config.json` 参数同样支持相对路径（以 cli 运行时的 CWD 为基准）。

执行（推荐：cd 进 hotfix 目录，全用相对参数）：

```bash
pid=$(jps | grep GameServer | awk '{print $1}')
cd /opt/gameserver/hotfix
java -jar hotfix-cli.jar $pid hotfix-agent.jar hotfix-config.json
```

## 端到端 demo 验证

```bash
# 1. 构建 patch jar（demo 的新类 + 改后旧类）
cd hotfix-tools/hotfix-demo
javac -d target/patch-classes patch-src/com/zulong/hotfix/demo/*.java
jar cf target/hotfix-patch.jar -C target/patch-classes .

# 2. 启动模拟 GameServer（每 2s 打印 damage）
java -jar target/hotfix-demo.jar
# 输出: tick=1 damage=20  (旧逻辑 base*2)

# 3. 另开终端，取 PID 后执行热更
jps -l | grep hotfix-demo   # 假设 PID=12345
java -jar ../hotfix-cli/target/hotfix-cli.jar 12345 \
    ../hotfix-agent/target/hotfix-agent.jar \
    <指向 hotfix-patch.jar 的 config 路径>

# 4. 观察 GameServer 输出变为 damage=30（新逻辑 base*3，经新类 NewSkillHelper）
```

## 约束与限制

- **旧类 redefine**：只能改方法体/常量池；不能加方法、加字段、改签名、改继承。需要加方法/字段时放到新增类里，旧类方法体调用它
- **新增类**：被 AppClassLoader 加载后**无法卸载**，不能反复热更同一个新类；设计为只增不改
- **线程安全**：redefine 在 safepoint 执行；`while(true)` 死循环方法会阻塞 redefine，避免热更 main loop 类
- **Metaspace**：每次热更新增类会占用新 Metaspace，建议 `-XX:MaxMetaspaceSize=512m` 或更大
- 详见 `技术需求.MD` 第八、九节

## 常见问题

**Q: IntelliJ 中 `com.sun.tools.attach.VirtualMachine` 红色无法解析？**
A: shade 后 cli 依赖来自本地 Maven 仓库的 `com.sun:tools:1.8`（compile scope），与 Project SDK 是 JRE 还是 JDK 无关。确保已执行上面的 `install:install-file`，再 Reload All Maven Projects。

**Q: JDK8 下 `java -jar hotfix-cli.jar` 报 `NoClassDefFoundError`?**
A: 旧版本 cli 没内嵌 tools.jar 需手动加 classpath。本仓库已 shade，直接 `java -jar` 即可。若仍报错，检查是否用的是本仓库 shade 后的 jar。

**Q: agent 报 "config file not found" 且路径是乱码？**
A: Attach API 传输 `agentArgs` 时非 ASCII 路径会乱码。**config 路径与 patchJar 路径都需用 ASCII**（生产 `/opt/gameserver/` 天然满足；本地验证若工作区含中文，把产物拷到 `C:\hotfix-test\` 之类的 ASCII 目录再跑）。
