# iOSCurl 接入与迁移

`libreadview` 的仿真翻页现在只保留 **iOSCurl / GoogleCurl**。覆盖、滑动、滚动、无动画和 iBook 风格滑动继续提供。

## 使用

```kotlin
import org.peyilo.libreadview.turning.IosCurlEffect
import org.peyilo.libreadview.turning.IosCurlOptions
import org.peyilo.libreadview.turning.IosCurlBinding

reader.pageEffect = IosCurlEffect()

// 底边装订的纵向翻页。
reader.pageEffect = IosCurlEffect(
    IosCurlOptions(vertical = true, binding = IosCurlBinding.Maximum)
)
```

`EffectFactory.IOS_CURL` 的值为 `2`。旧的 `EffectFactory.CURL` 保留为弃用别名，恢复旧配置时会进入 iOSCurl；GoogleCurl 的 ID `7` 不变。旧的 iBookCurl、GLSL Curl 类和实验仿真 View 已删除，使用这些类型的宿主应改为 `IosCurlEffect`。

旧 `PageGLSurfaceView` 及 `PageEffect` 上专用于它的创建/显示接口也已移除。容器现在只保存真实页面，`getPageChildCount()` 的语义不变。自定义效果可通过 `dispatchDraw(Canvas)` 合成结果；iOSCurl 的 EGL 资源完全由自身事务管理。

`IosCurlOptions` 不暴露算法内核类型：

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| `vertical` | `false` | 横向或纵向翻页 |
| `binding` | `Minimum` | 横向为左/右侧，纵向为上/下侧 |
| `rightToLeft` | `null` | 横向跟随容器 RTL；可显式覆盖；纵向不受 RTL 影响 |
| `shadowSoftness` | `1.35f` | 阴影淡出边缘宽度，范围 1–3 |
| `shadowOpacity` | `0.55f` | 中性黑色阴影透明度，范围 0–1 |
| `bitmapBudgetBytes` | 32 MiB | 两张截图、输出及读回缓冲的预算，不包含驱动纹理 |

`setAnimDuration(400)` 保留原版时长。其他非负值按比例缩放所有轨道；`0` 关闭视觉动画。`minPageTurnInterval` 继续限制连续翻页，程序调用 `flipToNextPage(false)` / `flipToPrevPage(false)` 可跳过间隔限制。

## 模块与资源边界

```text
Demo / 宿主 → libreadview 公共 PageEffect API
                    ↓
              turning/IosCurlEffect
                ├─ ios curl 输入适配：指针、页面坐标、密度
                ├─ IosCurlSession：一次翻页的不可变截图、时钟与资源
                └─ render/IosCurlRenderer：Android EGL/GLES2 绘制
                    ↓
                  :ioscurl
          gesture / control / layout / mesh / math / transition
```

算法模块从 [iReadView 的 bd73c4e](https://github.com/Peyilo/iReadView/commit/bd73c4e7a6adeeb3ecbf4386d862aa91d0eff313) 引入。生产算法与数值测试资源保持原样；没有重新拟合几何、UV 或阴影。源文件与校验摘要见 [来源记录](../ioscurl/UPSTREAM.json)。算法只依赖 Kotlin 标准类型和受管理数组，不依赖阅读库、Android、JNI、C/C++ 或固件。正常构建和测试不需要逆向研究环境。

`libreadview` 通过 `implementation(project(":ioscurl"))` 引用算法，Demo 只调用阅读库。算法源码及平台适配文件分开管理；之后同步上游时应运行原有数值对照测试。

## 手势与导航

Pan/Tap 使用核心原始识别器，保留 10pt 滞回、25/75 双采样速度权重、300pt/s 释放判断、45pt 点击移动半径及 1.5s 点击期限。阈值使用页面局部逻辑点，支持容器 padding。开始前出现第二指会使识别失败；开始后的第二指不接管主指。`ACTION_CANCEL` 复位当前纸页，反向拖动由核心速度/位置规则决定提交或取消。

宿主的点击区域回调优先处理点击；未消费时使用原版边缘点击区域，中间区域交给普通点击回调。子 View 接收手势的既有策略保持不变。

页面索引在完成动画时更新，取消不会更新。自动翻页被新操作中断时完成已经提交的翻页；尚未松手的拖动被切换效果或尺寸改变打断时取消。数据刷新或显式跳转会使旧事务失效，不能把旧目的页再次提交到新数据或新位置。完成、取消、异常及 detach 都归还位图并关闭本次 EGL 资源。

截图是事务持有的像素，不重放可变 View 录制。后续重试重新捕获页面，不会沿用上次隐藏层的透明图像。预算较小时等比例降低截图分辨率；预算无法容纳最小缓冲、内存不足或 EGL 不可用时，保留同一手势的导航判定并省略视觉动画。

单栏的背面采样同一张卷曲纸页的镜像内容，不使用下一页正面。背面纸张颜色优先读取正面页面的实色背景，否则采样正面截图的 8×8 主色。阴影以半透明中性黑色进行预乘 source-over 混合，避免夜间背景被灰白阴影提亮。

当前平台适配器为单栏。核心 `CurlConfig.doubleSided`、中缝装订及 `CurlLayout` 的独立正反面来源仍保留；双栏需要由单独的 spread 适配器提供页面，不应在网格算法中查询书籍内容。

## 构建、发布与验收

```powershell
./gradlew.bat :ioscurl:test :ioscurl:checkKotlinFormat verifyCurlArchitecture
./gradlew.bat :app:libreadview:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.peyilo.readview.IosCurlIntegrationTest"
```

核心的原有 golden 测试覆盖网格、UV、half4、索引、批次、手势、缓动、布局和转场。平台测试另外验证两个轴、两个装订边、RTL、前后翻、取消重试、反向手势、多指、首尾页、预算回退、尺寸改变、效果切换和页面局部坐标。设备与实际执行结果记录在 [验收记录](IOS_CURL_VALIDATION.md)。

Demo 的 PageContainer 页面默认使用 iOSCurl，阅读设置和翻页面板均提供 iOSCurl / GoogleCurl。“Test”入口使用同一生产适配器的 12 页网格示例。

算法仍是独立 JAR，手动分发 AAR 时必须同时携带它。使用 Maven 发布时，阅读库 POM 会记录 `org.peyilo.ireadview:ioscurl` 依赖，需同时发布该模块；本次代码接入不会发布 Maven Central 版本。只把阅读库 AAR 复制进宿主会遗漏算法依赖。

核心默认版本沿用上游的 `0.1.0-SNAPSHOT`。正式发布阅读库前，应通过 `-PireadVersion=<核心正式版本>` 使用已发布的非 SNAPSHOT 核心版本，并在对应仓库配置核心模块的发布与签名。`ioscurl:publishReleasePublicationToLocalRepository` 可生成用于本地验证的核心 Maven 仓库。

渲染采用 EGL 离屏绘制、readback 和 Canvas 合成，性能受设备影响。数值对照通过不等同于与原始 iPhone GPU 的每个像素完全相同。
