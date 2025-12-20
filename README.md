# libreadview

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.peyilo/libreadview/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.peyilo/libreadview)

An Android library designed specifically for eBook and novel reader apps, supporting multiple immersive page turning modes including scrolling, cover, simulation, and more.

![readview_loading](./images/readview_loading.png)

## Demo

To quickly see the page turning animations and layout in action, you can download the demo app:

 [Download demo.apk](https://github.com/Peyilo/libreadview/releases/download/v0.0.5/demo.apk)

## Features

- Multiple page-turning animations, including cover page turn, horizontal slide, simulation, scrolling, and more.
- Optimized for performance and customization

![readview_page_turning](./images/readview_page_turning.png)

| Cover                 | Google Curl                 | iBook Curl                 |
| --------------------- | --------------------------- | -------------------------- |
| ![](images/cover.gif) | ![](images/google_curl.gif) | ![](images/ibook_curl.gif) |

| Slide                 | Scroll                 | iBook Slide                 |
| --------------------- | ---------------------- | --------------------------- |
| ![](images/slide.gif) | ![](images/scroll.gif) | ![](images/ibook_slide.gif) |

## Installation

<details>
<summary><b>Via Jar</b></summary></details>
You can download a jar from this link:

 [GitHub's releases page](https://github.com/peyilo/libreadview/releases)

<details>
<summary><b>Via Gradle</b></summary></details>
Or use Gradle: 

```kotlin
dependencies {
    implementation("io.github.peyilo:libreadview:0.0.5")
}
```
<details> <summary><b>Via Maven</b></summary></details>

Or Maven:

```xml
<dependency>
    <groupId>io.github.peyilo</groupId>
    <artifactId>libreadview</artifactId>
    <version>0.0.5</version>
</dependency>
```

## Usage

### ReadView

#### 1. Add `BasicReadView` in XML

```xml
<org.peyilo.libreadview.basic.BasicReadView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/readview"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

#### 2. Initialize in Kotlin

```kotlin
class ReadViewActivity : AppCompatActivity() {

    private lateinit var readview: BasicReadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_view)
        supportActionBar?.hide()
        
        // Specify the path to the local text file
        val filePath = "/path/to/your/txtfile"
        val selectedFile = File(filePath)
        
        readview = findViewById(R.id.readview)
        readview.pageEffect = EffectFactory.create(EffectFactory.COVER)      // Set the page turning mode to cover page turning
           
        // 加载本地txt文件作为内容
        readview.openBook(
            TxtFileLoader(
                selectedFile, encoding = "UTF-8"
            ).apply {
                // 如果有需要可以指定章节标题正则表达式,用来分割章节
                // addTitleRegex("第\\d+章 .*")
            },
            chapIndex = 1,
            pageIndex = 1,
        )
        readview.setOnClickRegionListener { xPercent, yPercent ->
            when (xPercent) {
                in 0..30 -> readview.flipToPrevPage()     // Click on the left area to flip to the previous page
                in 70..100 -> readview.flipToNextPage()   // Click on the right area to flip to the next page
                else -> Unit
            }
            true
        }
        readview.preprocessBefore = 1        // Always preload one chapter before the current one
        readview.preprocessBehind = 1        // Always preload one chapter after the current one
    }
}
```

### 翻页模式的切换

```kotlin
readview.pageEffect = EffectFactory.create(EffectFactory.COVER)
```

你可以通过调用EffectFactory.create()来获取不同的PageEffect，并且设置给readview，从而切换翻页模式。

支持的翻页模式有：

- EffectFactory.NO_ANIMATION：无动画翻页
- EffectFactory.CURL：模拟纸张的仿真翻页
- EffectFactory.COVER：覆盖翻页
- EffectFactory.SLIDE：左右翻页
- EffectFactory.SCROLL：滚动翻页
- EffectFactory.IBOOK_SLIDE：模仿Apple iBook的左右翻页
- EffectFactory.GOOGLE_CURL：类似于Moon Reader中的Google仿真翻页

### 关于小说内容的加载

```kotlin
readview.openBook(
    TxtFileLoader(
        selectedFile, encoding = "UTF-8"
    ).apply {
        // Set the regular expression to match chapter titles
        addTitleRegex("第\\d+章 .*")       
    },
    chapIndex = 100,
    pageIndex = 1,
)
```

调用readview.openBook()即可打开一本小说，上示代码中设置的是一个TxtFileLoader，它会从本地的txt文件中加载小说章节内容。如果要加载的小说内容是来自于网络加载，你可以通过实现BookLoader接口，并指定给readview.openBook()即可加载网络小说内容。

BookLoader接口只有两个方法要实现：

```kotlin
interface BookLoader {

