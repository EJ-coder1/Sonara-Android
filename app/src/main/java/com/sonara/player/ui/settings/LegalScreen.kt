package com.sonara.player.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private sealed interface Block {
    data class Title(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Body(val text: String) : Block
}

private fun parseBlocks(raw: String): List<Block> =
    raw.replace("\r\n", "\n").split(Regex("\n\\s*\n")).mapNotNull { chunk ->
        val t = chunk.trim()
        when {
            t.isEmpty() -> null
            t.startsWith("# ") -> Block.Title(t.removePrefix("# "))
            t.startsWith("## ") -> Block.Heading(t.removePrefix("## "))
            else -> Block.Body(t)
        }
    }

/** doc = "privacy", "terms" or "licenses". Text lives in assets/legal and works offline. */
@Composable
fun LegalScreen(
    doc: String,
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val (title, file) = when (doc) {
        "privacy" -> "Privacy Policy" to "privacy_policy.txt"
        "terms" -> "Terms of Use" to "terms_of_use.txt"
        else -> "Licenses and credits" to "licenses.txt"
    }
    val blocks = remember(file) {
        val text = try {
            context.assets.open("legal/$file").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "This document couldn't be loaded."
        }
        parseBlocks(text)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            items(blocks) { block ->
                when (block) {
                    is Block.Title -> Text(
                        block.text,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    is Block.Heading -> Text(
                        block.text,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    )
                    is Block.Body -> Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}
