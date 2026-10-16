package com.glvietsub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

class GLVietsubProvider : MainAPI() {
    override var mainUrl = "https://www.glvietsubz.net"
    override var name = "GLVietsub"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Chỉ lấy đúng duy nhất Catalog Phim BL theo yêu cầu (không lấy bất kỳ Catalog nào khác)
    override val mainPage = mainPageOf(
        "the-loai/phim-bl-dam-my" to "Phim BL (Đam Mỹ)"
    )

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun cleanTitle(str: String): String {
        return str
            .replace(Regex("""^\[+|\]+$"""), "")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&#038;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""<[^>]+>"""), "")
            .trim()
    }

    private fun enhancePoster(url: String): String {
        if (url.isEmpty() || url.startsWith("data:image")) return ""
        var poster = url.trim().replace("&amp;", "&")
        if (poster.startsWith("//")) {
            poster = "https:$poster"
        }
        return fixUrl(poster)
    }

    private fun extractPoster(element: Element): String {
        val img = element.selectFirst("img")
        var poster = ""

        if (img != null) {
            val candidates = listOf(
                img.attr("data-src"),
                img.attr("data-lazy-src"),
                img.attr("data-original"),
                img.attr("data-srcset"),
                img.attr("srcset"),
                img.attr("src")
            )

            for (cand in candidates) {
                val trimmed = cand.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("data:image") && !trimmed.contains("blank.gif") && !trimmed.contains("placeholder")) {
                    poster = if (trimmed.contains(",")) {
                        trimmed.split(",").lastOrNull()?.trim()?.split(" ")?.firstOrNull() ?: ""
                    } else if (trimmed.contains(" ")) {
                        trimmed.split(" ").firstOrNull() ?: ""
                    } else {
                        trimmed
                    }
                    if (poster.isNotEmpty() && !poster.startsWith("data:image")) break
                }
            }
        }

        return enhancePoster(poster)
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val catalogPath = request.data.ifEmpty { "the-loai/phim-bl-dam-my" }
        val targetUrl = if (page <= 1) {
            "$mainUrl/$catalogPath"
        } else {
            "$mainUrl/$catalogPath/page/$page"
        }

        val items = mutableListOf<SearchResponse>()
        var hasNext = false

        try {
            val doc = app.get(
                targetUrl,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to "$mainUrl/"
                )
            ).document

