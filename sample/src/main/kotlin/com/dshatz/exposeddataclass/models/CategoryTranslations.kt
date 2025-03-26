package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.Entity
import com.dshatz.exposeddataclass.Id
import com.dshatz.exposeddataclass.ForeignKey
import com.dshatz.exposeddataclass.References

@Entity
data class CategoryTranslations(
    @Id
    @ForeignKey(Category::class)
    val categoryId: Long,
    @Id
    @ForeignKey(Language::class)
    val languageCode: String,
    val translation: String,

    @References(Category::class, "categoryId")
    val category: Category? = null,

    @References(Language::class, "languageCode")
    val language: Language? = null
)