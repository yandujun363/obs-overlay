# AGENTS.md — 接手须知（给下一个 agent）

> **这个分支（`26.3`）完全由 LLM 开发**，面向维护者自身的使用需求。
> 目标不是"做一个通用的、可长期维护的 mod"，而是"在 26.3 + Fabric + Windows + OBS 上跑起来"。
> 改动前先读 §1（边界）和 §8（红线）。

---

## 1. 项目边界

| 是 | 不是 |
|---|---|
| Minecraft **26.3** / Fabric / 单模块 | 多加载器（Architectury/NeoForge 已删） |
| 只隐藏 **HUD + 界面** | 世界内内容（告示牌/箱子/地图/名牌/旗帜/信标）—— **已明确砍掉，不要加回来** |
| Windows + **OpenGL** 后端 | Linux/macOS、Vulkan 后端 |
| `javac` 直编（26.3 无混淆、无 intermediary） | Gradle / Loom（本分支没有，且 26.3 的 loom 用法未验证） |
| 上游 `1.21.4` 分支是独立存在的历史 | 不要把上游的 Architectury 结构搬回本分支 |

`upstream` remote 指向上游作者的同名仓库（只读参考，别推）；`origin` 是当前 fork。

---

## 2. 运行前提（不满足 = 完全不生效，别误判成 bug）

1. **图形 API 必须是 OpenGL**（`options.txt` 里的 `preferredGraphicsBackend:"opengl"`）。
   Vulkan 下 `GpuTexture` 不是 `GlTexture`、也没有 `wglSwapBuffers`，模组会自动停用（HUD 正常显示）。
   `PreferredGraphicsApi.DEFAULT` 是"先 GL 再 Vulkan"，所以除非 GL 起不来，默认就是 GL。
2. **Windows only**：MinHook(dll) + JNA + `opengl32!wglSwapBuffers`。
3. **先开游戏，后开 OBS 采集**，且 OBS 必须用**游戏源 / Game Capture（图形钩子）**。
   原理见 §4；顺序反了 OBS 就会拍到叠加层。
4. 你需要一个 26.3 版本目录作为测试/取依赖目标，下面用 `<instance>` 表示它的路径
   （例如 `.minecraft/versions/<二十六点三版本名>/`）。

---

## 3. 构建 / 验证 / 部署

```bash
# 0) 只做一次：从本机安装取编译期依赖，写入 libs/（已 gitignore）
tools/fetch-deps.sh "<instance>"

# 1) 编译打包 → build/obs_overlay-<version>.jar
./build-local.sh

# 2) 【硬门槛】校验每个 mixin 注入点在 26.3 里真实存在
python3 verify-mixins.py

# 3) 部署到测试实例（记得删旧版本 jar）
rm -f "<instance>/mods/"obs_overlay-*.jar
cp build/obs_overlay-*.jar "<instance>/mods/"
```

要点：

- **`verify-mixins.py` 必须 0 failure。** 它用 `javap` 把 `@Inject/@WrapOperation` 的
  `method=`/`target=`、`@Invoker/@Accessor`、`@Shadow` 字段逐个对到解包出来的 26.3 客户端上。
  **编译通过 ≠ 注入点存在**；`defaultRequire: 1` 意味着注入点找不到会直接崩在启动。
- 版本号只有一个来源：`src/main/resources/fabric.mod.json` 的 `version`（`build-local.sh` 会读它）。
- `libs/` 里的依赖版本是从测试实例的 `<instance>.json` **按版本号 pin** 的（loader/mixin/jna/lwjgl/...），
  所以编译面与运行时一致。换实例后重跑 `fetch-deps.sh`。
- 如果开发环境里跑不了游戏（很常见：容器、无 GUI、跨系统编辑），**任何"已修复"的结论都要有
  真实客户端的日志/现象支撑**，不要凭代码自信断言。

---

## 4. 实现原理速览（为什么和 1.21.4 完全不同）

26.3 把整条渲染栈换成了 `renderpearl`（`com.mojang.renderpearl`，OpenGL/Vulkan 双后端）：

- `GlStateManager`、`net.minecraft.client.gl.*`（Framebuffer/ShaderLoader）、
  `Tessellator/BufferBuilder/VertexConsumerProvider` **全部不存在**
- 混合/深度等状态变成 `RenderPipeline` 的一部分，没有全局状态可改
- GUI 变成两段式：`Gui#extractRenderState` 把内容写进 `GuiRenderState` →
  `GuiRenderer` 排序合批后统一 `draw()`
