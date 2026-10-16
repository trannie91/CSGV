import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

android {
    namespace = "com.glvietsub"
}

version = 1

cloudstream {
    authors = listOf("GLVietsub")
    description = "Extension xem phim BL - Chỉ lấy duy nhất Catalog Phim BL, tự động bóc tách link 1080p FHD từ máy chủ Rumble Cloud, OK.ru, VK, loại bỏ chất lượng thấp."
    status = 1
    tvTypes = listOf("TvSeries", "AsianDrama")
    language = "vi"
    iconUrl = "https://www.glvietsubz.net/favicon.ico"
}

