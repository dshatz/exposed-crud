package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.Entity
import com.dshatz.exposeddataclass.Id
import com.dshatz.exposeddataclass.References

@Entity
data class CategoryTranslations(
    @Id
    @References(Category::class)
    val categoryId: Long,

    @Id
    @References(Language::class)
    val languageCode: String,

    val translation: String
)