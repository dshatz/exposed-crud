package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.*

@Entity
data class Category(
    @Id(autoGenerate = true)
    val id: Long,
    @Default("false")
    val adult: Boolean = false,

    @BackReference(CategoryTranslations::class)
    val translations: List<CategoryTranslations>? = null,

    @BackReference(Movie::class)
    val movies: List<Movie>? = null
)
