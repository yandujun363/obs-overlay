# 配置说明（`config/obs_overlay.json`）

这份文档讲**每个开关是干什么的、怎么组合、怎么验证**。
上游差异见 [`UPSTREAM-DIFF.md`](UPSTREAM-DIFF.md)，实现原理见 [`PORT-NOTES.md`](PORT-NOTES.md)。

---

## 1. 先搞懂：这是三层互不影响的开关

模组只做一件事：**把某些内容画到"屏幕上有、录出来的画面里没有"的叠加层里**。
围绕这点有三层开关，搞清楚它们的关系，后面就不用瞎试了：

| 层 | 配置键 | 打开之后 | 典型用途 |
|---|---|---|---|
| ① 组件叠加 | `overlayComponents` | 元素**照常画在你屏幕上**，但不进采集画面 | 自己想看坐标/聊天/计分板，观众看不到 |
| ② 开界面时自动隐藏 | `autoHideComponents` | 只要有任何界面开着，该元素**连你的屏幕都不画** | 开着背包时不想被快捷栏/聊天糊住 |
| ③ 整屏隐藏 | `hideAllScreens` / `overlayScreensList` / `overlayHandledScreens*` | 整个界面（内容 + 背景 + 背景模糊）都不进采集画面 | 别让观众看到背包、箱子、聊天记录 |

三者可以任意组合。判定顺序（每个元素在被"提取"进渲染列表时决定去向）：

```
这个元素属于哪个组件？
├─ 组件没开叠加(①=false)             → 正常画          （屏幕有、OBS 有）
├─ ① 开 + ② 命中（有界面开着）        → 完全不画        （屏幕没有、OBS 没有）
└─ ① 开 + ② 没命中                    → 画进叠加层      （屏幕有、OBS 没有）
```

整屏（③）走的是"整个 Screen 的内容都归一个标记"，和上面三条一个逻辑。

### 一个必须知道的前提

叠加层只有在**"分离是安全的"**时候才会启用：

```
wglSwapBuffers 钩子装上  &&  钩子至少已经触发过一次  &&  合成器没被标记为损坏
```

三者任一不满足 → `isActive() = false` → **所有开关暂时失效，一切按正常方式渲染**
（HUD 照常显示，OBS 里也照常有）。这是刻意的安全设计：
宁可"什么都没隐藏"，也不要"隐藏了但没合成出来，HUD 从屏幕上消失"。

所以：**进游戏第一帧不会隐藏任何东西**，第二帧开始才生效。

---

## 2. 配置在哪、什么时候生效

| 项 | 值 |
|---|---|
| 文件 | `<游戏目录>/config/obs_overlay.json`（设置界面「高级 / 诊断」页底部会显示完整路径） |
| 读取时机 | 客户端初始化时读**一次** |
| 手改 | **需要重启游戏** |
| 设置界面 | 开关立即生效；点"保存"时写盘 |
| 废弃键 | 保存时自动清理（例如从 1.21.4 配置里带过来的 `sign_text`、`chest` 等） |
| 重置 | 删掉文件重启，或用设置界面里每项的"重置"按钮 |

打开设置界面的两条路：

- `选项 → 视频设置 → OBS Overlay 设置`（底部的按钮）
- ModMenu → 本模组的设置按钮（需要装 ModMenu）

设置界面分三页：**HUD 元素** / **整屏界面** / **高级 / 诊断**。

---

## 3. 第一页：HUD / 界面元素（`overlayComponents`）

配置键是 `obs_overlay.<id>`（语言键去掉 `component.` 前缀）。
默认只开 `debug_menu`（和上游一致）。

| 组件 id（配置键后缀） | 默认 | 可 auto-hide | 屏幕上什么时候能看到它 | 怎么触发来测试 |
|---|---|---|---|---|
| `debug_menu` | **开** | ✅ | 左上/右侧的 F3 信息 | 按 `F3` |
| `main_hud` | 关 | ✅ | 屏幕底部：快捷栏、血量、饱食度、护甲、经验条/等级、切换物品时的名字 | 正常情况下一直在；换手持物品看名字 |
| `chat` | 关 | ✅ | 左下角聊天记录（**不含**输入栏） | 说句话 / `/say` |
| `chat_bar` | 关 | ❌ | 按 `T` 打开时的输入栏、命令补全弹窗（**不含**聊天记录） | 按 `T`，输入 `/` |
| `scoreboards` | 关 | ✅ | 屏幕右侧计分板 | `/scoreboard objectives setdisplay sidebar <目标>` |
| `player_list` | 关 | ❌ | 按住 `Tab` 的玩家列表 | 按住 `Tab` |
| `actionbar` | 关 | ✅ | 快捷栏上方那一行字 | `/title @s actionbar {"text":"test"}` |
| `title_subtitle` | 关 | ✅ | 屏幕中央的大字/副标题 | `/title @s title {"text":"test"}` |
| `effects` | 关 | ✅ | 右上角药水效果图标 | `/effect give @s speed 30 1` |
| `subtitles` | 关 | ✅ | 右下角音频字幕 | 辅助功能里开启字幕后放声音 |

