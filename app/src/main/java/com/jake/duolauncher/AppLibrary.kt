@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jake.duolauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import kotlinx.coroutines.delay

@Composable
internal fun AppLibrary(
    state: LauncherState, query: String, onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit, onPin: (String, Boolean) -> Unit, onActions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier, editing: Boolean = false,
    drag: HomeDragState? = null, page: Int? = null,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onTurnOnWork: (Long) -> Unit = {},
    isVisible: Boolean = true,
) {
    val glass = !editing
    val palette = LocalDuoPalette.current
    val ink = if (glass) Ink else MaterialTheme.colorScheme.onSurface
    val pinned = remember(state.homeSlots, state.leadingSlots) {
        (state.homeSlots.asSequence() + state.leadingSlots.asSequence()).filterNotNull().toSet()
    }
    val hasWork = state.profiles.any { it.isWork } || state.apps.any { it.isWork }
    var showWork by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val selectedProfile = if (showWork) state.profiles.firstOrNull { it.isWork } else state.profiles.firstOrNull { it.isPersonal }
    LaunchedEffect(showWork, selectedProfile?.available, selectedProfile?.quiet) {
        gridState.scrollToItem(0)
    }
    val visibleApps = remember(state.apps, query, showWork, hasWork) {
        state.apps.filter { (!hasWork || it.isWork == showWork) && it.label.contains(query.trim(), true) }
    }
    val groups = remember(visibleApps) {
        visibleApps.groupBy {
            it.label.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
        }
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(120)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    Surface(modifier, shape = RoundedCornerShape(24.dp),
        color = if (glass) Glass.copy(alpha = .48f) else MaterialTheme.colorScheme.surface,
        contentColor = ink,
        border = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = .38f)) else null) {
        Column(Modifier.background(Brush.verticalGradient(if (glass)
            listOf(Color.White.copy(alpha = .09f), Color.Transparent) else listOf(Color.Transparent, Color.Transparent)))
            .padding(horizontal = 16.dp).padding(top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Choose home apps" else "All apps", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(if (editing) "${pinned.size} pinned" else "${visibleApps.size}", color = ink, fontSize = 12.sp)
            }
            if (hasWork) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showWork, onClick = { showWork = false }, label = { Text("Personal") })
                FilterChip(selected = showWork, onClick = { showWork = true }, label = { Text("Work") })
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .focusRequester(focusRequester)
                    .clickable {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    .testTag(if (editing) "pin-search" else "library-search"),
                placeholder = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Rounded.Close, "Clear search")
                    }
                },
                colors = if (glass) OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink,
                    focusedContainerColor = Color.White.copy(alpha = .18f), unfocusedContainerColor = Color.White.copy(alpha = .12f),
                    focusedBorderColor = Color.White.copy(alpha = .8f), unfocusedBorderColor = Color.White.copy(alpha = .45f),
                    focusedPlaceholderColor = ink, unfocusedPlaceholderColor = ink,
                    focusedLeadingIconColor = ink, unfocusedLeadingIconColor = ink,
                    focusedTrailingIconColor = ink, unfocusedTrailingIconColor = ink,
                ) else OutlinedTextFieldDefaults.colors()
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).testTag("all-apps-list"),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (showWork && selectedProfile?.available == false) item(span = { GridItemSpan(2) }, key = "work-paused") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selectedProfile.quiet) "Work apps are paused" else "Work profile is unavailable")
                        if (selectedProfile.quiet) Button(onClick = { onTurnOnWork(selectedProfile.userSerial) },
                            Modifier.padding(top = 10.dp).testTag("turn-on-work")) { Text("Turn on work apps") }
                    }
                }
                if (groups.isEmpty()) item(span = { GridItemSpan(2) }) {
                    Text(if (state.loading) "Loading apps…" else "No apps found", Modifier.padding(vertical = 20.dp))
                }
                groups.forEach { (letter, entries) ->
                    item(span = { GridItemSpan(2) }, key = "heading-$letter") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 32.dp, height = 26.dp).background(
                                if (glass) (if (palette.dark) Color(0xFF314852) else Color(0xFFB7CBD3))
                                else MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Text(letter, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            if (glass) HorizontalDivider(Modifier.weight(1f).padding(start = 10.dp), color = Color.White.copy(alpha = .24f))
                        }
                    }
                    items(entries, key = { it.id }) { app ->
                        val isPinned = app.id in pinned
                        val launchBounds = remember { android.graphics.Rect() }
                        val dragModifier = if (drag != null) Modifier.dropRegion(drag, DropTarget.Library(app.id), app.id, page) else Modifier
                        val click = { if (editing) onPin(app.id, !isPinned) else onLaunchFrom(app, launchBounds) }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = if (glass) .12f else .06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .then(dragModifier)
                                .clip(RoundedCornerShape(16.dp))
                                .testTag("library-app-${app.id}")
                                .then(if (drag == null) Modifier.combinedClickable(onClick = click, onLongClick = { onActions(app) })
                                    else Modifier.clickable(onClick = click).semantics { onLongClick("App options") { onActions(app); true } })
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(app.imageBitmap, null, Modifier.size(38.dp)
                                    .onGloballyPositioned { launchBounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(RoundedCornerShape(10.dp)))
                                Text(app.label, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ink)
                                if (editing) IconButton(onClick = { onPin(app.id, !isPinned) }, Modifier.size(24.dp).testTag("pin-${app.id}")) {
                                    Icon(if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                        if (isPinned) "Remove ${app.label} from home" else "Pin ${app.label} to home",
                                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