            val articles = doc.select("article.item, article")
            for (art in articles) {
                val linkEl = art.selectFirst("a[href*='/phim-bo/'], a[href*='/phim/'], a") ?: continue
                val href = fixUrl(linkEl.attr("href"))
                if (href.isEmpty() || href == "$mainUrl/" || href.contains("/the-loai/")) continue

                val rawTitle = art.selectFirst(".dtinfo-title a, h4 a, h3 a, .title a, img")?.let {
                    it.attr("alt").ifEmpty { it.text() }
                } ?: art.selectFirst("img")?.attr("alt") ?: ""

                if (rawTitle.isEmpty()) continue
                val title = cleanTitle(rawTitle)
                val poster = extractPoster(art)

                // Trạng thái tập / chất lượng
                val epsSeries = art.selectFirst(".epseries, .quality")?.text()?.trim() ?: ""

                val response = newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
                    this.posterUrl = poster
                    if (epsSeries.isNotEmpty()) {
                        this.quality = SearchQuality.HD
                    }
                }
                items.add(response)
            }

            // Kiểm tra phân trang tiếp theo
            val pagination = doc.selectFirst(".pagination")
            if (pagination != null) {
                val nextLink = pagination.selectFirst("a.arrow_pag, a:contains(Sau), a.inactive:contains(${page + 1})")
                hasNext = nextLink != null
            } else {
                hasNext = items.size >= 20
            }
        } catch (_: Throwable) {}

        val distinctItems = items.distinctBy { it.url }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    name = request.name,
                    list = distinctItems,
                    isHorizontalImages = false
                )
            ),
            hasNext = hasNext
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.trim().isEmpty()) return emptyList()

        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val searchUrl = "$mainUrl/?s=$encodedQuery"
        val results = mutableListOf<SearchResponse>()

        try {
            val doc = app.get(
                searchUrl,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to "$mainUrl/"
                )
            ).document

            val articles = doc.select("article.item, .result-item article, article")
            for (art in articles) {
                val linkEl = art.selectFirst("a[href*='/phim-bo/'], a[href*='/phim/'], a") ?: continue
                val href = fixUrl(linkEl.attr("href"))
                if (href.isEmpty() || href == "$mainUrl/" || href.contains("/the-loai/")) continue

                val rawTitle = art.selectFirst(".dtinfo-title a, h4 a, h3 a, .title a, img")?.let {
                    it.attr("alt").ifEmpty { it.text() }
                } ?: art.selectFirst("img")?.attr("alt") ?: ""

                if (rawTitle.isEmpty()) continue
                val title = cleanTitle(rawTitle)
                val poster = extractPoster(art)

                results.add(
                    newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
                        this.posterUrl = poster
                    }
                )
            }
        } catch (_: Throwable) {}

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document

        // 1. Tiêu đề phim
        val rawTitle = doc.selectFirst(".sheader .data h1, .data h1, h1.entry-title, h1")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.split("-")?.firstOrNull()?.trim()
            ?: return null
        val title = cleanTitle(rawTitle)

        // 2. Poster phim
        val posterEl = doc.selectFirst("img.posterimage, .poster img, .sheader img")
        val poster = if (posterEl != null) {
            extractPoster(posterEl)
        } else {
            val ogImg = doc.selectFirst("meta[property='og:image'], meta[name='twitter:image']")?.attr("content") ?: ""
            enhancePoster(ogImg)
        }

        // 3. Nội dung mô tả tóm tắt
        val description = doc.selectFirst(".mieuta .mota, [itemprop=description], .wp-content")?.text()?.trim()
            ?: doc.selectFirst("meta[name=description], meta[property='og:description']")?.attr("content")?.trim()
            ?: title

        // 4. Thông tin diễn viên & năm phát hành
        val year = doc.selectFirst(".thongtin span:contains(20), .show-year")?.text()?.let {
            Regex("""\d{4}""").find(it)?.value?.toIntOrNull()
        }
        val actors = doc.select(".cast, [itemprop=actor]").map { it.text().trim() }.filter { it.isNotEmpty() }
        val tags = doc.select(".danhmuctheloai a, a[href*='/the-loai/']").map { it.text().trim() }.distinct()

        // 5. Trích xuất TOÀN BỘ tập phim từ danh sách #episodes
        val episodes = mutableListOf<Episode>()
        val epElements = doc.select("#episodes .episodios li, .episodios li, li[class*='mark-']")

        for ((idx, epEl) in epElements.withIndex()) {
            val aTag = epEl.selectFirst(".episodiotitle a, a") ?: continue
            val watchUrl = fixUrl(aTag.attr("href"))
            if (watchUrl.isEmpty()) continue

            val rawEpText = aTag.text().trim()
            val epNumMatch = Regex("""\d+""").find(rawEpText)
            val epNum = epNumMatch?.value?.toIntOrNull() ?: (idx + 1)

            val epName = if (rawEpText.isNotEmpty()) {
                cleanTitle(rawEpText)
            } else {
                "Tập $epNum"
            }

            episodes.add(
                newEpisode(watchUrl) {
                    this.name = epName
                    this.episode = epNum
                }
            )
        }

        // Trường hợp phim lẻ hoặc trang xem trực tiếp không có danh sách li #episodes
        if (episodes.isEmpty()) {
            val watchLink = doc.selectFirst("a[href*='/xem-phim/']")?.attr("href")?.let { fixUrl(it) } ?: url
            episodes.add(
                newEpisode(watchLink) {
                    this.name = "Full Movie (1080p FHD)"
                    this.episode = 1
                }
            )
        }

        val distinctEpisodes = episodes.distinctBy { it.data }.sortedBy { it.episode }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, distinctEpisodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.actors = actors.map { ActorData(Actor(it)) }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val watchUrl = data.trim()
        if (watchUrl.isEmpty()) return false

        var linkFound = false

        try {
            val watchDoc = app.get(watchUrl, headers = mapOf("User-Agent" to userAgent)).document
            val playerOptions = watchDoc.select("li.dooplay_player_option, li[id*='player-option']")

            for (opt in playerOptions) {
                val post = opt.attr("data-post").ifEmpty {
                    Regex("""data-post=['"]([^'"]+)['"]""").find(opt.outerHtml())?.groupValues?.get(1) ?: ""
                }
                val nume = opt.attr("data-nume").ifEmpty {
                    Regex("""data-nume=['"]([^'"]+)['"]""").find(opt.outerHtml())?.groupValues?.get(1) ?: ""
                }
                val type = opt.attr("data-type").ifEmpty { "tv" }

                if (post.isEmpty() || nume.isEmpty()) continue

                try {
                    // Gọi API DooPlay Player để lấy link máy chủ nhúng thực tế
                    val ajaxRes = app.post(
                        "$mainUrl/wp-admin/admin-ajax.php",
                        headers = mapOf(
                            "User-Agent" to userAgent,
                            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                            "Referer" to watchUrl,
                            "X-Requested-With" to "XMLHttpRequest"
                        ),
                        data = mapOf(
                            "action" to "doo_player_ajax",
                            "post" to post,
                            "nume" to nume,
                            "type" to type
                        )
                    ).text

                    val json = JSONObject(ajaxRes)
                    var embedUrl = json.optString("embed_url")
                    if (embedUrl.isEmpty()) continue

                    // Xử lý iframe bọc ngoài nếu có
                    val iframeSrc = Regex("""src=["']([^"']+)["']""").find(embedUrl)?.groupValues?.get(1)
                    if (!iframeSrc.isNullOrEmpty()) {
                        embedUrl = iframeSrc
                    }

                    // 1. Máy chủ Rumble Cloud FHD 1080p (Thường ở Server 1 của GLVietsub)
                    if (embedUrl.contains("watch?source=")) {
                        val sourceParam = Regex("""source=([^&]+)""").find(embedUrl)?.groupValues?.get(1)
                        if (!sourceParam.isNullOrEmpty()) {
                            val directStreamUrl = URLDecoder.decode(sourceParam, "UTF-8")
                            val isM3u8 = directStreamUrl.contains(".m3u8") || directStreamUrl.contains("chunklist")

                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "Server 1 (Rumble Cloud) - 1080p FHD",
                                    url = directStreamUrl,
                                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = Qualities.P1080.value
                                    this.referer = "$mainUrl/"
                                    this.headers = mapOf(
                                        "User-Agent" to userAgent,
                                        "Referer" to "$mainUrl/"
                                    )
                                }
                            )
                            linkFound = true
                        }
                    }

                    // 2. Máy chủ OK.ru (Chỉ lấy chất lượng 1080p FHD, loại bỏ các chất lượng thấp)
                    else if (embedUrl.contains("ok.ru")) {
                        try {
                            val okExtractor = OkRuExtractor()
                            okExtractor.getUrl(embedUrl, watchUrl, subtitleCallback) { link ->
                                linkFound = true
                                callback.invoke(link)
                            }
                        } catch (_: Throwable) {}
                    }

                    // 3. Máy chủ VK (Chỉ lấy chất lượng 1080p FHD)
                    else if (embedUrl.contains("vk.com") || embedUrl.contains("vk.ru") || embedUrl.contains("vkvideo.ru")) {
                        try {
                            val vkExtractor = VkExtractor()
                            vkExtractor.getUrl(embedUrl, watchUrl, subtitleCallback) { link ->
                                linkFound = true
                                callback.invoke(link)
                            }
                        } catch (_: Throwable) {}
                    }

                    // 4. Máy chủ Dailymotion
                    else if (embedUrl.contains("dailymotion.com")) {
                        try {
                            val dmExtractor = DailymotionExtractor()
                            dmExtractor.getUrl(embedUrl, watchUrl, subtitleCallback) { link ->
                                linkFound = true
                                callback.invoke(link)
                            }
                        } catch (_: Throwable) {}
                    }

                    // 5. Đường dẫn m3u8 hoặc mp4 trực tiếp chất lượng cao
                    else if (embedUrl.contains(".m3u8") || embedUrl.contains(".mp4")) {
                        val isM3u8 = embedUrl.contains(".m3u8")
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "Server Dự Phòng - 1080p FHD",
                                url = embedUrl,
                                type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.quality = Qualities.P1080.value
                                this.referer = "$mainUrl/"
                                this.headers = mapOf(
                                    "User-Agent" to userAgent,
                                    "Referer" to "$mainUrl/"
                                )
                            }
                        )
                        linkFound = true
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        return linkFound
    }
}
