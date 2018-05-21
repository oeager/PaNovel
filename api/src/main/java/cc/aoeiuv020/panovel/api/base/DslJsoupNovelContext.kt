package cc.aoeiuv020.panovel.api.base

import cc.aoeiuv020.panovel.api.*
import org.jsoup.Connection
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Created by AoEiuV020 on 2018.05.20-16:26:44.
 */
@Suppress("ClassName", "LocalVariableName")
abstract class DslJsoupNovelContext : JsoupNovelContext() {
    /*
    *************** member ***************
     */
    override var detailTemplate: String? = null
        /**
         * 详情页地址模板默认同时赋值给目录页，
         */
        set(value) {
            if (chapterTemplate == null) {
                chapterTemplate = value
            }
            field = value
        }
    override var chapterTemplate: String? = null
    override var contentTemplate: String? = null
    override var bookIdRegex: Pattern = firstIntPattern
    override var chapterIdRegex: Pattern = firstTwoIntPattern

    override var charset: String? = null

    /*
    *************** site ***************
     */
    override lateinit var site: NovelSite

    protected fun site(init: _NovelSite.() -> Unit) {
        _NovelSite().also { _site ->
            _site.init()
            site = _site.createNovelSite()
        }
    }

    protected class _NovelSite {
        lateinit var name: String
        lateinit var baseUrl: String
        lateinit var logo: String
        var enabled: Boolean = true
        fun createNovelSite(): NovelSite = NovelSite(
                name = name,
                baseUrl = baseUrl,
                logo = logo,
                enabled = enabled
        )
    }

    /*
    *************** search ***************
     */
    override fun searchNovelName(name: String): List<NovelItem> =
            _Search(name).initSearch()

    private lateinit var initSearch: _Search.() -> List<NovelItem>
    protected fun search(init: _Search.() -> List<NovelItem>) {
        initSearch = init
    }

    protected inner class _Search(name: String)
        : _Requester(name) {

        fun document(init: _NovelItemListParser.() -> Unit): List<NovelItem> =
                _NovelItemListParser(
                        parse(requireNotNull(connection), charset)
                ).also(init).parse()
    }

    protected inner class _NovelItemListParser(root: Element)
        : _Parser<List<NovelItem>>(root) {
        private lateinit var novelItemList: List<NovelItem>
        // 返回的是不可改的List, 但并不是最终的结果，
        // 最终结果在document外面，需要删改应该在document方法返回后，
        fun items(query: String, init: _NovelItemParser.() -> Unit): List<NovelItem> =
                root.requireElements(query).map {
                    _NovelItemParser(it).run {
                        init()
                        parse()
                    }
                }.also { novelItemList = it }

        override fun parse(): List<NovelItem> = novelItemList
    }

    /*
    *************** detail ***************
     */
    override fun getNovelDetail(extra: String): NovelDetail =
            _Detail(extra).initDetail()

    private lateinit var initDetail: _Detail.() -> NovelDetail
    protected fun detail(init: _Detail.() -> NovelDetail) {
        initDetail = init
    }

    protected inner class _Detail(extra: String) : _Requester(extra) {

        fun document(init: _NovelDetailParser.() -> Unit): NovelDetail =
                _NovelDetailParser(extra,
                        parse(connection ?: connect(getNovelDetailUrl(extra)), charset)
                ).also(init).parse()
    }