- `GuiRenderer#executeDrawRange(label, RenderTarget, transforms, start, end)` 的**目标 target 是参数**
  （引擎自己的模糊效果就是这么做的）

所以本移植的做法是：

```
extract 阶段：给元素打标记（OverlayComponentStack + IdentityHashMap）
        ↓
GuiRenderer#prepare：组件边界强制断批（previousDraw = null），记录每个 draw 归属
        ↓
GuiRenderer#draw：按归属把 draw 段画进主目标 or 自建 TextureTarget
        ↓
原生 swap 钩子（wglSwapBuffers）：OBS 拷完之后，用原生 GL 3.3 把叠加层贴到后缓冲
```

**安全回退链**（重要，任何一步失败都退回"什么都不隐藏"，绝不让 HUD 从屏幕上消失）：

`hook 未装上` → `isActive()=false` → 不分离；
`hook 装上但从未 fired` → 不分离；
`合成分解失败 / 纹理不是 GL 纹理` → `GlCompositor.isBroken()=true` → 不分离。

---

## 5. 代码地图

```
src/main/java/me/zziger/obsoverlay/
├── OBSOverlay.java                  入口、懒加载渲染器、Vulkan 警告
├── OBSOverlayFabricClient.java      ClientModInitializer
├── OBSOverlayConfig.java            JSON 配置 + isScreenOverlayed + 保存时清理陈旧键
├── OBSOverlayConfigScreen.java      cloth-config 设置界面（三页：HUD / 整屏 / 高级）
├── ModMenuIntegration.java          ModMenu entrypoint
├── OverlayComponentStack.java       标记栈 + 元素→组件 的 IdentityHashMap（每帧 reset）
├── OverlayRenderer.java             每帧 beginExtraction/beginFrame、active 判定、诊断 toast
├── OverlayTarget.java               离屏 TextureTarget（RGBA8 + D32_FLOAT）
├── OverlayFramebufferType.java      API 兼容用（只有 NORMAL 在用）
├── OverlayUtils.java                toast
├── api/                             IOverlayAPI（beginDraw/endDraw 现在是"打标记"）+ Dummy/Normal 实现
├── component/                       组件接口、注册表、默认组件表
│   └── type/                        DefaultOverlayComponent / HUDOverlayComponent
├── mixin/
│   ├── GuiMixin.java                Gui#extractRenderState HEAD → 重置标记
│   ├── HudMixin.java                8 组 Hud#extractXxx 的压/弹标记 + 测试图标
│   ├── SubtitleOverlayMixin.java    字幕（挂真正绘制入口；Hud 那边是延迟绘制，挂不上）
│   ├── ChatScreenMixin.java         整个聊天界面 = chat_bar
│   ├── ChatComponentMixin.java      聊天消息重新打 chat（HUD 与聊天界面两条路径都覆盖）
│   ├── ScreenMixin.java             整屏隐藏 + 被隐藏界面取消背景模糊
│   ├── GuiRenderStateMixin.java     addGuiElement/addText/addItem/addBlit/addGlyph/addPiP 打标记
│   ├── GuiRendererMixin.java        断批 + 重写 draw()（分段选目标）
│   ├── GameRendererMixin.java       render HEAD → 每帧清空叠加目标、帧号 +1
│   └── VideoSettingsScreenMixin.java 视频设置里加按钮
└── nativehook/
    ├── NativeHook.java              MinHook + JNA，钩 opengl32!wglSwapBuffers；帧号去重
    ├── GlCompositor.java            原生 GL 3.3 画 quad；完整保存/恢复 GL 状态
    ├── MinHook.java / Kernel32.java JNA 接口
```

资源：`fabric.mod.json`、`obs_overlay.mixins.json`（`compatibilityLevel: JAVA_25`, `defaultRequire: 1`）、
`assets/obs_overlay/{icon.png,lang/*}`（7 语言，键集必须一致）、`lib/MinHook.{x64,x86}.dll`。

文档（改行为/加开关时同步，别让它们和代码打架）：

- `README.md` —— 面向使用者：功能表、安装前提、设置入口
- `docs/CONFIG.md` —— **配置手册**：三层开关（组件叠加 / auto-hide / 整屏）的语义、每个组件何时出现、
  可抄的 JSON 配方、排障流程
- `docs/UPSTREAM-DIFF.md` —— 上游 1.21.4 逐项对照（17 个组件里哪些还原了、注入点映射、为什么没名字牌）
- `docs/PORT-NOTES.md` —— 26.3 渲染架构与移植原理
- `docs/API.md` —— 第三方 API

