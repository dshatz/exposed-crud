package com.dshatz.exposeddataclass

import com.google.devtools.ksp.symbol.KSFunctionDeclaration

data class JsonFormatModel(
    val formats: Map<String, KSFunctionDeclaration>
) {
}