    protected inner class _NovelDetailParser(
            // 有需要的话这个可以改public,
            private val detailExtra: String,
            root: Element
    ) : _Parser<NovelDetail>(root) {
        private val _novelDetail = _NovelDetail()

        init {
            // 默认直接将详情页的extra传给目录页，
            // 通常双方都是只需要一个bookId,
            _novelDetail.extra = detailExtra
        }

        var novel: NovelItem?
            get() = _novelDetail.novel
            set(value) {
                _novelDetail.novel = value
            }

        fun novel(init: _NovelItemParser.() -> Unit): NovelItem = _NovelItemParser(root).run {
            // 自己的extra本来就是能用来请求详情页的extra,
            extra = this@_NovelDetailParser.detailExtra
            init()
            parse()
        }.also { novel = it }

        var image: String?
            get() = _novelDetail.image
            set(value) {
                _novelDetail.image = value
            }

        fun image(query: String, block: (Element) -> String = { it.absSrc() }) {
            image = root.requireElement(query = query, name = TAG_IMAGE, block = block)
        }

        var update: Date?
            get() = _novelDetail.update
            set(value) {
                _novelDetail.update = value
            }

        @SuppressWarnings("SimpleDateFormat")
        fun update(query: String, format: String, block: (Element) -> String = { it.text() }) = update(query) {
            val updateString = block(it)
            val sdf = SimpleDateFormat(format)
            sdf.parse(updateString)
        }

        fun update(query: String, block: (Element) -> Date) {
            update = root.getElement(query = query, block = block)
        }

        var introduction: String?
            get() = _novelDetail.introduction
            set(value) {
                _novelDetail.introduction = value
            }

        fun introduction(query: String, block: (Element) -> String = { it.textList().joinToString("\n") }) {
            introduction = root.getElement(query = query, block = block)
        }

        var extra: String?
            get() = _novelDetail.extra
            set(value) {
                _novelDetail.extra = value
            }

        // 目录页一般可以靠bookId拼接地址搞定，如果不行，要调用这个方法，就很可能需要完整地址，所以默认用absHref,
        fun extra(query: String, block: (Element) -> String = { it.absHref() }) {
            extra = root.requireElement(query = query, name = TAG_CHAPTER_PAGE, block = block)
        }

        override fun parse(): NovelDetail = _novelDetail.createNovelDetail()

        private inner class _NovelDetail {
            var novel: NovelItem? = null
            var image: String? = null
            var update: Date? = null
            var introduction: String? = null
            var extra: String? = null
            fun createNovelDetail() = NovelDetail(
                    requireNotNull(novel),
                    requireNotNull(image),
                    update,
                    introduction.toString(),
                    requireNotNull(extra)
            )
        }
    }

    /*
       *************** chapters ***************
        */

    override fun getNovelChaptersAsc(extra: String): List<NovelChapter> =
            _Chapters(extra).initChapters()

    private lateinit var initChapters: _Chapters.() -> List<NovelChapter>
    protected fun chapters(init: _Chapters.() -> List<NovelChapter>) {
        initChapters = init
    }

    protected inner class _Chapters(extra: String) : _Requester(extra) {
        fun document(init: _NovelChapterListParser.() -> Unit): List<NovelChapter> =
                _NovelChapterListParser(
                        parse(connection ?: connect(getNovelChapterUrl(extra)), charset)
                ).also(init).parse()
    }

    protected inner class _NovelChapterListParser(root: Element)
        : _Parser<List<NovelChapter>>(root) {
        private lateinit var novelChapterList: List<NovelChapter>
        // 需要删改解析出来的列表中的元素应该在document返回后，
        // TODO: items方法返回值并没有用，考虑删除，
        fun items(query: String, init: _NovelChapterParser.() -> Unit = {
            name = root.text()
            // 默认从该元素的href路径中找到chapterId，用于拼接章节正文地址，
            extra = findChapterId(root.path())
        }): List<NovelChapter> =
                root.requireElements(query, name = TAG_CHAPTER_LINK).map {
                    _NovelChapterParser(it).run {
                        init()
                        parse()
                    }
                }.also { novelChapterList = it }

        override fun parse(): List<NovelChapter> = novelChapterList
    }

    protected inner class _NovelChapterParser(root: Element)
        : _Parser<NovelChapter>(root) {
        private val _novelChapter = _NovelChapter()
        override fun parse(): NovelChapter = _novelChapter.createNovelChapter()

        var name: String?
            get() = _novelChapter.name
            set(value) {
                _novelChapter.name = value
            }

        fun name(query: String, block: (Element) -> String = { it.text() }) {
            name = root.requireElement(query, block = block)
        }

        var extra: String?
            get() = _novelChapter.extra
            set(value) {
                _novelChapter.extra = value
            }

        fun extra(query: String, block: (Element) -> String = { it.absHref() }) {
            extra = root.requireElement(query, block = block)
        }

        var update: Date?
            get() = _novelChapter.update
            set(value) {
                _novelChapter.update = value
            }

        @SuppressWarnings("SimpleDateFormat")
        fun update(query: String, format: String, block: (Element) -> String = { it.text() }) = update(query) {
            val updateString = block(it)
            val sdf = SimpleDateFormat(format)
            sdf.parse(updateString)
        }

        fun update(query: String, block: (Element) -> Date) {
            update = root.getElement(query = query, block = block)
        }

        private inner class _NovelChapter {
            var name: String? = null
            var extra: String? = null
            var update: Date? = null
            fun createNovelChapter(): NovelChapter = NovelChapter(
                    name = requireNotNull(name),
                    extra = requireNotNull(extra),
                    update = update
            )
        }
    }

    override fun getNovelText(extra: String): NovelText =
            _Content(extra).initContent()

    private lateinit var initContent: _Content.() -> NovelText
    protected fun content(init: _Content.() -> NovelText) {
        initContent = init
    }

