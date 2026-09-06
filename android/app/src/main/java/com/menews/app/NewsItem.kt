package com.menews.app

data class NewsItem(
    val guid: String = "",
    val title: String = "",
    val summary: String? = null,
    val link: String = "",
    val image: String? = null,
    val category: String? = null,
    val source_id: String? = null,
    val source_name: String? = null,
    val source_cat: String? = null,
    val published: Long = 0,
    val detail: String? = null
)
