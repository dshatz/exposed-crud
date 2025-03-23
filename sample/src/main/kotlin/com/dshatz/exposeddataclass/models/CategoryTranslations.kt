package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.Entity
import com.dshatz.exposeddataclass.Id
import com.dshatz.exposeddataclass.ForeignKey

@Entity
data class CategoryTranslations(
    @Id
    val msgId: Long,
    @Id
    @ForeignKey(Language::class)
    val languageCode: String,

    val translation: String
)