（顺序就是设置界面里的显示顺序，由 `AllDefaultOverlayComponents` 的注册顺序决定。）

几个容易踩的坑：

- **`chat` 和 `chat_bar` 是两个独立开关。** 想"聊天记录和输入框都藏"就两个都开；
  聊天界面打开时，聊天记录仍归 `chat`，输入栏归 `chat_bar`。
- **auto-hide 只对打了 ✅ 的组件存在。** `chat_bar` / `player_list` 本来只在"界面开着"时才出现，
  再套一层 auto-hide 没意义，所以没有这个开关。
- **auto-hide 的忽略列表里只有聊天界面。** 也就是"聊天框开着"不算"有界面打开"，
  主 HUD / 聊天记录不会被这个规则误隐藏。
- 默认值 `true` 的只有 `debug_menu`：F3 是最容易在直播里泄密的东西（坐标、种子、实体数）。

JSON 片段：

```json
"overlayComponents": {
  "obs_overlay.debug_menu": true,
  "obs_overlay.main_hud": true,
  "obs_overlay.chat": true,
  "obs_overlay.chat_bar": true,
  "obs_overlay.player_list": true,
  "obs_overlay.scoreboards": true,
  "obs_overlay.actionbar": false,
  "obs_overlay.title_subtitle": false,
  "obs_overlay.effects": false,
  "obs_overlay.subtitles": false
}
```

`autoHideComponents` 用同样的键名，值表示"开界面时要不要完全不画"（默认 `true`）：

```json
"autoHideComponents": {
  "obs_overlay.main_hud": true,
  "obs_overlay.chat": true
}
```

> 注意：`overlayComponents` 里没写的组件用默认值，`autoHideComponents` 里没写的用 `true`。
> 所以只想让"主 HUD 开界面时不消失"，就写 `"obs_overlay.main_hud": false`。

---

## 4. 第二页：整屏界面（`overlayScreensList` / `hideAllScreens`）

| 键 | 界面 | 默认 |
|---|---|---|
| `inventory` | 生存物品栏 | 关 |
| `creative_inventory` | 创造物品栏 | 关 |
| `pause_menu` | 暂停菜单（Esc） | 关 |
| `command_block` | 命令方块编辑界面 | 关 |
| `hideAllScreens` | 上面这些 + **所有**界面（含各种容器） | 关 |

要点：

- `hideAllScreens` 只在**进世界之后**生效（`level != null`），所以主菜单/加载界面不受影响。
- 打开 `hideAllScreens` 时，设置界面里那些单项开关会变灰——不是坏了，是"已经全部包含"。
- 被隐藏的界面**连背景模糊一起取消**。否则"世界突然糊了"本身就是"有人开菜单了"的剧透。
  这是刻意和上游一致的行为。
- 界面被隐藏时，它在 OBS 里是**完全消失**（不是透明），所以你屏幕上看到的还是正常界面。

JSON 片段：

```json
"overlayScreensList": {
  "inventory": true,
  "creative_inventory": true,
  "pause_menu": false,
  "command_block": false
},
"hideAllScreens": false
```

---

## 5. 第二页下半：按菜单 id 隐藏任意容器

只对**容器类界面**（`AbstractContainerScreen`：箱子、熔炉、铁砧、工作台、附魔台……）有效，
因为这类界面才有"菜单 id"。普通界面（比如选项菜单）没有 id，用不了这一项。

```json
"overlayHandledScreensEnabled": true,
"overlayHandledScreensList": [
  "minecraft:anvil",
  "minecraft:furnace",
  "minecraft:generic_9x3"
]
```

- 总开关 `overlayHandledScreensEnabled` 必须先开，列表才生效。
- 写法 `minecraft:anvil` 和 `anvil` **都认**（带命名空间和不带都行）。
- **id 从哪找**：设置界面里打开这一项后，下方会出现「存在的屏幕标识符」列表，那是
  `BuiltInRegistries.MENU` 里的全部 id，直接抄。常见几个：

  | id | 界面 |
  |---|---|
  | `minecraft:generic_9x3` | 普通箱子（27 格） |
  | `minecraft:generic_9x6` | 大箱子（54 格） |
  | `minecraft:shulker_box` | 潜影盒 |
  | `minecraft:furnace` / `minecraft:blast_furnace` / `minecraft:smoker` | 熔炉三兄弟 |
  | `minecraft:anvil` | 铁砧 |
  | `minecraft:enchantment` | 附魔台 |
  | `minecraft:crafting` | 工作台 |
  | `minecraft:brewing_stand` | 酿造台 |
  | `minecraft:merchant` | 村民交易 |

- 填了不存在的 id，设置界面会标红报错；手改 JSON 填错则静默无效（不崩）。
- 和 `hideAllScreens` 的关系：`hideAllScreens` 打开时这一整块都不需要了，规则上它优先。

---

## 6. 第三页：高级 / 诊断

