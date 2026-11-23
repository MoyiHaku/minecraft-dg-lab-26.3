# Minecraft-DG-LAB · Status Edition

Minecraft Java **26.3** / Fabric 双端模组：受伤画面反馈 HUD、16 个屏幕波形预设、只读 V3 扫码配对，以及玩家主动开启的 A/B 读数同步。

基于 DancingSnow0517/Minecraft-DG-LAB 1.2.7 的独立移植（版本 `1.2.7+status.26.3.3`）。玩家在自己的游戏电脑上建立只读连接，可自行开启向当前服务器共享。屏幕显示的是 App 实际上报的数值；游戏事件和服务端脚本不能通过本版向设备发送刺激指令。

## 环境要求

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.166.2+26.3 |
| Java | 25（编译与运行） |
| Gradle | 9.9.2（由 `gradlew` 提供，无需单独安装） |
| Fabric Loom | 1.19.4（`gradle.properties` 的 `loom_version`，插件 id `net.fabricmc.fabric-loom`） |

## 目录结构

```text
.
├── build.gradle              # Loom 构建脚本（依赖、jar-in-jar、资源版本注入）
├── settings.gradle           # 插件仓库与项目名
├── gradle.properties         # 版本号与 Gradle 运行参数
├── gradlew / gradlew.bat     # Gradle Wrapper
├── gradle/wrapper/           # Wrapper 配置
├── src/main/java/            # cn.dancingsnow.dglab.status.*（服务端 + 客户端入口）
├── src/main/resources/       # fabric.mod.json、assets/dglab（语言文件、波形预设）、icon.png
├── licenses/                 # 第三方许可（ZXing Apache-2.0）
├── tools/                    # 波形预设导入脚本（开发辅助，不参与构建）
├── LICENSE                   # GNU AGPL-3.0
└── README.md
```

Minecraft 26.3 的官方 JAR 已未混淆，因此本项目不使用 Yarn/Mojang mappings，也不需要 remap 步骤，源码直接按官方类名编译。

## 构建

依赖全部由 Gradle 管理：Minecraft 26.3、Fabric Loader 0.20.1、Fabric API 0.166.2+26.3、ZXing Core 3.5.3。ZXing 通过 Loom 的 `include` 打包为 jar-in-jar（产物内 `META-INF/jars/`），因此运行时不需额外安装。

需要 JDK 25：Gradle 守护进程本身也必须运行在 25 上（仅靠编译 toolchain 不够）。若默认 `java` 不是 25，请把 `JAVA_HOME` 指向 JDK 25，或让 Gradle 用 `org.gradle.java.home` 指定本机 JDK。

```sh
./gradlew build          # Windows: gradlew.bat build
```

产物：`build/libs/Minecraft-DG-LAB-26.3-Fabric-Status-1.2.7+status.26.3.3.jar`（另生成 `-sources.jar`）。

首次构建时 Wrapper 会下载 Gradle 9.9.2 分发包。Gradle 缓存与 Wrapper 分发的位置由 `GRADLE_USER_HOME` 决定（默认是用户目录下的 `.gradle`）；仓库里只保留 `build.gradle` / `settings.gradle` / `gradle.properties` / Wrapper 这类可移植配置，不写入任何绝对路径，构建产生的 `build/`、`.gradle/` 都由 `.gitignore` 排除。

## 安装

**服务端**：把 `build/libs/` 下的 JAR 放入 Fabric 服务端的 `mods` 目录。已有同名旧版时替换原文件；升级不需要覆盖 `world` 或 `server.properties`。

**客户端**：把同一个 JAR 放入 Fabric 实例的 `mods` 目录。请只保留一个 `dglab` 版本。原版 26.3 玩家可以照常进服。

## 自定义本玩家的受伤反馈

版本 1.2.7+status.26.3.3 使用 Fabric 服务端的受伤事件，在受伤玩家自己的客户端显示反馈面板。单人游戏也会加载同一事件监听。多人游戏需要双方更新 JAR 才能看到受伤提示。画面反馈独立于手机，初始基准为 20 点，每次命中默认增加 12 点，每秒向基准恢复 4 点，范围 0–100。反馈面板在命中后显示约 5 秒。下方色条表示画面点数。上方的“设备上报 · A/B”保留手机 App 的当前值和上限，沿用原有同步命令。

例如，只让跌落和魔法触发提示，跌落增加 15 点并显示「潮汐」，魔法减少 8 点并显示「呼吸」。在游戏聊天中依次输入：

```text
/dglab feedback sources none
/dglab feedback source minecraft:fall on
/dglab feedback source minecraft:fall step 15
/dglab feedback source minecraft:fall wave 潮汐
/dglab feedback source minecraft:magic on
/dglab feedback source minecraft:magic step -8
/dglab feedback source minecraft:magic wave 呼吸
```

