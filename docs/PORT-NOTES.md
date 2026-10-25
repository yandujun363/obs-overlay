# 26.3 移植技术笔记

这份文档解释**为什么**这一版和上游 1.21.4 的实现完全不同，以及关键的 26.3 API 事实（升级版本时用得上）。
流程类内容（怎么构建、怎么验证、常见任务）在 [`../AGENTS.md`](../AGENTS.md)。

---

## 1. 目标版本的事实

| 项 | 值 | 证据 |
|---|---|---|
| Minecraft | 26.3（`world_version 5023`） | `versions/<实例>/<实例>.json` 的 `clientVersion` |
| Java | 25 | 同上 `javaVersion.majorVersion` |
| Fabric Loader | 0.19.5（实例 json 里 pin） | 同上 `libraries` |
| Mixin | sponge-mixin 0.17.4 + mixin 0.8.7 | 同上 |
| 窗口/GL 创建 | **SDL**（`org.lwjgl.sdl`） | `com.mojang.renderpearl.backend.opengl.GlBackend#createWindow` |
| GL 版本 | **3.3 core / forward compatible** | `GlBackend#createWindow` 的 `SDL_GL_SetAttribute` |

### 26.x 起 jar 不再混淆

`net/minecraft/client/Minecraft.class`、`com/mojang/blaze3d/...` 直接就是官方名（mojmap），
`libraries/net/fabricmc/intermediary/` 只到 1.21.1 —— **没有 intermediary、没有 refmap**。
所以：

- 模组直接对着官方名编译，mixins 配置里不写 `refmap`，`compatibilityLevel` 用 `JAVA_25`
- 上游 1.21.4 用的 Yarn 名一个都不能用（见下表）

### 1.21.4 → 26.3 名字对照（本移植用到的部分）

| 1.21.4（Yarn） | 26.3（官方名） |
|---|---|
| `client.MinecraftClient` | `client.Minecraft` |
| `util.Identifier` | `resources.Identifier`（`Identifier.of` → `fromNamespaceAndPath`，`toTranslationKey` → `toLanguageKey`） |
| `text.Text` | `network.chat.Component` |
| `gui.DrawContext` | `gui.GuiGraphicsExtractor`（提取阶段）/ `gui.render.GuiRenderer`（渲染阶段） |
| `gui.hud.InGameHud` | `gui.Gui` + `gui.Hud` |
| `gui.hud.ChatHud` / `PlayerListHud` / `DebugHud` / `SubtitlesHud` | `gui.components.ChatComponent` / `PlayerTabOverlay` / `DebugScreenOverlay` / `Hud#extractSubtitleOverlay` |
| `gui.screen.Screen` / `...ingame.HandledScreen` | `gui.screens.Screen` / `gui.screens.inventory.AbstractContainerScreen` |
| `gui.screen.GameMenuScreen` | `gui.screens.PauseScreen` |
| `gui.screen.option.VideoOptionsScreen` | `gui.screens.options.VideoSettingsScreen`（`OptionsSubScreen#addOptions`，列表 `OptionsList#addBig`） |
| `render.GameRenderer` / `render.WorldRenderer` | `renderer.GameRenderer` / `renderer.LevelRenderer` |
| `render.RenderTickCounter` | `client.DeltaTracker` |
| `client.util.math.MatrixStack` | `com.mojang.blaze3d.vertex.PoseStack` |
| `registry.Registries` / `SCREEN_HANDLER` | `core.registries.BuiltInRegistries` / `BuiltInRegistries.MENU`（`getKey`/`containsKey`/`keySet`） |
| `gui.option.GameOptions` | `client.Options` |
| `MinecraftClient#setScreen` | `Minecraft#setScreenAndShow` |
| `client.gl.Framebuffer` / `SimpleFramebuffer` | `com.mojang.blaze3d.pipeline.{RenderTarget,TextureTarget,MainTarget}` |
| `GlStateManager` / `RenderSystem.getFramebuffer()` | **不存在** |
| `Tessellator/BufferBuilder/BufferRenderer/VertexConsumerProvider` | `com.mojang.blaze3d.vertex.*` + `renderer.{SubmitNodeCollector,StagedVertexBuffer}` |
| `toast.SystemToast`（`Type`） | `gui.components.toasts.SystemToast`（`SystemToastId`） |

---