---

## 6. 常见任务

### 6.1 新增一个 HUD 组件

1. `AllDefaultOverlayComponents` 里 `new HUDOverlayComponent(OBSOverlay.id("<id>"), 默认是否隐藏, 是否可以 autoHide)`
2. 用 `javap -p <目标类>` 找到它对应的提取方法（26.3 里通常是 `Hud#extractXxx(GuiGraphicsExtractor, DeltaTracker)`）
3. 在 `HudMixin` 加一对 `@Inject`（HEAD push / RETURN pop），**方法字符串必须带完整描述符**
4. 语言键 `obs_overlay.component.obs_overlay.<id>` 和 `.tooltip`，**7 个语言文件都要加**（键集必须一致）
5. `python3 verify-mixins.py` → 0 failure
6. 若该组件在多个路径被提取（例如聊天），参考 `ChatComponentMixin` 用嵌套标记覆盖所有路径

### 6.2 新增一个可隐藏的整屏

`OverlayComponentRegistry.hideableScreens.put("<id>", Clazz.class)` + 语言键 `obs_overlay.screen.<id>`。
判定逻辑在 `OBSOverlayConfig#isScreenOverlayed`。

### 6.3 加一个配置项

`OBSOverlayConfig` 加字段（Gson 默认值即可）→ 在 `OBSOverlayConfigScreen` 加 entry（cloth-config，
别忘 `setSaveConsumer`）→ 需要时在 `save()` 里做清理/迁移。

设置界面现在是**三页**，新开关放哪页按语义决定：

| 页 | 类别键 | 放什么 |
|---|---|---|
| HUD 元素 | `obs_overlay.config.category.hud` | 逐组件的叠加/自动隐藏 |
| 整屏界面 | `obs_overlay.config.category.screens` | 与 Screen 相关的开关 |
| 高级 / 诊断 | `obs_overlay.config.category.advanced` | 诊断、合成微调、只影响"怎么画"而不是"画什么"的开关 |

同时更新 `docs/CONFIG.md`（配置手册是面向用户的标准答案，别让文档和代码打架）；新增语言键时
7 个语言文件都要有（键集必须一致，可用 §3 的构建 + 下面这段检查）。

```bash
python3 - <<'EOF'
import json,glob
s={f:set(json.load(open(f))) for f in glob.glob('src/main/resources/assets/obs_overlay/lang/*.json')}
base=s['src/main/resources/assets/obs_overlay/lang/en_us.json']
print(all(v==base for v in s.values()), sorted(base-set().union(*s.values())))
EOF
```

### 6.4 改合成 / 颜色不对

`nativehook/GlCompositor`：

- GL 3.3 **core**（没有 glBegin），只能 VAO + shader
- 采样前必须 `glBindSampler(0, 0)` 并设 `MIN_FILTER=NEAREST`：默认 min filter 是 mipmap，
  单 mip 纹理会 sampling incomplete → **全黑**
- 改过的每一项 GL 状态都要**原样恢复**（renderpearl 有自己的状态缓存，脏了下一帧就花）
- 预乘 alpha 走配置 `premultipliedAlpha`（默认 true）；颜色发灰/发亮就切它

---

## 7. 踩过的坑（现象 → 原因 → 对策）

