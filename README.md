# Minecraft-DG-LAB-Connect

Minecraft Java **26.3** / Fabric 客户端/服务端可选模组：受伤画面反馈 HUD、16 个波形预设、只读 V3 扫码配对、A/B 读数同步，以及把本地受伤事件下发给已配对设备的功能。

从 DancingSnow0517/Minecraft-DG-LAB 1.2.7 移植而来，此后独立演进。强度、波形与设备输出都由受伤事件驱动，配对与读数在本机完成；服务器只会看到你主动开启共享的 A/B 数值，无法触发任何输出。

## 环境要求

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.166.2+26.3 |
| Java | 25（编译与运行） |
| Gradle | 9.9.2（由 `gradlew` 提供，无需单独安装） |
| Fabric Loom | 1.19.4（`gradle.properties` 的 `loom_version`，插件 id `net.fabricmc.fabric-loom`） |

## 版本与发布通道

产物命名规则：`Minecraft-DG-LAB-Connect-<Minecraft 版本>-Fabric-<通道>_<模组版本>.jar`

| 片段 | 含义 |
| --- | --- |
| `Minecraft-DG-LAB-Connect` | 模组名 |
| `26.3` | Minecraft 版本|
| `Alpha` / `Beta` / `Release` | 发布通道，由 `mod_channel` 决定 |
| `0.0.1` | 模组自身版本，独立编号 |

```sh
gradlew.bat build                        # 默认 Alpha（gradle.properties 的 mod_channel）
gradlew.bat build -Pmod_channel=Beta     # 同一份代码出 Beta 通道产物
gradlew.bat build -Pmod_channel=Release  # 正式版通道
gradlew.bat build -Pmod_version=0.0.2    # 指定模组版本
```
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
## 构建

依赖全部由 Gradle 管理：Minecraft 26.3、Fabric Loader 0.20.1、Fabric API 0.166.2+26.3、ZXing Core 3.5.3。ZXing 通过 Loom 的 `include` 打包为 jar-in-jar（产物内 `META-INF/jars/`），因此运行时不需额外安装。

需要 JDK 25：Gradle 守护进程本身也必须运行在 25 上（仅靠编译 toolchain 不够）。若默认 `java` 不是 25，请把 `JAVA_HOME` 指向 JDK 25，或让 Gradle 用 `org.gradle.java.home` 指定本机 JDK。

```sh
./gradlew build          # Windows: gradlew.bat build
```

产物：`build/libs/Minecraft-DG-LAB-Connect-26.3-Fabric-Alpha_0.0.1.jar`（另生成 `-sources.jar`）。发布 Beta 通道时加参数：`gradlew.bat build -Pmod_channel=Beta`，产物名随之变成 `...-Beta_0.0.1.jar`。

首次构建时 Wrapper 会下载 Gradle 9.9.2 分发包。Gradle 缓存与 Wrapper 分发的位置由 `GRADLE_USER_HOME` 决定（默认是用户目录下的 `.gradle`）；仓库里只保留 `build.gradle` / `settings.gradle` / `gradle.properties` / Wrapper 这类可移植配置，不写入任何绝对路径，构建产生的 `build/`、`.gradle/` 都由 `.gitignore` 排除。

## 安装

**服务端**：把 `build/libs/` 下的 JAR 放入 Fabric 服务端的 `mods` 目录。已有同名旧版时替换原文件即可。

**客户端**：把同一个 JAR 放入 Fabric 实例的 `mods` 目录。

## 自定义本玩家的受伤反馈

本模组使用 Fabric 服务端的受伤事件，在受伤玩家自己的客户端显示反馈面板。画面反馈独立于手机，默认基础强度为 20，每次命中默认增加 12，每秒向基础强度回落 4，范围 0–100；这个数值就是下发给设备的通道强度。受伤面板只在设备已配对连接时出现（断开即隐藏），并受「受伤提示」开关控制。面板在命中后显示约 5 秒，下方色条表示当前强度。上方的“设备上报 · A/B”保留手机 App 的当前值和上限，沿用原有同步命令。

打开 `/dglab menu` 可以图形化调整下面所有项目：受伤开关、默认来源、基础强度/每次增减/每秒回落、默认波形、设备同步开关、输出通道、脉冲帧数、最大排队秒数、回落到基础强度后停止输出，以及「死亡事件」和按来源自定义的配置开关、步长与波形。右侧「来源设置 ▸」切换到死亡事件与逐来源覆盖页；菜单里的「默认来源」只改默认值，已经单独设置过的来源仍按自己的开关生效（要一次性清空用 `/dglab feedback sources all|none`）。

**死亡事件**是独立的一项：服务端用 `ServerLivingEntityEvents.AFTER_DEATH` 单独上报，不与其他伤害类型共用规则。默认开启、`+100` 强度（因此直接拉满）、波形「潮汐」，同样会按设置下发到设备。

它监听的是「玩家死亡」这个事件本身；伤害来源没有注册键时按 `dglab:unknown` 兜底上报。两点例外：不死图腾等取消死亡的机制只会走普通受伤规则；致命伤会跳过 `AFTER_DAMAGE`，所以不会和普通命中重复触发。

例如，只让跌落和魔法触发提示，跌落增加 15 强度并显示「潮汐」，魔法减少 8 强度并显示「呼吸」。在游戏聊天中依次输入：

```text
/dglab feedback sources none
/dglab feedback source minecraft:fall on
/dglab feedback source minecraft:fall step 15
/dglab feedback source minecraft:fall wave 潮汐
/dglab feedback source minecraft:magic on
/dglab feedback source minecraft:magic step -8
/dglab feedback source minecraft:magic wave 呼吸
```

