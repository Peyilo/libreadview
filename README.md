# libreadview

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.peyilo/libreadview/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.peyilo/libreadview)

An Android library designed specifically for eBook and novel reader apps, supporting multiple immersive page turning modes including scrolling, cover, simulation, and more.

![readview_loading](./images/readview_loading.png)

## Demo

To quickly see the page turning animations and layout in action, you can download the demo app:

 [Download demo.apk](https://github.com/Peyilo/libreadview/releases/download/0.0.2/demo)

## Features

- Multiple page-turning animations, including cover page turn, horizontal slide, simulation, scrolling, and more.
- Page container layout to manage reading flow
- Optimized for performance and customization

![readview_page_turning](./images/readview_page_turning.png)

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
    implementation("io.github.peyilo:libreadview:0.0.2")
}
```
<details> <summary><b>Via Maven</b></summary></details>

Or Maven:

```xml
<dependency>
    <groupId>io.github.peyilo</groupId>
    <artifactId>libreadview</artifactId>
    <version>0.0.2</version>
</dependency>
```

## Usage

### PageContainer

`PageContainer` works similarly to `RecyclerView`. You provide an `Adapter` and select a page flip animation style to get started quickly.

#### 1. Add `PageContainer` in XML

```xml
<!-- res/layout/activity_page_container.xml -->
<org.peyilo.libreadview.PageContainer
    android:id="@+id/pageContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

####  2. Initialize in Kotlin

```kotlin
class PageContainerActivity : AppCompatActivity() {

    private lateinit var pageContainer: PageContainer
    private val colors = mutableListOf<Pair<Int, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_page_container)
        supportActionBar?.hide()

        // Generate demo data: 1000 pages with random background colors and numbers
        repeat(1000) {
            val randomColor = Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
            colors.add(Pair(randomColor, it + 1))
        }

        pageContainer = findViewById(R.id.pageContainer)

        // Set the initial page index
        pageContainer.initPageIndex(1)

        // Choose a page animation manager (see options below)
        pageContainer.layoutManager = SimulationPageManagers.Style1()

        // Set adapter
        pageContainer.adapter = ColorAdapter(colors)

        // Handle tap regions: left 30% = previous, right 30% = next
        pageContainer.setOnClickRegionListener { xPercent, _ ->
            when (xPercent) {
                in 0..30 -> pageContainer.flipToPrevPage()
                in 70..100 -> pageContainer.flipToNextPage()
                else -> return@setOnClickRegionListener false
            }
            true
        }
    }
}
```

#### 3. Implement Adapter

```kotlin
class ColorAdapter(private val items: List<Pair<Int, Int>>) :
        AbstractPageContainer.Adapter<ColorAdapter.ColorViewHolder>() {

    inner class ColorViewHolder(itemView: View) : AbstractPageContainer.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = GridPage(parent.context)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val itemView = holder.itemView as GridPage
        itemView.setBackgroundColor(items[position].first)
        itemView.content.number = items[position].second
        itemView.progress.text = "${position + 1}/${items.size}"
        itemView.header.text = "这是第${position + 1}页"
    }

    override fun getItemCount(): Int = items.size
}
```

### ReadView

#### 1. Add `SimpleReadView` in XML

```xml
<org.peyilo.libreadview.SimpleReadView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/readview"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".ReadViewActivity"/>
```

#### 2. Initialize in Kotlin

```kotlin
class ReadViewActivity : AppCompatActivity() {

    private lateinit var readview: SimpleReadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read_view)
        supportActionBar?.hide()
        
        // Specify the path to the local text file
        val filePath = "/path/to/your/txtfile"
        val selectedFile = File(filePath)
        
        readview = findViewById(R.id.readview)
        readview.layoutManager = ScrollLayoutManager()      // Set the page turning mode to scrolling
           
        readview.openBook(
            SimpleNativeLoader(selectedFile).apply {
                addTitleRegex("第\\d+章 .*")       // Set the regular expression to match chapter titles
            },
            chapIndex = 100,
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

### Available LayoutManagers

- CoverLayoutManager()
- IBookSlideLayoutManager()
- SlideLayoutManager()
- NoAnimLayoutManagers.Horizontal()
- NoAnimLayoutManagers.Vertical()
- SimulationLayoutManagers.Style1()
- ScrollLayoutManager()

### TODO

- Long-press word capture function
- Paragraph comment function
- Over-drawing optimization 

## License

This library is licensed under the [MIT License](https://opensource.org/licenses/MIT).
