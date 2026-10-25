# 上游 `1.21.4` ↔ 当前 `26.3` 差异对照

这份文档回答一个问题：**上游能隐藏的东西，这个分支到底还原了多少？**
（流程/构建在 [`../AGENTS.md`](../AGENTS.md)，渲染原理在 [`PORT-NOTES.md`](PORT-NOTES.md)，
配置怎么用见 [`CONFIG.md`](CONFIG.md)。）

上游基准：`upstream/1.21.4`（commit `f007917`，Architectury 三模块 + Gradle）。

---

## 0. 一句话结论

| 类别 | 上游 | 当前 | 状态 |
|---|---|---|---|
| HUD / 界面元素（纯 2D） | 10 项 | 10 项 | **1:1 还原** |
| 名字牌（`name_tag` / `name_tag_sneaking`） | 2 项 | 0 项 | 未还原（世界 3D 阶段，见 §5） |
| 世界内内容（告示牌/箱子/地图/旗帜/信标） | 5 项 | 0 项 | 未还原（**项目边界，永不加回**） |
| 整屏隐藏（4 个内置 + 自定义容器 + 隐藏全部） | 有 | 有 | **1:1 还原** |
| 测试图标 / 失败提示 / 7 语言 / ModMenu | 有 | 有 | 还原 |
| cloth-config 设置界面 | 有（单页） | 有（**三个分类页**） | 还原并重组（见 `CONFIG.md`） |

也就是说：**"UI 隐藏"这一侧已经和上游对齐**；缺的只有 7 项世界内（3D）组件，
其中 5 项是明确砍掉的边界，2 项是名字牌（原因见 §5）。

---

## 1. 组件对照（上游 17 项）

上游 `AllDefaultOverlayComponents` 一共 17 个组件。`HUDOverlayComponent` 走普通 2D 通道，
`InGameOverlayComponent` 走带深度的通道（`OverlayFramebufferType.DEPTH`）——后者就是本分支
说的"3D / 世界内内容"。

| # | 上游 id | 上游类型 | 当前 | 说明 |
|---|---|---|---|---|
| 1 | `debug_menu` | HUD | ✅ | F3 调试菜单 |
| 2 | `chat` | HUD | ✅ | 聊天记录（不含输入栏） |
| 3 | `chat_bar` | HUD | ✅ | 聊天输入栏 + 命令补全 |
| 4 | `player_list` | HUD | ✅ | Tab 玩家列表 |
| 5 | `subtitles` | HUD | ✅ | 右下角音频字幕 |
| 6 | `scoreboards` | HUD | ✅ | 右侧计分板 |
| 7 | `actionbar` | HUD | ✅ | 快捷栏上方 `/title actionbar` |
| 8 | `title_subtitle` | HUD | ✅ | 屏幕中央 `/title` |
| 9 | `effects` | HUD | ✅ | 右上角药水效果 |
| 10 | `main_hud` | HUD | ✅ | 快捷栏/血量/饱食度/护甲/经验/切换物品名 |
| 11 | `name_tag` | HUD | ❌ | 名字牌：**画在世界渲染阶段**，属 3D |
| 12 | `name_tag_sneaking` | InGame（DEPTH） | ❌ | 潜行玩家名字牌，同上 |
| 13 | `sign_text` | InGame（DEPTH） | ❌ | 告示牌文字（边界：不加回） |
| 14 | `chest` | InGame（DEPTH） | ❌ | 箱子（边界：不加回） |
| 15 | `item_frame_map` | InGame（DEPTH） | ❌ | 展示框地图（边界：不加回） |
| 16 | `banner_canvas` | InGame（DEPTH） | ❌ | 旗帜图案（边界：不加回） |
| 17 | `beacon_beam` | InGame（DEPTH） | ❌ | 信标光柱（边界：不加回） |

`name_tag` 上游声明成 `HUDOverlayComponent`（普通通道），但注入点是
`EntityRenderer#renderLabelIfPresent`，也就是**世界 3D 阶段**的文字，
所以本分支把它和 `name_tag_sneaking` 一起归入 3D，没有移植。

---

## 2. 整屏隐藏对照