    /**
     * 在这里完成目录初始化，目录初始化之后，要保证Book中的每个Chapter都包含了加载章节所有信息，例如：章节的链接。
     * 以便在loadChap(chapter: Chapter)中完成章节实际内容的加载
     */
    fun initToc(): Book

    /**
     * 加载章节的内容
     */
    fun loadChap(chapter: Chapter): Chapter

}
```

一个加载网络小说内容的BookLoader示例如下：

```kotlin
class BiqugeBookLoader(val bookId: Long): BookLoader {

    var timeout = 20_000

    companion object {
        private const val BASE_URL = "https://www.yuyouku.com/"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari"
    }

    override fun initToc(): Book {
        val url = "$BASE_URL/book/$bookId/"
        val document = fetch(url)
        val bookTitle = document.select("article h1").first()!!.ownText().trim()
        val book = Book(bookTitle)
        book.id = bookId
        document.select("#chapters-list li a").forEach {
            book.addBookNode(Chapter(it.text()).apply {
                what = it.attr("href")
            })
        }
        return book
    }

    override fun loadChap(chapter: Chapter): Chapter {
        val url = "$BASE_URL${chapter.what}"
        val document = fetch(url)
        val txtContent = document.select("#txtContent")
        // 文本净化操作
        txtContent.select("div.gad2").remove()      // 删除无用的标签
        // 选中 #txtContent 里的所有 <p> 标签，并去掉标签外壳
        txtContent.select("#txtContent p").unwrap()
        val html = txtContent.html()
        val splits = html.split("<br>")
        splits.map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { chapter.addParagraph(it) }
        return chapter
    }

    // —— 工具方法 ——
    private fun fetch(url: String): Document =
        Jsoup.connect(url)
            .userAgent(UA)
            .timeout(timeout)
            .get()

}
```

### 页面的跳转

BasicReadView实现了PageNavigator、PageNavigator两个接口，你可以调用这两个接口的函数来实现任意页面的跳转：

```kotlin
interface PageNavigator {

    /**
     * 获取容器中全部page的数量
     */
    fun getContainerPageCount(): Int

    /**
     * 获取当前显示的page，位于容器中的索引，从1开始
     */
    fun getCurContainerPageIndex(): Int

    /**
     * 跳转到指定章节
     * @param pageIndex 章节索引，从1开始
     */
    fun navigatePage(@IntRange(from = 1) pageIndex: Int): Boolean

    /**
     * 跳转到第一页
     * @return 是否跳转成功
     */
    fun navigateToFirstPage(): Boolean

    /**
     * 跳转到最后一页
     * @return 是否跳转成功
     */
    fun navigateToLastPage(): Boolean

    /**
     * 跳转到下一页
     * @return 是否跳转成功
     */
    fun navigateToNextPage(): Boolean

    /**
     * 跳转到上一页
     * @return 是否跳转成功
     */
    fun navigateToPrevPage(): Boolean

}
```

```kotlin
/**
 * 一个定义了书记导航所需实现的函数的接口
 */
interface BookNavigator {

    /**
     * 获取章节数量
     */
    fun getChapCount(): Int

    /**
     * 当前章节的理论分页数量（根据排版计算得出）
     */
    fun getCurChapPageCount(): Int

    /**
     * 获取当前章节索引，从1开始
     */
    fun getCurChapIndex(): Int

    /**
     * 获取当前页在当前章节的索引，从1开始
     */
    fun getCurChapPageIndex(): Int

    /**
     * 获取指定章节的标题
     * @return 标题, 可能为null
     */
    fun getChapTitle(@IntRange(from = 1) chapIndex: Int): String?

    /**
     * 跳转到指定章节的指定页
     * @param chapIndex 章节索引，从1开始
     * @param chapPageIndex 页索引，从1开始
     */
    fun navigateBook(@IntRange(from = 1) chapIndex: Int, @IntRange(from = 1) chapPageIndex: Int): Boolean

    /**
     * 跳转到指定章节
     * @param chapIndex 章节索引，从1开始
     * @return 是否跳转成功
     */
    fun navigateToChapter(@IntRange(from = 1) chapIndex: Int): Boolean

