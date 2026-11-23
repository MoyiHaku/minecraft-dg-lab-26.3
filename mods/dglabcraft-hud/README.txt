DGLab Craft HUD Edition · Minecraft Java 26.3 / Fabric
独立的画面反馈移植，版本 1.1.4+hud.26.3.1

上游作者 bilbillm / Lumoren
上游的 26.2-Fabric 分支，提交 04eb2249ce2c93b1bd4995a33d8cc78e1c1e5864
https://github.com/bilbillm/DGLab-Craft/tree/04eb2249ce2c93b1bd4995a33d8cc78e1c1e5864

2026-11-21 修改说明
HudPanel 的配色、波形柱绘制和四角定位来自上游的 DGLabCraftHUD、WaveformPreview 和 OverlayLayout。
26.3 移植改用新版键盘映射、Fabric HUD 绘制接口和本地生命值记录。受伤条和低血量心跳绘制在玩家屏幕上。
玩家可调整显示时机、位置和环境提示。此版本仅有画面反馈，不含 WebSocket、设备连接或电刺激输出。
模组源码与对应许可都在此目录。授权见 LICENSE (GPL-3.0)。

重新构建
准备 Python 3.9+ 和 JDK 25，将 JAVA_HOME 指向 JDK，在本目录运行 python build.py。
脚本从 Mojang、Fabric 和 Modrinth 下载固定的编译依赖，按 build-dependencies.json 校验 SHA-256。
生成的 .build/DGLabCraft-HUD-26.3-fabric.jar 可直接用于 26.3 Fabric 客户端。
游玩无需安装 Python 或编译工具。