| 游戏内命令 | 作用                                                       |
| --- |----------------------------------------------------------|
| `/dglab feedback`、`/dglab feedback status` | 查看本地设置，或查询当前强度、上一次生效的伤害和波形。                              |
| `/dglab feedback waves` | 列出 16 个波形，点名称会填入选择默认波形的命令，按回车保存。                         |
| `/dglab feedback preview 潮汐` | 预览 5 秒。手动预览保留上一次受伤及强度记录。                                 |
| `/dglab feedback wave 呼吸` | 修改默认的波形预设。                                               |
| `/dglab feedback base 20` | 将基础强度和当前强度设为 20（允许 0–100）。                               |
| `/dglab feedback step 12` | 设置每次选中伤害的默认增减，范围 −100 到 +100，正数增加，负数减少。                  |
| `/dglab feedback decay 4` | 每 20 个游戏刻向基础强度回落 4（0–100）。填 0 可停留在当前值。                   |
| `/dglab feedback sources all`、`sources none` | 全选或清空来源；之前为各来源指定的步长和波形继续保留。                              |
| `/dglab feedback source minecraft:fall` | 查询某一来源的有效配置。来源按当前世界的数据注册，可按 Tab 补全，支持数据包自定义 ID。          |
| `/dglab feedback source minecraft:fall reset` | 清除该来源的单独设置，使其继承默认值。                                      |
| `/dglab feedback on`、`off` | 开关受伤提示；设备读数和共享仍可独立使用。                                    |
| `/dglab feedback death` | 查看死亡事件的设置（默认开启，+100 点，「潮汐」波形）。                           |
| `/dglab feedback death on\|off`、`step 100`、`wave 潮汐`、`reset` | 单独配置死亡事件：是否触发、点数增减、波形，或恢复默认。                             |
| `/dglab menu` | 打开图形化设置界面（受伤规则、波形、设备同步、按来源覆盖）。                           |
| `/dglab device on`、`off` | 开关「受伤 → 设备」联动。默认关闭。                                      |
| `/dglab device channel 1\|2\|3` | 选择输出通道：1 = A，2 = B，3 = 双通道。                              |
| `/dglab device frames 2` | 受伤提示每条竖条表示几帧，1 帧 = 100 ms，1–3。                           |
| `/dglab device queue 6` | 设备端最大排队秒数（1 秒 = 10 帧，默认 6 秒 = 60 帧）。队列满后新事件被丢弃，可填任意非负整数。 |
| `/dglab device idle on\|off` | 回落到基础强度后是否停止输出。                                          |

设置按游戏实例保存在 `config/dglab-damage-hud.json`。新角色、复活或重连时强度回到基础值，偏好保留。其他玩家受伤不会改变你的强度。这个强度只在已配对连接期间显示与下发，不会作为设备 A/B 读数上送服务器。

## 受伤 → 设备联动

配对成功后，`/dglab device on`（或菜单里的「下发到已配对设备」）会让每一次本机受伤都通过已经建好的 SOCKET V3 连接推给手机：

- **强度**：受伤提示上的数值就是目标通道强度（0–100），按照受伤事件加减、每秒回落到基础强度；每次下发都会对照 App 上报的当前值算「加/减多少」，因此手机上手动调过的强度不会被写成错值。
- **波形**：命中时把该来源选中的波形转成 V3 `pulse` 帧（8 字节 / 帧，每帧 100 ms）**追加到设备队列**，不打断正在播放的波形，按排队顺序依次播放。单次波形长度 = 18 条 × 脉冲帧数 × 100 ms（1/2/3 帧 → 1.8/3.6/5.4 秒）；B 通道使用与 A 相同的一组帧数据。
- **队列上限**：客户端自己记录已下发的帧数并按 100 ms/帧 递减，只允许排队 `最大排队 秒` 秒的波形（默认 6 秒 = 60 帧，够放一条 3 帧的最长波形）。队列放不下时，新来的命中/死亡事件**整条丢弃**（HUD 数值也不变），直到队列腾出空间；因此连续挨打时反馈会按顺序播放，而不是互相顶掉。
- **回落**：强度每秒向基础强度回落时同步下调；回到基础强度且开启「回落到基础强度后停止输出」时，发送 `clear` 并停止输出。
- **预览**：`/dglab feedback preview <波形>` 与菜单里的「预览当前波形」也会同时播放到设备。

只有你自己的受伤事件会触发输出；服务器脚本、其他玩家和命令都无法远程驱动设备。关闭「下发到已配对设备」、`/dglab disconnect`、修改通道/帧数或退出游戏时会清空设备队列并把通道归零。

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

本移植提供 Fabric 客户端和专用服务端入口，重写本机 V3 会话（读数上报与可选的波形/强度下发），迁移二维码与状态绘制，并添加中英文命令提示与图形化设置界面。模组在服务器内存中保存每位共享玩家的最新一组读数；配对码、手机地址及设备标识留在玩家电脑。

16 种预设的名称与简化曲线读取自下列固定版本，随包保留 18 列的画面缩略图，离线也可切换。导入脚本见 [tools/import_wave_previews.py](tools/import_wave_previews.py)：
https://github.com/dungeonlab-open/dglab-websocket-simple/blob/5cda0e8bdac35f61286430ec4d0c77947fcd4f4d/socket/DG_WAVES_V2_V3_simple.js

项目授权为 GNU AGPL v3，见 [LICENSE](LICENSE)。二维码编码使用 ZXing Core 3.5.3，见 [licenses/ZXing-Apache-2.0.txt](licenses/ZXing-Apache-2.0.txt)。图标取自同一上游版本。
