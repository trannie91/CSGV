package com.glvietsub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class GLVietsubPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký Provider chính chỉ lấy duy nhất Catalog Phim BL từ https://www.glvietsubz.net/the-loai/phim-bl-dam-my
        registerMainAPI(GLVietsubProvider())

        // Đăng ký các Extractor trích xuất link 1080p FHD cao nhất, loại bỏ luồng chất lượng thấp
        registerExtractorAPI(OkRuExtractor())
        registerExtractorAPI(VkExtractor())
        registerExtractorAPI(DailymotionExtractor())
    }
}
