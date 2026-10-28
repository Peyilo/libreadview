# iOSCurl 接入验证

日期：2026-10-28。算法来源固定为 iReadView `bd73c4e7a6adeeb3ecbf4386d862aa91d0eff313`，详见 [UPSTREAM.json](../ioscurl/UPSTREAM.json)。

| 检查 | 结果 |
| --- | --- |
| 上游算法和测试资源 | 38 个文件逐字节一致，SHA-256 已记录 |
| 原有核心数值对照和阴影测试 | 11 项通过，未修改基准或误差范围 |
| 阅读库已有 JVM 单元测试 | 1 项通过 |
| 核心 Kotlin 格式检查 | 通过 |
| `verifyCurlArchitecture` | 通过，24 个可移植算法源文件，仅 iOSCurl / GoogleCurl 两个具体仿真实现 |
| Demo debug APK / instrumentation APK | 构建通过 |
| 阅读库 release AAR / 核心 JAR / sources JAR | 构建通过 |
| 阅读库 Maven POM | 正确记录 ioscurl runtime 依赖 |
| iOSCurl 平台回归 | API 36、Pixel_9a 模拟器，8 项通过 |
| 已有滚动模式与跨章节回归 | 同一 API 36 模拟器，3 项通过 |
| 暂存内容独立验收 | 通过：核心 11 项、库 1 项、iOSCurl 8 项及滚动 3 项；排除了原有的其他未提交修改 |

平台回归覆盖：

- 横纵两个轴、最小/最大边装订、LTR/RTL、前后翻共 16 组自动翻页。
- 两个方向分别连续取消三次，再次拖动时用图像颜色确认当前纸页和底页都可见，最终提交正确。
- 反向拖动取消；开始前第二指使识别失败；开始后第二指不接管主指。
- 首尾页边界、零时长翻页、挂载前配置，以及位图预算不足时的提交与取消判定。
- 切换效果、改变尺寸、刷新数据和显式跳转时释放旧事务，避免旧目的页污染新位置。
- 带 padding 的页面局部坐标与宿主点击区域回调。

发布产物检查确认：AAR 包含新的 iOSCurl 适配器和保留的 GoogleCurl，不含旧 iBookCurl、GLSL Curl、旧 GL 容器或旧 shader 资源；核心 JAR 使用 `org.peyilo.ioscurl` 包名，无旧原型包。没有引入自编 native 库。

复现命令见 [接入文档](IOS_CURL.md#构建发布与验收)。本次设备执行范围为 API 36；minSdk 仍为 21，但本次没有在 API 21/22 真机上运行。数值基准与设备回归通过不构成跨 GPU 逐像素一致或帧率保证。