| 能力 | 上游 | 当前 |
|---|---|---|
| 生存物品栏 `inventory` | ✅ `InventoryScreen` | ✅ 同名（`hideableScreens`，顺序固定） |
| 创造物品栏 `creative_inventory` | ✅ | ✅ `CreativeModeInventoryScreen` |
| 暂停菜单 `pause_menu` | ✅ `GameMenuScreen` | ✅ `PauseScreen` |
| 命令方块 `command_block` | ✅ | ✅ `CommandBlockEditScreen` |
| 按菜单 id 隐藏任意容器 | ✅ `HandledScreen` + `Registries.SCREEN_HANDLER` | ✅ `AbstractContainerScreen` + `BuiltInRegistries.MENU` |
| 隐藏所有界面 `hideAllScreens` | ✅（要求 `world != null`） | ✅ 相同语义 |
| 被隐藏界面的背景模糊 | ✅ 取消 `Screen#applyBlur` | ✅ 取消 `Screen#extractBlurredBackground` |
| 不触发 auto-hide 的界面 | ✅ 只有 `ChatScreen` | ✅ 相同 |

---

## 3. 注入点对照（搬代码时最有用的一张表）

上游 Yarn 名 → 26.3 官方名，映射依据见 `PORT-NOTES.md` §1。

| 上游 mixin | 上游注入的方法 | 当前 mixin | 当前注入的方法 |
|---|---|---|---|
| `components/DebugHudMixin` | `DebugHud#render` | `HudMixin` | `Hud#extractDebugOverlay` |
| `components/ChatHudMixin` | `ChatHud#render` | `HudMixin` + `ChatComponentMixin` | `Hud#extractChat` + `ChatComponent#extractRenderState` |
| `components/ChatScreenMixin` | `ChatScreen#render`（`ChatHud#render` 之后开始） | `ChatScreenMixin` | `ChatScreen#extractRenderState`（整段 = `chat_bar`，消息由 `ChatComponentMixin` 改回 `chat`） |
| `components/PlayerListHudMixin` | `PlayerListHud#render` | `HudMixin` | `Hud#extractTabList` |
| `components/SubtitlesHudMixin` | `SubtitlesHud#render` | `SubtitleOverlayMixin` | `SubtitleOverlay#extractRenderState`（见下方注解） |
| `components/InGameHudMixin` | `renderScoreboardSidebar` | `HudMixin` | `Hud#extractScoreboardSidebar` |
| `components/InGameHudMixin` | `renderOverlayMessage` | `HudMixin` | `Hud#extractOverlayMessage` |
| `components/InGameHudMixin` | `renderTitleAndSubtitle` | `HudMixin` | `Hud#extractTitle` |
| `components/InGameHudMixin` | `renderStatusEffectOverlay` | `HudMixin` | `Hud#extractEffects` |
| `components/InGameHudMixin` | `renderMainHud` + `renderExperienceLevel` | `HudMixin` | `Hud#extractHotbarAndDecorations`（含经验条/等级、切换物品名） |
| `ScreenMixin` | `Screen#applyBlur`（取消） | `ScreenMixin` | `Screen#extractBlurredBackground`（取消） |
| `fabric/.../GameRendererMixin` | `Screen#renderWithTooltip`（前后 = 整屏） | `ScreenMixin` | `Screen#extractRenderStateWithTooltipAndSubtitles` |
| `GameRendererMixin` | `DrawContext#draw()` 之后画测试图标 | `HudMixin` | `Hud#extractRenderState` RETURN（画测试图标） |
| `VideoOptionsScreenMixin` | `VideoOptionsScreen#addOptions` | `VideoSettingsScreenMixin` | `VideoSettingsScreen#addOptions` |
| `components/EntityRendererMixin` | `EntityRenderer#renderLabelIfPresent` | — | 未移植（名字牌） |
| `components/{AbstractSign,Banner,Beacon,Chest}BlockEntityRendererMixin`、`MapRendererMixin` | 各 `render*` | — | 未移植（世界内内容） |

**字幕为什么要单独挂一个 mixin**：26.3 的 `Hud#extractSubtitleOverlay(gg, boolean)` 有两种模式，
HUD 固定走 `deferred = true`：这种情况下它只是把 `() -> subtitleOverlay.extractRenderState(gg)`
存进 `deferredSubtitles` 字段、**什么都不画**；真正绘制发生在 `Gui#extractRenderState` 末尾的
`Hud#extractDeferredSubtitles()`（所以字幕会盖在打开的界面上）。挂 `Hud#extractSubtitleOverlay`
的话，等到真正绘制时标记早就弹掉了 → 字幕会漏进 OBS。因此标记挂在
`SubtitleOverlay#extractRenderState` 上（全项目只有 `Hud` 引用这个类，两条路径都收敛到它）。