| 键 | 默认 | 作用 |
|---|---|---|
| `showTestIcon` | `false` | 左上角画一个 16×16 紫色方块。**屏幕上必须有、OBS 里必须没有**——这是判断"到底生效没"最可靠的办法 |
| `premultipliedAlpha` | `true` | 合成时的 alpha 约定。26.3 的 GUI 管线输出预乘 alpha，所以默认开；**颜色发灰/发亮/边缘发白时切 `false`** |

`premultipliedAlpha` 是最容易误调的项，判断方法：

- 隐藏内容颜色**偏亮/发白/像蒙了一层** → 试试 `false`
- 切换后颜色正常了 → 说明你的环境（驱动/资源包/Iris）输出的是非预乘 alpha
- 纯粹"位置对、颜色对"就别动它

---

## 7. 直接抄的配方（整份文件）

把下面内容存成 `config/obs_overlay.json`（或只替换对应段）后**重启游戏**。
没写的键自动用默认值，所以片段可以只写关心的部分。

### 7.1 默认：只隐藏 F3

```json
{
  "overlayComponents": {},
  "autoHideComponents": {},
  "overlayScreensList": {},
  "hideAllScreens": false,
  "overlayHandledScreensEnabled": false,
  "overlayHandledScreensList": [],
  "showTestIcon": false,
  "premultipliedAlpha": true
}
```

### 7.2 直播安全：HUD 基本全藏 + 所有界面不露

```json
{
  "overlayComponents": {
    "obs_overlay.debug_menu": true,
    "obs_overlay.main_hud": true,
    "obs_overlay.chat": true,
    "obs_overlay.chat_bar": true,
    "obs_overlay.player_list": true,
    "obs_overlay.scoreboards": true,
    "obs_overlay.actionbar": true,
    "obs_overlay.title_subtitle": true,
    "obs_overlay.effects": true,
    "obs_overlay.subtitles": true
  },
  "hideAllScreens": true,
  "showTestIcon": false,
  "premultipliedAlpha": true
}
```

### 7.3 只藏"会泄密"的两样：聊天 + 计分板

```json
{
  "overlayComponents": {
    "obs_overlay.debug_menu": true,
    "obs_overlay.chat": true,
    "obs_overlay.scoreboards": true
  }
}
```

### 7.4 只藏界面，HUD 全部留在屏幕上

```json
{
  "overlayComponents": {},
  "hideAllScreens": true
}
```

### 7.5 藏界面但讨厌"开界面时 HUD 突然消失"

```json
{
  "overlayComponents": {
    "obs_overlay.main_hud": true,
    "obs_overlay.chat": true
  },
  "autoHideComponents": {
    "obs_overlay.main_hud": false,
    "obs_overlay.chat": false
  },
  "overlayScreensList": {
    "inventory": true
  }
}
```

### 7.6 只藏背包和铁砧/熔炉（其它容器照常显示）

```json
{
  "overlayScreensList": {
    "inventory": true
  },
  "overlayHandledScreensEnabled": true,
  "overlayHandledScreensList": [
    "minecraft:anvil",
    "minecraft:furnace"
  ]
}
```

---

## 8. 排障速查

| 现象 | 先看什么 |
|---|---|
| 什么都不隐藏，进游戏弹了 `OBS Overlay disabled` 提示 | 图形 API 是不是 OpenGL（视频设置里看）；日志里有没有 `native present hook installed` |
| 日志有 `present hook installed`，但没有 `present hook fired` | swap 没走 `opengl32!wglSwapBuffers`，见 `PORT-NOTES.md` 末尾"风险"一节 |
| 上一秒还好，改完配置没反应 | 手改 JSON 必须重启；设置界面里的开关则是即时的 |
| 隐藏的内容颜色不对 | 切 `premultipliedAlpha` |
| 屏幕上有、OBS 里也有 | 打开 `showTestIcon`：如果测试方块在 OBS 里也看得见，说明模组整体没生效，不是某个组件的问题 |
| 屏幕上有、OBS 里没有，但**界面上看不到某些 HUD** | 断批失效，见 `AGENTS.md` §7 |
| 视频设置里找不到按钮 | 按 `AGENTS.md` §7 最后一行同步 `VideoSettingsScreenMixin` |
| 想确认某个组件 id 还在不在 | 看 `AllDefaultOverlayComponents`，或设置界面第一页的列表 |

日志关键字：`[obs_overlay]`。三条关键顺序：
`native present hook installed` → `present hook fired - overlay splitting is now active` → `GL compositor ready`。

---

## 9. 和上游 1.21.4 的配置差异

- 键名、语义、默认值都和上游一致（包括"`true` = 从 OBS 隐藏"这个反直觉的命名）。
- 上游多出来的 7 项（名字牌 + 告示牌/箱子/地图/旗帜/信标）**在这个分支没有**，
  所以旧配置里这些键会在保存时被自动清掉。原因见 [`UPSTREAM-DIFF.md`](UPSTREAM-DIFF.md) §5。
- 新增了一个上游没有的键：`premultipliedAlpha`（26.3 合成器需要）。
