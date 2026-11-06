package com.sonara.player.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.Moods
import com.sonara.player.ui.OnlineState
import com.sonara.player.ui.SearchViewModel
import com.sonara.player.ui.SongsState
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.components.MessageCard
import com.sonara.player.ui.components.SectionHeader
import com.sonara.player.ui.components.ShimmerRow
import com.sonara.player.ui.components.TrackRow
import androidx.compose.material.icons.rounded.SearchOff

@Composable
fun SearchScreen(
    padding: PaddingValues,
    appVm: AppViewModel,
    vm: SearchViewModel = viewModel(),
) {
    val player = LocalPlayer.current
    val focus = LocalFocusManager.current

    val query by vm.query.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val songsState by appVm.songs.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()

    // "Go to artist / album" from the track menu pre-fills the search box.
    val request by appVm.searchRequest.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        request?.let {
            vm.setQuery(it)
            appVm.consumeSearchRequest()
        }
    }

    val q = query.trim()
    val songs = (songsState as? SongsState.Ready)?.songs.orEmpty()
    val localMatches = remember(songs, q) {
        if (q.length < 2) emptyList()
        else songs.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }.take(50)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "title") {
            Text(
                "Search",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item(key = "field") {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Songs, artists or albums") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            )
        }

        if (q.length < 2) {
            item(key = "hint") {
                SectionHeader(
                    "Start typing to search",
                    subtitle = if (vm.isConfigured) "Searches your phone and free music online." else "Searches the music on your phone.",
                )
            }
            if (vm.isConfigured) {
                item(key = "suggestions") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(Moods, key = { it.label }) { mood ->
                            AssistChip(onClick = { vm.setQuery(mood.tag) }, label = { Text(mood.label) })
                        }
                    }
                }
            }
        } else {
            // ----- on this phone -----
            if (localMatches.isNotEmpty()) {
                item(key = "local-header") { SectionHeader("On your phone") }
                items(localMatches, key = { "l-" + it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.id == track.id,
                        onClick = { player.playQueue(localMatches, localMatches.indexOf(track)) },
                    )
                }
            }

            // ----- online -----
            if (vm.isConfigured) {
                item(key = "online-header") { SectionHeader("Free music online") }
                when (val s = online) {
                    OnlineState.Idle, OnlineState.Loading -> item(key = "online-loading") { ShimmerRow() }
                    is OnlineState.Error -> item(key = "online-error") {
                        MessageCard(s.message, actionLabel = "Retry", onAction = vm::retry)
                    }
                    is OnlineState.Ready ->
                        if (s.tracks.isEmpty()) {
                            item(key = "online-empty") {
                                MessageCard("No online results for \"$q\".", icon = Icons.Rounded.SearchOff)
                            }
                        } else {
                            items(s.tracks, key = { "o-" + it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    isCurrent = playerState.current?.id == track.id,
                                    onClick = { player.playQueue(s.tracks, s.tracks.indexOf(track)) },
                                )
                            }
                        }
                }
            } else if (localMatches.isEmpty()) {
                item(key = "none") {
                    MessageCard("No songs on your phone match \"$q\".", icon = Icons.Rounded.SearchOff)
                }
            }
        }
    }
}
