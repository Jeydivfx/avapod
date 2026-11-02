package com.avapod.app.utils

import com.avapod.app.models.Category

/** Stable additive IDs: existing Firebase IDs and podcast assignments are never replaced. */
object CategoryCatalog {
    val defaults = listOf(
        Triple("society", "جامعه و فرهنگ", "community"),
        Triple("crime", "جنایی", "crime"), Triple("horror", "ترسناک", "horror"),
        Triple("science", "علم و دانش", "science"), Triple("cinema", "سینما و تلویزیون", "cinema"),
        Triple("history", "تاریخ", "history"), Triple("literature", "ادبیات و کتاب", "book"),
        Triple("philosophy", "فلسفه", "thought"), Triple("future", "آینده‌پژوهی", "future"),
        Triple("politics", "سیاست", "politics"), Triple("sport", "ورزش", "sport"),
        Triple("music", "موسیقی", "music"), Triple("story", "داستان", "audio"),
        Triple("psychology", "روان‌شناسی", "psychology"), Triple("health", "سلامت", "health"),
        Triple("technology", "فناوری", "technology"), Triple("economy", "اقتصاد", "economy"),
        Triple("business", "کسب‌وکار", "business"), Triple("learning", "آموزش", "learning"),
        Triple("language", "یادگیری زبان", "language"), Triple("travel", "سفر", "travel"),
        Triple("nature", "طبیعت و محیط زیست", "nature"), Triple("comedy", "طنز", "comedy"),
        Triple("family", "خانواده و فرزندپروری", "family"), Triple("art", "هنر و طراحی", "art"),
        Triple("spirituality", "معنویت", "spirituality"), Triple("biography", "زندگی‌نامه", "biography"),
        Triple("food", "آشپزی و خوراک", "food"), Triple("games", "بازی‌های ویدیویی", "games"),
        Triple("astronomy", "نجوم و فضا", "astronomy")
    ).map { (id, name, icon) -> Category("avapod_cat_$id", name, "ic_category_$icon") }

    fun displayName(name: String): String {
        val key = PodcastOrder.searchKey(name).replace("‌", "").replace(" ", "")
        return when (key) {
            "جامعه", "اجتماعی", "جامعهوفرهنگ", "فرهنگ" -> "جامعه"
            "علم", "علمی", "دانش", "علمودانش", "علمیدانستنی", "علمیودانستنی" -> "علم"
            "سینما", "فیلموسریال", "سینماوتلویزیون" -> "سینما"
            "ادبیات", "کتاب", "کتابوادبیات", "ادبیاتوکتاب", "کتابصوتی" -> "ادبیات"
            "روانشناسی", "روانشناسیوتوسعهفردی" -> "روان‌شناسی"
            "تکنولوژی" -> "فناوری"
            "پیشگویی", "آخرالزمان", "آیندهپژوهی" -> "آینده‌پژوهی"
            "ترس", "وحشت" -> "ترسناک"
            "فلسفهوتفکر" -> "فلسفه"
            "یادگیریزبان", "آموزشزبان", "زبان" -> "زبان"
            "طبیعتومحیطزیست", "محیطزیست", "طبیعت" -> "طبیعت"
            "خانوادهوفرزندپروری", "فرزندپروری", "خانواده" -> "خانواده"
            "هنروطراحی", "فرهنگوهنر", "هنر" -> "هنر"
            "آشپزیوخوراک", "خوراک", "آشپزی" -> "آشپزی"
            "بازیهایویدیویی", "بازی", "گیم" -> "بازی"
            "نجوموفضا", "نجوم", "فضا" -> "نجوم"
            "زندگینامه" -> "زندگی‌نامه"
            "کسبوکار" -> "کسب‌وکار"
            else -> name.trim().replace('ي', 'ی').replace('ك', 'ک').split(Regex("\\s+")).joinToString("‌")
        }
    }
    private fun canonical(name: String) = displayName(name).replace("‌", "")
    fun complete(existing: List<Category>): List<Category> {
        // Keep all original IDs in a group so deduplication never hides their podcasts.
        val result = existing.groupBy { canonical(it.name) }.values.map { group ->
            group.first().copy(name = displayName(group.first().name),
                sourceIds = group.flatMap { listOf(it.id) + it.sourceIds }.distinct())
        }.toMutableList()
        val names = existing.map { canonical(it.name) }.toMutableSet()
        val ids = existing.map { it.id }.toMutableSet()
        for (category in defaults) {
            if (result.size >= 30) break
            if (canonical(category.name) !in names && category.id !in ids) {
                result += category.copy(name = displayName(category.name), sourceIds = listOf(category.id))
                names += canonical(category.name)
                ids += category.id
            }
        }
        return result
    }
}