| 游戏内命令 | 作用 |
| --- | --- |
| `/dglab feedback`、`/dglab feedback status` | 查看本地设置，或查询当前画面点数、上一次生效的伤害和波形。 |
| `/dglab feedback waves` | 列出 16 个波形，点名称会填入选择默认波形的命令，按回车保存。 |
| `/dglab feedback preview 潮汐` | 独立预览 5 秒。手动预览保留上一次受伤及点数记录。 |
| `/dglab feedback wave 呼吸` | 修改默认的波形预设。 |
| `/dglab feedback base 20` | 将基准和当前画面点数设为 20（允许 0–100）。 |
| `/dglab feedback step 12` | 设置每次选中伤害的默认增减，范围 −100 到 +100，正数增加，负数减少。 |
| `/dglab feedback decay 4` | 每 20 个游戏刻向基准移动 4 点（0–100）。填 0 可停留在当前值。 |
| `/dglab feedback sources all`、`sources none` | 全选或清空来源；之前为各来源指定的步长和波形继续保留。 |
| `/dglab feedback source minecraft:fall` | 查询某一来源的有效配置。来源按当前世界的数据注册，可按 Tab 补全，支持数据包自定义 ID。 |
| `/dglab feedback source minecraft:fall reset` | 清除该来源的单独设置，使其继承默认值。 |
| `/dglab feedback on`、`off` | 开关受伤提示；设备读数和共享仍可独立使用。 |

设置按游戏实例保存在 `config/dglab-damage-hud.json`。新角色、复活或重连时画面点数回到基准，偏好保留。其他玩家受伤不会增加本人的点数。波形缩略图仅用于屏幕绘制。伤害事件和画面点数均不作为设备 A/B 读数上送。

## 配对与同步

1. 手机和运行游戏的电脑接入同一局域网。进入服务器后输入 `/dglab share on`。若尚未配对，左上角会出现二维码。用支持 SOCKET V3 的 DG-LAB 3.x App 扫码，配对码在三分钟内有效。
2. 手机读数进入玩家本地 HUD。Minecraft 通过现有游戏连接向本服同步 A/B 当前值和上限。服务器无需额外开端口。首次读数到达前显示“等待读数”。
3. 在游戏里输入 `/dglabserver status` 核对服务器收到的读数。普通玩家可以查自己。管理员（命令权限 2 级及以上）可用 `/dglabserver list` 列出正在共享的玩家，用 `/dglabserver status 玩家名` 查询指定在线玩家。在控制台输入同样的命令时省略斜杠。
4. `/dglab share off` 立即撤回本服读数，本地 HUD 继续显示。`/dglab disconnect` 同时结束设备连接和共享。断开手机、离服或重启服务端，模组会清除最近读数。再次进服需由玩家重新开启共享。
5. `/dglab connect` 可单独用于本机配对，`/dglab status` 查看本地状态。已经在本机配对的玩家也可直接运行 `/dglab share on` 开始同步。

## 网络和显示选项

- 多个网卡时可显式指定开游戏这台电脑的内网 IPv4，例如 `/dglab connect 192.168.1.20`。默认选择空闲端口，也可在地址后指定端口，例如 `/dglab connect 192.168.1.20 18080`。若二维码选错网卡，先输入 `/dglab disconnect`，再用指定地址配对并输入 `/dglab share on`。
- 显示出的 IP 应与电脑当前的局域网 IPv4 一致。Windows 防火墙若询问，允许游戏的 Java 在专用网络接收本地连接。访客 Wi-Fi 的客户端隔离可能阻止手机访问电脑。
- `/dglab hud on`、`/dglab hud off` 控制读数面板。`/dglab hud max on` 或 `off` 控制上限显示。`/dglab hud position left` 或 `right` 控制位置。显示偏好保存在实例的 `config/dglab-status-hud.properties`。
- 配对码在左上角，连接成功后，通道读数默认出现在右上角。可与游戏受伤 HUD 一起使用。
- 本版适配原项目使用的 SOCKET V3。DG-LAB 4.x 专用连接协议、旧版 KubeJS 脚本接口和实体设备输出功能均未移植。

## 来源与许可

原项目由 DancingSnow 开发。本移植基于 `1.21.1-1.2.7-build.32`，提交 `6f7bf819d37dc23e89cbe070c6c0081db20ea0f8`：
https://github.com/DancingSnow0517/Minecraft-DG-LAB/tree/6f7bf819d37dc23e89cbe070c6c0081db20ea0f8

本移植提供 Fabric 客户端和专用服务端入口，重写本机 V3 只读会话，迁移二维码与状态绘制，并添加中英文命令提示。模组在服务器内存中保存每位共享玩家的最新一组读数；配对码、手机地址及设备标识留在玩家电脑。

16 种预设的名称与简化曲线读取自下列固定版本，随包保留 18 列的画面缩略图，离线也可切换。导入脚本见 [tools/import_wave_previews.py](tools/import_wave_previews.py)：
https://github.com/dungeonlab-open/dglab-websocket-simple/blob/5cda0e8bdac35f61286430ec4d0c77947fcd4f4d/socket/DG_WAVES_V2_V3_simple.js

项目授权为 GNU AGPL v3，见 [LICENSE](LICENSE)。二维码编码使用 ZXing Core 3.5.3，见 [licenses/ZXing-Apache-2.0.txt](licenses/ZXing-Apache-2.0.txt)。图标取自同一上游版本。