| 现象 | 原因 / 对策 |
|---|---|
| 隐藏的 HUD 直接从屏幕上消失 | 分离了但没有合成。先看日志有没有 `present hook fired` / `GL compositor ready`；安全回退链见 §4 |
| 叠加层全黑 | 纹理采样 incomplete（mipmap min filter / 绑定了 sampler object）。见 §6.4 |
| 隐藏内容发灰/发亮 | 预乘 alpha 不对，切 `premultipliedAlpha` |
| 装了钩子但日志**没有** `present hook fired` | 26.3 用 SDL，`GlSurface.present()` = `SDL_GL_SwapWindow`；若没有走到 `opengl32!wglSwapBuffers`，**不要**直接改钩 `gdi32!SwapBuffers` 或 `SDL_GL_SwapWindow`：那会画在 OBS 抓帧之前 = 泄密。要先确认 OBS 钩的是哪个入口 |
| 第一帧不隐藏 | 设计如此：钩子 fired 过一次后才开始分离 |
| 聊天记录/输入框互串 | 组件边界没断开。`GuiRendererMixin#obs$beforeElement` 负责断批，别删 |
| 界面上看不到某些 HUD，或 OBS 里漏了某块 | 大量元素被合进同一个 draw。检查断批逻辑是否被 upsteam 的排序改动影响 |
| 启动直接崩在 mixin | 注入点描述符变了。跑 `verify-mixins.py` |
| 视频设置界面按钮不见了 | 上游改动了 `VideoSettingsScreen`/`OptionsSubScreen#addOptions`/`OptionsList#addBig`，同步 `VideoSettingsScreenMixin` |
| 改了 `config/obs_overlay.json` 没反应 | 手改要重启游戏（配置只在客户端初始化时读一次）；设置界面里的开关才是即时的 |
| 设置界面出现 `obs_overlay.config.xxx` 这种裸键名 | 语言文件缺键；7 个语言键集必须一致（见 §6.3 的检查脚本） |
| 某个组件"开了但没效果" | 先确认标记挂的是**真正绘制**的方法，不是"延迟/转发"的方法。典型：26.3 的 `Hud#extractSubtitleOverlay` 只存 Runnable（`deferredSubtitles`），真正绘制在 `Gui#extractRenderState` → `Hud#extractDeferredSubtitles` → `SubtitleOverlay#extractRenderState`，所以字幕要挂 `SubtitleOverlayMixin`。用 `javap -c` 看方法体里有没有 `putfield ...Runnable` / 只调 `setXxx` 不调 `add*` |

---

## 8. 红线（不要做的事）

1. **不要提交** `libs/`、`build/`、解包出来的客户端类、任何 jar 二进制。
2. **不要**在没有真实客户端（Windows + OBS）验证的情况下声称"修好了"。
3. **不要**把世界内内容隐藏加回来。
4. **不要**在没有实测的情况下改变"先游戏后 OBS"的时序假设，也不要把合成挪到 Java 层（必须在原生 swap 钩子里）。
5. **不要**改动 `GuiRendererMixin` 里重写的 `draw()` 而不对照当前版本的 vanilla 实现
   （模糊边界 `firstDrawIndexAfterBlur`、projection、DynamicTransforms 三处最容易漏）。
6. **不要** force push `26.3`；`1.21.4` 分支和 `upstream` remote 只读。
7. **不要**把 `fetch-deps.sh` 的版本 pin 改成"随便拿最新"——编译面要跟运行时一致。

---

## 9. 测试矩阵（每次改动至少过一遍关键项）

- 组件：F3、计分板、Tab、聊天、聊天输入（含 `/` 补全）、标题/动作栏、药水效果、主 HUD、字幕
- 界面隐藏：生存/创造物品栏、暂停菜单、命令方块、自定义容器 id、隐藏所有屏幕
- 配置界面：三页都能打开/保存/重置；`hideAllScreens` 打开时单项开关变灰；改完立刻生效（不用重启）
- 边界：全屏切换、GUI 缩放变化、F1 隐藏 HUD、退出世界回主菜单、Iris + Sodium 组合、立即重开界面
- 判定标准永远是一句话：**屏幕上有、OBS 里没有**
- 日志必须出现：`native present hook installed` → `present hook fired` → `GL compositor ready`

---

## 10. 已知待办（可选）

1. Gradle + Loom 正式工程（26.3 的配置方式未验证，别当成"应该很简单"）
2. `docs/API.md` 里承诺的第三方 API 还没有真实使用者验证
3. Iris 的 shader 管线对 3D 部分的影响（本分支已无 3D，可忽略）
4. 若上游 1.21.4 分支新增了 HUD 组件，可按 §6.1 同步过来
5. **名字牌（`name_tag` / `name_tag_sneaking`）**：上游的 HUD 分类里有这一项，但它画在世界 3D 阶段，
   要"屏幕上有、OBS 里没有"必须走"世界内容单独一遍渲染 + 拷深度"的管线（见 `docs/UPSTREAM-DIFF.md` §5）。
   在那条管线打通之前不要做；**更不要**用"取消 `submitNameTag`"糊过去——那会让名字牌在屏幕上一起消失，
   语义和上游不同，等于偷偷改了功能。
6. 测试图标目前画在 `Hud#extractRenderState` 末尾（开界面时在界面下面）。若想让它在界面之上也可见，
   改到 `Gui#extractRenderState` 的末尾（需要 `@Local` 取 `GuiGraphicsExtractor`，`verify-mixins.py` 不校验 sugar，
   改完必须在真实客户端确认）。
