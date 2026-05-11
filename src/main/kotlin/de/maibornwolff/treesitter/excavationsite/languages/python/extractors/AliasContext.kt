package de.maibornwolff.treesitter.excavationsite.languages.python.extractors

internal data class AliasContext(val fromImports: Map<String, String>, val standardImports: Map<String, String>)
