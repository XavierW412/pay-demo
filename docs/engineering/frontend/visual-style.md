# Tailwind 与 shadcn/ui 主题规范

## 1. 适用范围

本文规定 React 运营后台如何使用 Tailwind CSS、shadcn/ui 组件源码、Base UI 交互基座和项目设计令牌。产品体验目标见[产品界面设计原则](./design-principles.md)；参考产品和浏览器评审方法见[视觉参考](./visual-references.md)；选型状态见[技术栈清单](../technology-stack.md)。

项目建议采用 React + TypeScript + Vite + Tailwind CSS 4 + shadcn/ui + Base UI + Lucide React。Tailwind 是唯一页面主样式体系；不并用 Sass/SCSS 或 Element Plus。

## 2. 样式和组件目录

```text
frontend/src/
├── components/
│   └── ui/                       # shadcn/ui 源码组件，按需加入并由项目维护
├── styles/
│   ├── index.css                 # Tailwind 和 shadcn/ui 样式入口
│   ├── tokens.css                # CSS 变量和主题令牌
│   └── base.css                  # 少量全局基础样式
├── modules/                      # 按业务模块组织页面和业务组件
└── shared/                       # 跨模块请求、格式化和业务组件
```

`components.json` 中的组件目录和 CSS 入口必须与实际结构保持一致。按使用需要创建文件，不提前生成空目录。

## 3. Tailwind 使用约定

- 页面布局、间距、响应式、文字、边框和状态优先使用 Tailwind 主题令牌对应的工具类。
- 类名按布局、尺寸与间距、排版、表面与边框、交互状态、响应式变体组织；同类组件保持顺序一致。
- 优先用布局容器和 `gap-*` 表达元素关系，避免依赖脆弱的子元素外边距堆叠。
- 颜色、间距、圆角、阴影和字体从项目刻度选取；禁止在业务页面散落任意色值和随意像素值。
- 不用工具类机械堆出通用 SaaS 模板；页面结构先由业务任务和信息优先级决定。
- 只有重复出现且语义稳定的组合才抽成 React 组件；不为缩短 `className` 创建大量薄封装。
- 状态变体明确覆盖 hover、focus-visible、active、disabled 和 loading 等实际需要的情况。
- 禁止用宽泛全局选择器修改所有按钮、输入框或弹窗；通用基础样式集中在 `base.css` 并限定作用范围。
- `@apply` 只用于稳定、语义清楚、跨多个位置重复的规则；默认使用 Tailwind 类或复用 React 组件。
- 动态状态样式用完整静态类名映射；不在 TSX 里拼出无法被 Tailwind 扫描的片段。

## 4. 设计令牌

### 4.1 三层结构

1. **原始令牌**：中性色阶、品牌候选色、间距刻度、字号、圆角和阴影。
2. **语义令牌**：页面背景、表面、正文、弱化文字、边框、焦点、成功、警告、危险和待核实状态。
3. **组件令牌**：按钮高度、表格密度、弹层尺寸、输入控件高度等项目级规则。

业务页面引用语义令牌；组件令牌只在确有统一需求时建立。星辙品牌色和品牌资产尚未确认时，不把示例值写成正式品牌标准。

### 4.2 CSS 变量映射

- CSS 自定义属性保存主题值；Tailwind 主题配置将语义变量映射为可复用的颜色、字体、圆角、阴影和间距类。
- `components.json` 启用 CSS 变量主题模式；shadcn/ui 组件使用项目语义令牌，不逐个组件写品牌色。
- 不重复维护一套 CSS 变量和另一套硬编码颜色表。
- 支付、退款、通知和结算状态分别定义令牌；每个状态都要有清楚文本，不能把“支付成功”与“已结算”显示成一个状态。
- Figma 有已确认变量后，先映射为原始和语义令牌再应用到组件；临时设计颜色不自动成为产品令牌。

## 5. shadcn/ui 与 Base UI 组件维护

- shadcn/ui CLI 添加到仓库的组件属于项目源码，纳入代码评审、命名规则和长期维护。
- 新项目优先使用 shadcn/ui 当前默认的 Base UI 基座。Radix 或 React Aria 只有在具体组件能力、可访问性或兼容性有明确需要时才评估，不同时混搭多种基座。[官方公告](https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default)
- 只添加当前功能实际需要的组件，不一次性生成整套 UI。
- 检查组件依赖、键盘行为、焦点管理、禁用状态和无障碍语义；生成的源码不代表已通过项目验收。
- 常用业务表达封装为项目组件，例如金额展示、支付状态、关联订单号和危险操作确认。组件 API 用项目业务语言，不泄漏底层原语细节。
- 页面组合业务组件；不复制同一份按钮、弹窗、标签实现再局部修改。
- shadcn/ui 默认主题是起点。按业务密度、品牌和代表页面评审定制，不直接批量接受默认样式。

## 6. 图标和动效

- 统一使用 Lucide React，保持常用尺寸、描边风格和文字基线一致。
- 图标按钮必须有可访问名称；重要操作不能只使用含义不明确的图标。
- 动效只解释反馈、展开层级和状态变化；尊重系统减少动态效果的偏好。
- 普通过渡优先使用 CSS；没有复杂时间轴或滚动编排的明确需求时，不引入 GSAP；后台不默认引入 Three.js。

## 7. 页面实现和视觉验收

- 实现前写明用户任务、关键数据、主操作、失败路径和页面参考；“高级感”不能作为唯一设计要求。
- 先检查已存在的令牌、组件和 Figma 信息，再新增界面代码。
- 重要页面在浏览器实际渲染后检查列表、详情、弹窗、异常和窄屏布局；有 Figma 时按截图对照。
- 发现视觉偏差时优先修正共享令牌或组件；仅适用于单页的差异留在业务模块中。
- 移除无业务含义的卡片、统计图、欢迎语、装饰性渐变和虚构数据。

## 8. 参考资料

- [Tailwind CSS 工具类样式](https://tailwindcss.com/docs/styling-with-utility-classes)
- [Tailwind CSS 主题变量](https://tailwindcss.com/docs/theme)
- [shadcn/ui Vite 安装指南](https://ui.shadcn.com/docs/installation/vite)
- [shadcn/ui 组件配置](https://ui.shadcn.com/docs/components-json)
- [shadcn/ui 主题](https://ui.shadcn.com/docs/theming)
- [Base UI](https://base-ui.com/react)
- [Lucide](https://lucide.dev/)
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/)
