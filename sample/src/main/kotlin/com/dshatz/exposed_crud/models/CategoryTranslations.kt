package com.dshatz.exposed_crud.models

import com.dshatz.exposed_crud.Entity
import com.dshatz.exposed_crud.Id
import com.dshatz.exposed_crud.ForeignKey
import com.dshatz.exposed_crud.References

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