# OBS Overlay — Minecraft 26.3 (Fabric)

> ## ⚠️ 关于这个分支
>
> - `26.3` 分支**完全由 LLM 开发**，是仓库主人为自己的需求折腾出来的分支（"自己想用"优先于"通用/稳定"）。
> - **不承诺**通用性、不承诺跟进上游、不承诺长期维护；上游合并/重构后本分支可能直接失效。
> - 上游作者的多加载器版本在 `1.21.4` 分支（`upstream/1.21.4`），本分支不是它的替代品。
> - 世界内内容隐藏（告示牌、箱子、地图、名牌、旗帜、信标）在本分支**已移除**，不要再期待它。
> - 只支持 **Fabric + Windows + OpenGL 后端**。Vulkan 后端下模组会自动停用（HUD 正常显示，什么都不隐藏）。

把 HUD / 界面元素渲染到一个叠加层里，从 OBS 的游戏捕捉中隐藏它们。

原理：需要隐藏的内容在主渲染目标里**不画**，而是画进独立的 `TextureTarget`；在
`wglSwapBuffers` 的钩子里（此时 OBS 已经拷走了干净的一帧）再把叠加层合成到窗口后缓冲上。
所以**屏幕上看得见、OBS 里看不见**。

---

## 功能

| 类别 | 内容 |
|---|---|
| HUD（10 项） | 调试菜单(F3)、聊天、聊天输入栏（含 `/` 补全）、玩家列表(Tab)、字幕、计分板、动作栏、标题/副标题、药水效果、主 HUD（快捷栏/血量/饥饿/护甲/经验） |
| 整屏隐藏 | 生存物品栏、创造物品栏、暂停菜单、命令方块、任意容器界面（按菜单 id）、隐藏所有界面 |
| 其它 | 被隐藏的界面不再触发背景模糊、测试图标（屏幕上必有/OBS 必无）、失败时弹提示、7 种语言、ModMenu 集成 |

**未包含**：世界内内容（见上面的警告）。

## 安装

1. Fabric Loader **≥ 0.19**、Minecraft **26.3**、Java **25**
2. 必需依赖：[Cloth Config](https://modrinth.com/mod/cloth-config)（设置界面）
3. 可选：[Mod Menu](https://modrinth.com/mod/modmenu)（模组列表里直接打开设置）
4. 把构建出的 jar 丢进 `mods/`

### 使用前提（不满足就完全不会生效）

1. **图形 API 必须是 OpenGL**（选项 → 视频设置 → 图形 API）。Vulkan 下没有 GL 纹理句柄，也没有 `wglSwapBuffers`。
2. **先开游戏，后开 OBS 采集**。OBS 在开始采集时才装钩子，MinHook 后装的钩子排在链首 ——
   顺序对了 OBS 才会拷到"干净"的帧。
3. OBS 必须用**游戏源 / Game Capture（图形钩子）**，不能用显示器采集或窗口采集。
4. 仅 Windows。

## 设置

- `选项 → 视频设置 → OBS Overlay 设置`
- 或 ModMenu 里本模组的配置按钮
- 也可直接编辑 `config/obs_overlay.json`（改完要重启游戏）

默认只隐藏 F3 调试菜单（和上游一致）。打开界面时是否顺带完全隐藏某个组件，在
"auto hide" 子分类里控制。

## 构建

本分支**不使用 Gradle**：26.3 的客户端 jar 未混淆、Fabric 在 26.x 也没有 intermediary/remap 层，
所以直接对着解包出来的客户端类编译即可（工具链只有一个 javac）。

```bash
# 1) 从本机安装里取编译期依赖（会写 libs/，已被 gitignore）
tools/fetch-deps.sh "/path/to/.minecraft/versions/<26.3 instance>"

# 2) 编译打包
./build-local.sh            # → build/obs_overlay-<version>.jar

# 3) 校验所有 mixin 注入点在 26.3 里真实存在（改过 mixin 必跑）
python3 verify-mixins.py
```

## 排障

| 现象 | 处理 |
|---|---|
| 什么都不隐藏，右上角提示 "OBS Overlay disabled" | 看日志。多半是图形 API 还是 Vulkan，或钩子没装上 |
| 日志有 `present hook installed` 但**没有** `present hook fired` | SDL 的 swap 没走到 `opengl32!wglSwapBuffers`，见 `docs/PORT-NOTES.md` 的"风险"一节 |
| 隐藏内容颜色发灰/发亮 | 设置里关掉"预乘 Alpha"再试 |
| 第一帧不隐藏 | 设计如此：要先确认钩子被调用过一次，才会开始分离内容 |

日志关键字：`[obs_overlay]`。

## 文档

- [`AGENTS.md`](AGENTS.md) —— **接手前必读**：构建/验证/部署流程、代码地图、常见改动、踩过的坑
- [`docs/PORT-NOTES.md`](docs/PORT-NOTES.md) —— 26.3 渲染架构与实现原理（为什么和 1.21.4 完全不同）
- [`docs/API.md`](docs/API.md) —— 给第三方模组的 API

## 许可

MIT，见 [`LICENSE`](LICENSE)。上游项目：[zziger/obs-overlay](https://github.com/zziger/obs-overlay)。
