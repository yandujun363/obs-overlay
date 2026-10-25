# API（给第三方模组）

> 状态：形状与上游 1.21.4 版本兼容，但 **26.3 的语义变了** —— `beginDraw/endDraw` 不再是"切 FBO"，
> 而是"给接下来提取的 GUI 内容打标记"。第三方模组**没有真实使用者验证过**，接入前请先跑一遍。

## 1. 把自己的 GUI 内容变成叠加层

上游文档里的写法仍然可用，但**必须在 GUI 提取阶段调用**（也就是在某个 `extractXxx` 的调用链里）：

```java
OBSOverlay.getAPI().beginDraw();
// 你的绘制/提取代码（往 GuiGraphicsExtractor 里写）
OBSOverlay.getAPI().endDraw();
```

- `beginDraw()` / `endDraw()`：内容交给"当前组件"的标记决定去向
- `beginDraw(IOverlayComponent)` / `endDraw(IOverlayComponent)`：按组件自身的配置决定
  （没启用 → 正常画；auto-hide 命中 → 完全不画）
- `beginDraw(OverlayFramebufferType)` / `endDraw(OverlayFramebufferType)`：强制作为叠加层
  （目前只有 `NORMAL` 有意义，`DEPTH` 是上游遗留值）

**注意**：不在提取阶段调用（例如在世界渲染里调用）不会有任何效果 —— 26.3 的世界渲染是 submit 模式，
这一版没有对应通道。

## 2. 注册自己的组件

```java
public final class MyModComponents {
    public static final IOverlayComponent myHudPart =
            new HUDOverlayComponent(Identifier.fromNamespaceAndPath("mymod", "my_hud_part"),
                    /* defaultOverlay */ false, /* canAutoHide */ true);

    public static void init() {
        OverlayComponentRegistry.registerComponents(myHudPart);
    }
}
```

- 组件 id 会被转成语言键：`obs_overlay.component.<namespace>.<path>`，请提供它和 `.tooltip`
- 自己实现 `IOverlayComponent` 也可以（参考 `DefaultOverlayComponent`），
  但 `isHidden()` 的语义是"完全不画"
- 组件在设置界面里自动出现（`OBSOverlayConfigScreen` 遍历 `OverlayComponentRegistry.components`，
  显示在「HUD 元素」页；`canAutoHide()` 为真的组件还会出现在该页的自动隐藏子分类里）

## 3. 注册可隐藏的整屏

```java
OverlayComponentRegistry.addHideableScreen("my_screen", MyScreen.class);
```

语言键：`obs_overlay.screen.my_screen`。判定逻辑在 `OBSOverlayConfig#isScreenOverlayed`
（也支持"任意容器界面按菜单 id"，用户可自行填 id）。

## 4. 注册不该触发 auto-hide 的界面

```java
OverlayComponentRegistry.addIgnoredScreen(MyOverlayScreen.class);
```

默认只有 `ChatScreen` 在忽略列表里。

## 5. 生命周期注意

- 元素标记表每帧在 `Gui#extractRenderState` 开头清空；跨帧保存 `GuiRenderState`
  里的对象引用没有意义
- 你的内容会被断批逻辑切成独立 draw：不要指望它和别的元素合批
- 如果内容依赖深度/世界状态，这一版不支持（3D 已砍）
