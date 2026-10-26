package org.athar.app.learning

object PlayerPolicy {
    private val videoPattern = Regex("[A-Za-z0-9_-]{11}")
    fun validVideo(id: String) = videoPattern.matches(id)
    fun safeTime(value: Double): Int? = value.takeIf { it.isFinite() && it in 0.0..86400.0 }?.toInt()
    fun youtubeUrl(video: String, position: Int = 0): String {
        require(validVideo(video))
        return "https://www.youtube.com/watch?v=$video&t=${position.coerceIn(0, 86400)}s"
    }
    fun origin(packageName: String): String { require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]+"))); return "https://$packageName/" }
}