## 2. 26.3 的渲染架构（和 1.21.4 的根本冲突）

### 2.1 后端抽象换成了 renderpearl

`com.mojang.renderpearl`（249 个类）分 `api / frontend / backend.{opengl,vulkan}`：

- `GpuDevice`、`GpuTexture`、`GpuSurface`、`CommandEncoder`、`RenderPass`、`RenderPipeline`
- 混合/深度/剔除是 **pipeline 的一部分**（`api/pipeline/{BlendFunction,DepthStencilState,...}`），
  没有 `GlStateManager`，没有全局 GL 状态可以随手改
- `PreferredGraphicsApi.DEFAULT` = **先 OpenGL，失败再 Vulkan**（`getBackendsToTry` 字节码）

### 2.2 GUI 变成两段式

```
Minecraft#renderFrame
  └─ GameRenderer#extract      → Gui#extractRenderState → Hud#extractXxx → GuiRenderState（纯数据）
  └─ GameRenderer#render
       ├─ 清主目标 → 世界 → 后处理
       ├─ GuiRenderer#render   → prepare()（排序/合批/prepareText/prepareItemElements）
       │                        → draw()（executeDrawRange 到某个 RenderTarget）
       └─ windowSurface.blitFromTexture(主目标)   ← 搬到 swapchain
  └─ encoder.submit()
  └─ windowSurface.present()   = SDL_GL_SwapWindow
```

关键点：**`executeDrawRange(label, RenderTarget, transforms, start, end)` 的目标是参数**。
引擎自己的界面模糊就是这么做的（`firstDrawIndexAfterBlur` 把 draw 列表切成两段，中间插一次
`processBlurEffect()`）。这就是本移植能"把某几段 GUI 画到别的 target"的支点。

### 2.3 为什么不能沿用旧的 FBO 方案

| 1.21.4 的做法 | 26.3 为什么不行 |
|---|---|
| `GlStateManager._glBindFramebuffer` 中途切 FBO | 类不存在；渲染是"记录 pass"的，pass 的目标在创建时确定 |
| `DrawContext.draw()` 强制刷缓冲 | `DrawContext` 不存在；合批发生在 `GuiRenderer#prepare` |
| 每个渲染器 HEAD/RETURN 包 `beginDraw/endDraw` | 世界渲染改成了 submit 模式，画到哪个 pass 由框架决定（这也是 3D 部分被砍的原因） |
| `SimpleFramebuffer` + `ShaderLoader` + `Tessellator` 画 quad | 全部不存在；改用 `TextureTarget` + 原生 GL |

---

## 3. 本移植的实现

### 3.1 打标记（extract 阶段）

- `OverlayComponentStack`：一个允许 `null` 的栈 + `IdentityHashMap<元素, 组件 id>`
- `HudMixin` / `ChatScreenMixin` / `ChatComponentMixin` / `ScreenMixin` 在提取方法前后压/弹
- `GuiRenderStateMixin` 在 `addGuiElement / addText / addItem / addBlitToCurrentLayer /
  addGlyphToCurrentLayer / addPicturesInPictureState` 时按当前栈顶给元素打标记
- 文字/物品的几何是在 `prepare()` 阶段才生成的（不在 extract 阶段），所以
  `GuiRendererMixin` 用 `@WrapOperation` 包住 `forEachText / forEachItem / forEachPictureInPicture`
  的 consumer，把标签临时压回去，glyph/blit 才能继承正确的组件

`DROP` 标记 = 完全不画（auto-hide：开界面时组件直接消失，和上游一致）。

### 3.2 断批 + 分段绘制

`GuiRenderer#addElementToMesh` HEAD：元素归属和上一批不同 → `previousDraw = null`，强制新开一批。
这样**一个 draw 批次永远不会同时包含隐藏与可见内容**（安全红线）。

`GuiRenderer#draw` 被整体重写（HEAD cancel）：

1. 复刻 vanilla 的 prologue：`guiProjection.setupOrtho(...)`、`RenderSystem.setProjectionMatrix(...)`、
   `DynamicTransforms` 矩阵、`firstDrawIndexAfterBlur` 的模糊边界（`clearDepthTexture` + `processBlurEffect`）
2. 扫描 draw 列表，按"归属相同且不跨模糊边界"切段
3. 每段调用 `executeDrawRange`，目标是主 target 或叠加 target；`DROP` 段直接跳过

