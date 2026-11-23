# Minecraft Java 26.3 Carpet 生电服与 DG-LAB

适用于 Minecraft Java 26.3、Fabric Loader 0.20.1、Fabric API 0.166.2 和 Java 25。此仓库含版本 `1.2.7+status.26.3.3` 的双端模组、可选的 DGLab Craft HUD 和 Windows 服务端启动源码。

| 目录 | 内容 |
| --- | --- |
| [mods/minecraft-dg-lab](mods/minecraft-dg-lab) | 本玩家受伤 HUD、16 个屏幕波形预设、只读 V3 配对和玩家主动开启的 A/B 读数同步。 |
| [mods/dglabcraft-hud](mods/dglabcraft-hud) | 可选客户端 HUD，显示受伤、低血量和环境提示。 |
| [server/Carpet-26.3](server/Carpet-26.3) | `start.bat`、默认服务端配置、Java 启动器源码、开服说明和第三方组件来源。 |
| [client](client) | 两个 Modrinth 客户端整合包的清单和构建脚本。 |

## 构建

准备 JDK 25、Python 3.9 或更新版本，并把 `JAVA_HOME` 指向 JDK。从仓库根目录运行以下命令。模组的构建脚本会按锁文件下载并校验依赖。

```sh
python mods/minecraft-dg-lab/build.py
python mods/dglabcraft-hud/build.py
python server/build_launcher.py
python client/build_packs.py
```

双端模组 JAR 生成在 `mods/minecraft-dg-lab/build/`，可选 HUD JAR 生成在 `mods/dglabcraft-hud/.build/`。启动器生成在 `build/server/tools/server-start.jar`。客户端整合包生成在 `build/client/`，用支持 `.mrpack` 的启动器导入。

已有 Windows 生电服可关服后替换 `mods` 下同名的双端模组 JAR，再通过原来的 `start.bat` 启动。手动组装服务器时，`start.bat` 需要目录内的 `runtime/bin/java.exe` 和 `tools/server-start.jar`，服务端还需要 Fabric 启动器、依赖库与运行模组。固定版本和来源见 [组件来源.txt](server/Carpet-26.3/tools/组件来源.txt)。

## 游戏内使用

- 用 `/dglab feedback` 查询和调整受伤 HUD，`/dglab feedback waves` 列出波形预览。
- 要配对并共享设备读数时，进服输入 `/dglab share on`，用和游戏电脑处于同一局域网的 DG-LAB 3.x App 扫码。服主可在控制台用 `dglabserver status 玩家名` 查询。玩家用 `/dglab share off` 停止共享。
- 查看完整命令与配置方式。[Minecraft-DG-LAB 说明](mods/minecraft-dg-lab/README.md)

两个移植的上游作者、版本和许可记录于各模组目录。Minecraft-DG-LAB 为 AGPL-3.0，DGLab Craft HUD 为 GPL-3.0。第三方组件分别适用各自的许可。[Minecraft-DG-LAB 许可](mods/minecraft-dg-lab/LICENSE) · [DGLab Craft HUD 许可](mods/dglabcraft-hud/LICENSE)