基础设施类 mixin（`FramebufferMixin` 拦 FBO 绑定、`MinecraftClientMixin` 起渲染器/改分辨率、
`WorldRendererMixin` 备份深度、`RenderSystemMixin` 空实现）在 26.3 **没有对应物**：
不再有 `GlStateManager`、不再有"中途切 FBO"，也没必要备份深度。
对应功能改由 `OverlayRenderer`（懒加载 + 每帧清理）、`OverlayTarget`（`TextureTarget` 自动跟随分辨率）、
`nativehook/NativeHook`（`wglSwapBuffers` 钩子 + 帧号去重）承担。

---

## 4. 为什么实现方式完全不同（不能照搬）

| 上游能做的事 | 26.3 为什么不行 |
|---|---|
| `beginDraw()` 里直接 `glBindFramebuffer` 切 FBO，画完再切回来 | `GlStateManager` 不存在；渲染是"记录 pass"，pass 的目标在创建时就定了 |
| `DrawContext#draw()` 强制刷缓冲，保证顺序 | `DrawContext` 不存在；合批发生在 `GuiRenderer#prepare` |
| 每个 renderer 前后 `beginDraw/endDraw` | 世界渲染改成 submit 模式，画到哪个 pass 由框架决定（这也是 3D 部分被砍的根因） |
| `SimpleFramebuffer` + `ShaderLoader` 画 quad 合成 | 全部不存在；改成 `TextureTarget` + 原生 GL 3.3 合成器 |

本分支的替代方案（标记 → 断批 → 分段选目标 → 原生 swap 钩子合成）见 `PORT-NOTES.md` §3。

---

## 5. 名字牌为什么没还原（唯一"差一点"的 UI 项）

上游的做法是：在世界渲染阶段把名字牌的文字写进叠加层的 FBO（`OverlayFramebufferType.NORMAL`），
再在 present 钩子里把叠加层贴回后缓冲——屏幕上有、OBS 里没有。

26.3 要等价实现，需要：

1. 在世界 pass 里把"名字牌这一类 submit 节点"单独截出来（renderpearl 按 pipeline/render type 合批，
   没有"按提交者过滤"的入口）；
2. 用**世界相机矩阵 + 该 pass 的投影**二次渲染到一个离屏目标；
3. 把主 pass 的深度贴到这个目标上，否则名字牌会穿墙显示。

也就是"主 pass 跳过 + 拷深度 + 第二遍渲染"这套 3D 管线工程——正是 `PORT-NOTES.md` §4 里
说明过、且项目边界明确排除的方向。**所以本分支不做名字牌**，也不做任何世界内内容隐藏。

如果哪天真要做，最小切口是 `EntityRenderer#submitNameDisplay` /
`OrderedSubmitNodeCollector#submitNameTag`（26.3 里名字牌的唯一入口），但前提是先把上面第 2、3 步的
"世界内容单独一遍渲染"打通——那已经是另一个项目的量级。

---

## 6. 行为上的细微差异（不影响"能用"，但值得知道）

| 项 | 上游 | 当前 |
|---|---|---|
| 测试图标位置 | `DrawContext#draw()` 之后画在最上层 | 在 `Hud#extractRenderState` 末尾提取，**开界面时会被界面盖住**；判定"屏幕上有没有、OBS 里有没有"的用法不变 |
| 混合方式 | 每个 HUD 组件进 FBO 前设一次预乘混合 | 统一在合成器里做，用配置项 `premultipliedAlpha` 切换（默认开） |
| 第三方 API | `beginDraw/endDraw` = 切 FBO，`backupDepth` 真做事 | 同签名，语义变成"给接下来提取的内容打标记"；`backupDepth` 是空实现（见 `API.md`） |
| ImmediatelyFast 兼容 | 有（`forceDraw`） | 不需要：26.3 已无立即模式批量 |
| 依赖 | Architectury + Fabric API + cloth-config | 只有 cloth-config（+ 可选 ModMenu） |
| 失败表现 | 初始化失败弹 toast，HUD 正常画 | 相同，且多了安全回退链：钩子没装/没触发/合成器坏 → 什么都不隐藏 |

---

## 7. 想继续对齐上游时怎么做

- **纯 2D UI 组件**：按 `AGENTS.md` §6.1 加（`AllDefaultOverlayComponent` + `HudMixin` 一对 `@Inject`
  + 7 个语言文件的键 + `verify-mixins.py`）。
- **整屏**：`AGENTS.md` §6.2。
- **世界内内容 / 名字牌**：先读 §5 与 `PORT-NOTES.md` §4；在"世界内容单独渲染一遍"这条管线打通之前，
  不要用"取消提交"之类的捷径冒充还原（那会让该内容在屏幕上一起消失，语义和上游不同）。
