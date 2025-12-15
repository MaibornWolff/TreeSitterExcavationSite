@file:Suppress("unused")

package de.maibornwolff.treesitter.excavationsite.api

import de.maibornwolff.treesitter.excavationsite.languages.Language

/**
 * Re-export Language from shared/languages for public API usage.
 *
 * This type alias allows external code to import Language from the api package
 * while the actual implementation lives in shared/languages.
 */
typealias Language = Language