    protected inner class _Content(extra: String) : _Requester(extra) {
        fun document(init: _NovelContentParser.() -> Unit): NovelText =
                _NovelContentParser(
                        parse(connection ?: connect(getNovelContentUrl(extra)), charset)
                ).also(init).parse().let {
                    NovelText(it)
                }
    }

    protected inner class _NovelContentParser(root: Element)
        : _Parser<List<String>>(root) {
        private lateinit var novelContent: List<String>
        // 查到的可以是一个元素，也可以是一列元素，
        fun items(query: String, block: (Element) -> List<String> = { it.textList() }) {
            novelContent = root.requireElements(query, name = TAG_CONTENT).flatMap {
                block(it)
            }
        }

        override fun parse(): List<String> = novelContent
    }

    /*
    *************** novel ***************
     */

    protected inner class _NovelItemParser(root: Element) : _Parser<NovelItem>(root) {
        private val _novelItem = _NovelItem()
        var name: String?
            get() = _novelItem.name
            set(value) {
                _novelItem.name = value
            }
        fun name(query: String, block: (Element) -> String = { it.text() }) {
            name = root.requireElement(query = query, name = TAG_NOVEL_NAME) {
                // 为了从列表中拿小说时方便，
                // 尝试从该元素中提取bookId，如果能成功，就不需要调用extra块，
                // 如果是详情页，在这前后传入详情页的extra都可以，
                if (_novelItem.extra == null && it.href().isNotBlank()) {
                    _novelItem.extra = findBookId(it.path())
                }
                block(it)
            }
        }

        var author: String?
            get() = _novelItem.author
            set(value) {
                _novelItem.author = value
            }
        fun author(query: String, block: (Element) -> String = { it.text() }) {
            author = root.requireElement(query = query, name = TAG_AUTHOR_NAME, block = block)
        }

        var extra: String?
            get() = _novelItem.extra
            set(value) {
                _novelItem.extra = value
            }

        fun extra(query: String, block: (Element) -> String = { findBookId(it.path()) }) {
            extra = root.requireElement(query = query, name = TAG_NOVEL_LINK, block = block)
        }

        override fun parse(): NovelItem = _novelItem.createNovelItem()

        private inner class _NovelItem {
            var site: String = this@DslJsoupNovelContext.site.name
            var name: String? = null
            var author: String? = null
            var extra: String? = null
            fun createNovelItem() = NovelItem(
                    site,
                    requireNotNull(name),
                    requireNotNull(author),
                    requireNotNull(extra)
            )
        }
    }

    /*
    *************** parser ***************
     */
    @DslTag
    protected abstract inner class _Parser<out T>(
            val root: Element
    ) {
        abstract fun parse(): T
    }

    /*
    *************** requester ***************
     */
    @DslTag
    protected abstract inner class _Requester(
            val extra: String
    ) {
        var connection: Connection? = null
        // 指定响应的编码，用于jsoup解析html时，
        // 不是参数的编码，参数不进行额外的URLEncode,
        var charset: String? = this@DslJsoupNovelContext.charset

        fun get(init: _Request.() -> Unit): Connection = _Request().run {
            method = Connection.Method.GET
            init()
            createConnection()
        }.also { connection = it }

        fun post(init: _Request.() -> Unit): Connection = _Request().run {
            method = Connection.Method.POST
            init()
            createConnection()
        }.also { connection = it }
    }

    //    @DslTag
    // 这个不做为dsl标签，为了能直接访问外面的变量，比如extra,
    protected inner class _Request {
        var url: String? = null
        var method: Connection.Method? = null
        var dataMap: Map<String, String>? = null
        fun createConnection(): Connection = connect(absUrl(requireNotNull(url)))
                .method(requireNotNull(method))
                .apply { if (dataMap != null) data(dataMap) }

        /**
         * TODO: 这种方法装载参数的话，如果是get, jsoup写死URLEncode编码utf-8, 需要用到的话就整个改okhttp吧，
         */
        @Deprecated("如果是get, jsoup写死URLEncode编码utf-8, 先别用，")
        fun data(init: _Data.() -> Unit) {
            _Data().also { _data ->
                _data.init()
                dataMap = _data.createDataMap()
            }
        }

        inner class _Data {
            val map: MutableMap<String, String> = mutableMapOf()
            fun createDataMap(): Map<String, String> = map
            operator fun Pair<String, String>.unaryPlus() {
                map[first] = second
            }
        }
    }


    /*
    *************** annotation ***************
     */
    @DslMarker
    annotation class DslTag
}