### 3.3 合成（原生 swap 钩子）

`NativeHook` 用 MinHook 钩 `opengl32!wglSwapBuffers`（OBS 的 game capture 钩的就是它）：

```
swap 调用链（我们的钩子装在游戏启动时，OBS 的钩子启动采集时才装 → MinHook 前插 → OBS 先跑）
  OBS detour：拷走后缓冲（干净帧）
  → 我们的 detour：合成叠加层
  → 真正的 wglSwapBuffers
```

**只钩这一个入口**：钩更早的入口（`gdi32!SwapBuffers`、`SDL_GL_SwapWindow`）会画在 OBS 拷帧之前 = 泄密。

合成用原生 GL 3.3 core（`GlCompositor`）：自建 shader + VAO + VBO，纹理 id 来自
`((GlTexture) target.getColorTexture()).glId()`。进入前保存、退出后恢复所有被改动的 GL 状态
（renderpearl 有自己的状态缓存）。采样前必须 `glBindSampler(0,0)` 且 `MIN_FILTER=NEAREST`，
否则单 mip 纹理 sampling incomplete → 全黑。

### 3.4 安全回退链

```
isActive() = hook 已安装 && hook 已至少触发一次 && 合成器未标记 broken
```

任一条件不满足 → 不分离内容 → 一切按原样渲染（HUD 正常显示、什么都不隐藏）。
`GlCompositor` 在初始化失败或拿不到 GL 纹理时把自己标记为 `broken`。

这比"隐藏失败但 HUD 消失"要安全得多，是刻意设计。

---

## 4. 已移除 / 未移植

| 东西 | 原因 |
|---|---|
| 世界内内容隐藏（告示牌/箱子/地图/名牌/旗帜/信标） | 26.3 世界渲染是 submit 模式，隐藏世界内内容需要"主 pass 跳过 + 拷深度 + 第二遍渲染"，代价远超收益；仓库主人决定砍掉 |
| Architectury / NeoForge | 26.3 生态无 Architectury；测试实例是 Fabric |
| ImmediatelyFast 兼容 | 26.3 已无立即模式/batching，其 API 类不存在 |
| `DrawContext.draw()`、`GlStateManager` 相关 mixin | 对应 API 不存在 |
| Gradle / Loom | 未验证 26.3 的 loom 用法；javac 直编已满足需求 |

---

## 5. 反编译 26.3 客户端（写/改 mixin 时的常用手段）

```bash
# 一次：下载 Vineflower
curl -L -o /tmp/vineflower.jar https://repo1.maven.org/maven2/org/vineflower/vineflower/1.11.1/vineflower-1.11.1.jar

# 单个类（client-26.3 是 tools/fetch-deps.sh 解出来的类目录）
java -jar /tmp/vineflower.jar -dgs=1 libs/client-26.3/net/minecraft/client/gui/render/GuiRenderer.class /tmp/out

# 只 dump 签名（更快）
javap -p -s -classpath libs/client-26.3 net.minecraft.client.gui.render.GuiRenderer
```

## 6. 升级到 26.x 其它版本时的检查清单

1. `javap` 确认这些**注入点**还在（`verify-mixins.py` 会做，但要看语义是否变化）：
   `Gui#extractRenderState`、`Hud#extract*`、`ChatScreen#extractRenderState`、
   `ChatComponent#extractRenderState`、`Screen#extractRenderStateWithTooltipAndSubtitles`、
   `Screen#extractBlurredBackground`、`GuiRenderState#add*`、
   `GuiRenderer#{prepare,addElementToMesh,prepareText,prepareItemElements,preparePictureInPicture,draw,executeDrawRange}`、
   `GameRenderer#render`、`VideoSettingsScreen#addOptions`
2. 对照 `GuiRenderer#draw` 的新实现同步 `GuiRendererMixin#obs$draw`（prologue 三处最容易漏）
3. 确认 `GuiRenderer#executeDrawRange` 的签名/语义没变（这是整个方案的地基）
4. 确认 `GlTexture#glId()` 还在、`TextureTarget` 构造参数没变
5. 确认 `renderpearl` 仍是 GL 3.3 core（改变 GL 版本会影响 `GlCompositor` 的 shader）
6. 跑一次真实客户端（Windows + OBS），看日志三条关键行
