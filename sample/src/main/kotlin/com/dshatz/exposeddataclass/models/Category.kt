package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.Entity
import com.dshatz.exposeddataclass.ForeignKey
import com.dshatz.exposeddataclass.Id
import com.dshatz.exposeddataclass.References

@Entity
data class Category(
    @Id(autoGenerate = true)
    val id: Long,
    @ForeignKey(CategoryTranslations::class, "msgId")
    val msgId: Long,
)