    /**
     * 跳转到下一章节
     * @return 是否跳转成功
     */
    fun navigateToNextChapter(): Boolean

    /**
     * 跳转到上一章节
     * @return 是否跳转成功
     */
    fun navigateToPrevChapter(): Boolean

}
```

### BasicReadView各种间距、字体、字体大小、字体颜色、背景的设置

##### 设置背景

- setPageBackgroundColor(@ColorInt color: Int)
- setPageBackgroundResource(@DrawableRes resId: Int)
- setPageBackground(drawable: Drawable)
- setPageBackground(bitmap: Bitmap) 

##### 设置字体

- setTitleTypeface(typeface: Typeface)：设置章节标题文字字体 (请在ui线程调用)
- setContentTypeface(typeface: Typeface)：设置章节正文文字字体 (请在ui线程调用)

##### 设置字体大小

- setTitleTextSize(size: Float)：设置章节标题文字大小 (请在ui线程调用)
- setContentTextSize(size: Float)：设置章节正文文字大小 (请在ui线程调用)

##### 设置字体颜色

- setTitleTextColor(color: Int)：设置章节标题文字颜色
- setContentTextColor(color: Int)：设置章节正文文字颜色
- setHeaderAndFooterTextColor(color: Int)：设置页眉和页脚文字颜色

##### 各种间距的设置

- setPagePadding(left: Int, top: Int, right: Int, bottom: Int)：设置页面的边距
- setHeaderPadding(left: Int, top: Int, right: Int, bottom: Int)：设置页眉的边距
- setFooterPadding(left: Int, top: Int, right: Int, bottom: Int) ：设置页脚的边距
- setBodyPadding(left: Int, top: Int, right: Int, bottom: Int) ：设置主体内容的边距
- setTitlePadding(left: Int, top: Int, right: Int, bottom: Int) ：设置标题部分的边距
- setContentPadding(left: Int, top: Int, right: Int, bottom: Int) ：设置正文部分的边距

- setFirstParaIndent(indent: Float)：设置段落首行缩进 (请在ui线程调用)
- setContentTextMargin(margin: Float)： 设置正文文字的边距 (请在ui线程调用)
- setTitleTextMargin(margin: Float)：设置标题文字间距 (请在ui线程调用)
- setContentLineMargin(margin: Float)：设置行间距 (请在ui线程调用)
- setContentParaMargin(margin: Float)：设置段落间距 (请在ui线程调用)

### TODO

- IBookCurlEffect在API21和API22两个版本会出现崩溃
- IBookCurlEffect虽然与真实的纸张翻页很相似，但是由于控制点与触摸点可能会重合，造成页面显示会出现突变。同时，IBookCurlEffect的装订线实现现在还没有很好的策略做出一个平滑的效果。在之后的开发中，考虑对控制点的位置重新设计，并且实现装订线。
- 关于滚动翻页：由于PageEffect处理的AbstractPageContainer的Child view，也就是一个与AbstractPageContainer大小相同的Page，这会使得整个Page（包括页眉、页脚）都在滚动，考虑一下怎么解决
- 长按选择文字功能
  - 问题1：高亮相关信息以上面形式的数据保存呢？文本是否高亮是并不依赖于分页状态的，因此需要保证如果布局参数发生改变、需要重新进行分页时，高亮状态需要延续下去。
  - 问题2：高亮颜色，在不同背景、不同文字颜色时，高亮颜色的显示问题，例如：在白色背景黑色字体下，使用亮黄色作为高亮颜色没问题；但是如果换成黑色背景白色字体，亮黄色的高亮就显得非常突兀。
  - 问题3：如何支持笔记功能呢，笔记的内容以什么形式保存呢，并且需要与正文内容建立某种关系。
- 段评功能：支持章节标题、段落、图片的段评按钮功能。段评按钮绘制方式分为两种，一种是直接绘制一个图标，另一种是作为一个view。另外，段评按钮的位置也分为多种：紧接在段落最后一个字的后面；或者，在x轴方向紧贴在页面的右侧，在y轴方向和段落的最后一行对齐。
- 过度绘制优化
- 支持加载epub并显示：似乎要依赖于WebView实现
- 网络小说加载的步骤：初始化目录、加载指定章节内容、解析章节内容（章节内容可能包含文字和图片）、

## License

This library is licensed under the [MIT License](https://opensource.org/licenses/MIT).
