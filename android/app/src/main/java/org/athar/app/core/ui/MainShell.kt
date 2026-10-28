package org.athar.app.core.ui

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.AppViewModel
import org.athar.app.R
import org.athar.app.auth.AuthState
import org.athar.app.quran.QuranScreen
import org.athar.app.adhkar.AdhkarScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.athar.app.media.LocalAtharAudio
import org.athar.app.media.AtharMiniPlayer
import org.athar.app.media.audioViewModel

internal fun JsonElement.text(key: String): String = runCatching { asJsonObject[key]?.takeUnless { it.isJsonNull }?.asString.orEmpty() }.getOrDefault("")
internal fun JsonElement.array(key: String): List<JsonElement> = runCatching { asJsonObject[key]?.asJsonArray?.toList().orEmpty() }.getOrDefault(emptyList())

private data class Destination(val key: String, val title: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val destinations = listOf(Destination("home", R.string.home, AtharIcons.Home), Destination("quran", R.string.quran, AtharIcons.MenuBook), Destination("knowledge", R.string.knowledge, AtharIcons.School), Destination("adhkar", R.string.adhkar, AtharIcons.Spa), Destination("account", R.string.account, AtharIcons.Person))

@Composable
private fun AtharNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
) {
    val reduced = reducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (selected && !reduced) 1.08f else 1f,
        animationSpec = if (reduced) snap() else spring(
            stiffness = AtharMotion.SmallSpringStiffness,
            dampingRatio = AtharMotion.SmallSpringDamping,
        ),
        label = "athar-nav-icon",
    )
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable fun MainShell(vm: AppViewModel, auth: AuthState.SignedIn) {
    val audio = audioViewModel(vm.graph)
    var selected by rememberSaveable { mutableStateOf("home") }
    val navigation = rememberNavController()
    val currentEntry by navigation.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route ?: "home"
    val topLevel = destinations.any { it.key == route }
    val reduced = reducedMotion()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LaunchedEffect(route) { if (destinations.any { it.key == route }) selected = route }
    val navigateTab: (String) -> Unit = { key ->
        selected = key
        navigation.navigate(key) { popUpTo(navigation.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    }
    val open: (String) -> Unit = { path ->
        if (path == "night-prayer" || path == "upcoming" || path == "player") navigation.navigate(path) { launchSingleTop = true }
        else if (path == "catalog-books") navigation.navigate("catalog/books") { launchSingleTop = true }
        else {
            val parts = path.split('/')
            if (parts.size == 3 && parts[0] == "quran" && (parts[1].toIntOrNull() ?: 0) in 1..114 && (parts[2].toIntOrNull() ?: 0) in 1..286) navigation.navigate(path) { launchSingleTop = true }
            else if(parts.size==2 && parts[0]=="upcoming" && (parts[1].toIntOrNull()?:0)>0) navigation.navigate(path){launchSingleTop=true}
            else if (parts.size == 2 && parts[0] in listOf("books","scholars","series","lessons") && (parts[1].toIntOrNull() ?: 0) > 0) navigation.navigate("content/${parts[0]}/${parts[1]}") { launchSingleTop = true }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val navigationLayout = AtharNavigationLayoutPolicy.resolve(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
        val wide = navigationLayout.rail
        CompositionLocalProvider(LocalAtharAudio provides audio) {
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                header = { Brand(Modifier.size(76.dp)) },
            ) {
                Spacer(Modifier.height(24.dp))
                destinations.forEach { destination ->
                    val active = selected == destination.key
                    NavigationRailItem(
                        modifier = Modifier.testTag("nav-${destination.key}"),
                        selected = active,
                        onClick = { navigateTab(destination.key) },
                        icon = { AtharNavIcon(destination.icon, active) },
                        label = { Text(stringResource(destination.title)) },
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            Scaffold(modifier = Modifier.weight(1f), topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        if (topLevel) {
                            Brand(Modifier.height(44.dp).width(64.dp))
                        } else {
                            Text(
                                stringResource(
                                    when {
                                        route == "night-prayer" -> R.string.night_prayer
                                        route.startsWith("quran/") -> R.string.quran
                                        route == "player" -> R.string.listen
                                        route.startsWith("upcoming") -> R.string.upcoming_lessons
                                        currentEntry?.arguments?.getString("kind") == "books" -> R.string.books
                                        currentEntry?.arguments?.getString("kind") == "scholars" -> R.string.scholars
                                        currentEntry?.arguments?.getString("kind") == "series" -> R.string.series
                                        else -> R.string.lessons
                                    },
                                ),
                            )
                        }
                    },
                    navigationIcon = {
                        if (!topLevel) {
                            IconButton(onClick = { navigation.popBackStack() }) {
                                Icon(AtharIcons.ArrowBack, stringResource(R.string.back))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    ),
                )
            }, bottomBar = {
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
                if (route != "player") AtharMiniPlayer(audio, { open("player") },
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                if (!wide && topLevel) {
                    AtharFloatingSurface(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    NavigationBar(
                        modifier = Modifier.heightIn(min = navigationLayout.minimumBarHeightDp.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        destinations.forEach { destination ->
                            val active = selected == destination.key
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav-${destination.key}"),
                                selected = active,
                                onClick = { navigateTab(destination.key) },
                                icon = { AtharNavIcon(destination.icon, active) },
                                label = { Text(stringResource(destination.title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                    }
                }
                }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    SharedTransitionLayout {
                    CompositionLocalProvider(LocalAtharSharedScope provides this) {
                    NavHost(navigation, startDestination = "home", enterTransition = { fadeIn(tween(if (reduced) 0 else AtharMotion.Normal, easing = AtharMotion.Enter)) }, exitTransition = { fadeOut(tween(if (reduced) 0 else AtharMotion.Fast, easing = AtharMotion.Exit)) }) {
                        composable("home") { CompositionLocalProvider(LocalAtharVisibilityScope provides this) { HomeScreen(vm.graph, auth, navigateTab, open) } }
                        composable("quran") { QuranScreen(vm.graph, settings?.quranFontSize ?: 28) }
                        composable(
                            "quran/{surah}/{ayah}",
                            arguments = listOf(
                                navArgument("surah") { type = NavType.IntType },
                                navArgument("ayah") { type = NavType.IntType },
                            ),
                        ) { entry ->
                            QuranScreen(
                                graph = vm.graph,
                                fontSize = settings?.quranFontSize ?: 28,
                                initialSurah = entry.arguments?.getInt("surah"),
                                initialAyah = entry.arguments?.getInt("ayah"),
                            )
                        }
                        composable("knowledge") { CompositionLocalProvider(LocalAtharVisibilityScope provides this) { KnowledgeScreen(vm.graph, open) } }
                        composable("adhkar") { AdhkarScreen(vm.graph) }
                        composable("account") { AccountScreen(vm, auth, open) }
                        composable("night-prayer") { org.athar.app.nightprayer.NightPrayerScreen(vm.graph) }
                        composable("player") { org.athar.app.media.AudioScreen(vm.graph, expandedOnly = true) }
                        composable("upcoming") { org.athar.app.learning.UpcomingScreen(vm.graph,open=open) }
                        composable("upcoming/{id}",arguments=listOf(navArgument("id"){type=NavType.IntType})) { entry -> org.athar.app.learning.UpcomingScreen(vm.graph,entry.arguments?.getInt("id"),open) }
                        composable("catalog/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry -> CompositionLocalProvider(LocalAtharVisibilityScope provides this) { KnowledgeScreen(vm.graph, open, entry.arguments?.getString("kind")?.takeIf { it in listOf("books","lessons","series","scholars") } ?: "lessons") } }
                        composable("content/{kind}/{id}", arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType })) { entry -> CompositionLocalProvider(LocalAtharVisibilityScope provides this) { CatalogDetail(vm.graph, "${entry.arguments?.getString("kind")}/${entry.arguments?.getInt("id")}", open) } }
                    }
                    }
                    }
                }
            }
        }
        }
    }
}

@Composable private fun HomeScreen(graph: AppGraph, auth: AuthState.SignedIn, navigate: (String) -> Unit, open: (String) -> Unit) {
    PremiumHomeScreen(graph, auth, navigate, open)
}

@Composable private fun ContentRow(item: JsonElement, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(item.text("title").ifBlank { item.text("name") }) }, supportingContent = { val subtitle = item.text("scholar_name").ifBlank { item.text("author") }; if (subtitle.isNotBlank()) Text(subtitle) }, trailingContent = { Icon(AtharIcons.ArrowForward, null) }, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable private fun KnowledgeScreen(graph: AppGraph, open: (String) -> Unit, initialType: String = "lessons") {
    val tabs = listOf("lessons" to R.string.lessons, "series" to R.string.series, "scholars" to R.string.scholars, "books" to R.string.books)
    var type by rememberSaveable { mutableStateOf(initialType) }
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize()) {
        if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp > 520 && LocalDensity.current.fontScale < 1.3f) {
            AtharScreenIdentity(stringResource(if (type == "books") R.string.books else R.string.knowledge),
                if (type == "books") AtharIcons.MenuBook else AtharIcons.School,
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp), stringResource(R.string.library_invitation))
        }
        TextButton(onClick={open("upcoming")},modifier=Modifier.padding(horizontal=16.dp)){Icon(AtharIcons.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.upcoming_lessons))}
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { tabs.forEach { tab -> FilterChip(type == tab.first, { type = tab.first; page = 1; query = ""; input = "" }, modifier = Modifier.testTag("catalog-category-${tab.first}"), label = { Text(stringResource(tab.second)) }) } }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it.take(100) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.search)) })
            IconButton(onClick = { query = input.trim(); page = 1 }) { Icon(AtharIcons.Search, stringResource(R.string.search)) }
        }
        DataScreen(graph, type, mapOf("q" to query, "page" to "$page")) { data, _ ->
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val rows = data.array("items")
                if (rows.isEmpty()) item(key = "empty", contentType = "state") { QuietEmptyState(stringResource(R.string.empty_state)) }
                items(rows, key = { it.text("id") }, contentType = { type }) { item -> AtharCatalogCard(item, type, { open("$type/${item.text("id")}") }) }
                item(key = "pagination", contentType = "controls") { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { page-- }, enabled = page > 1) { Text(stringResource(R.string.back)) }
                    Text("$page", Modifier.padding(16.dp))
                    TextButton(onClick = { page++ }, enabled = data.asJsonObject["pagination"].text("has_more") == "true") { Text(stringResource(R.string.more)) }
                } }
            }
        }
    }
}

@Composable private fun CatalogDetail(graph: AppGraph, path: String, open: (String) -> Unit) {
    val owner=remember{requireNotNull(graph.session.userId)}
    if(path.startsWith("lessons/")){org.athar.app.learning.WatchScreen(graph,path.substringAfter('/').toInt(),open);return}
    if(path.startsWith("series/")){org.athar.app.learning.SeriesScreen(graph,path.substringAfter('/').toInt(),open);return}
    if(path.startsWith("scholars/")){org.athar.app.learning.ScholarScreen(graph,path.substringAfter('/').toInt(),open);return}
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var feedback by remember(path) { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var favoriteInitial by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (!path.startsWith("scholars/")) try {
            val type = if (path.startsWith("series/")) "series" else path.substringBefore('/').removeSuffix("s")
            favoriteInitial = graph.content.get("me/favorites").data.asJsonArray.any { it.text("target_type") == type && it.text("target_id") == path.substringAfter('/') }
        } catch (failure: CancellationException) { throw failure } catch (_: Exception) { /* The idempotent save action remains available. */ }
    }
    val saved = stringResource(R.string.saved); val failed = stringResource(R.string.network_error)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
    // Render the native identity before the detail request so a selected cover can actually match.
    if (path.startsWith("books/")) Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
        val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 500 || LocalDensity.current.fontScale >= 1.3f
        AtharBookCover(path.substringAfter('/'), Modifier.width(if (compact) 56.dp else 88.dp).height(if (compact) 76.dp else 116.dp))
    }
    Box(Modifier.weight(1f)) {
    DataScreen(graph, path) { item, _ ->
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "heading", contentType = "heading") { Text(item.text("title").ifBlank { item.text("name") }, style = MaterialTheme.typography.headlineMedium) }
            if (item.text("author").isNotBlank()) item(key = "author", contentType = "metadata") { Text(item.text("author"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item(key = "description", contentType = "text") { Text(item.text("description").ifBlank { item.text("biography") }, style = MaterialTheme.typography.bodyLarge) }
            if (item.text("youtube_id").matches(Regex("[A-Za-z0-9_-]{11}"))) item(key = "youtube", contentType = "action") { Button(onClick = { uri.openUri("https://www.youtube.com/watch?v=${item.text("youtube_id")}") }) { Text(stringResource(R.string.open_youtube)) } }
            if (path.startsWith("books/") && item.text("can_download") == "true") item(key = "download", contentType = "action") { Button(onClick = { scope.launch {
                try { val response = graph.session.request("GET", "$path/download",expectedUser=owner); val link = response.text("url"); if (link.startsWith("https://")) uri.openUri(link) else feedback = failed }
                catch (failure: CancellationException) { throw failure } catch (_: Exception) { feedback = failed }
            } }) { Text(stringResource(R.string.download_book)) } }
            if (!path.startsWith("scholars/")) item(key = "favorite", contentType = "action") { MarkToggle(path, favoriteInitial, true, snackbar) { selected ->
                graph.session.request(if (selected) "PUT" else "DELETE", "me/favorites", mapOf("target_type" to (if (path.startsWith("series/")) "series" else path.substringBefore('/').removeSuffix("s")), "target_id" to path.substringAfter('/').toInt()),expectedUser=owner)
                graph.content.invalidate("me/favorites",owner)
            } }
            if (feedback != null) item(key = "feedback", contentType = "feedback") { MessagePanel(feedback!!) }
            items(item.array("series"), key = { "series-${it.text("id")}" }, contentType = { "series" }) { row -> ContentRow(row, { open("series/${row.text("id")}") }) }
            items(item.array("lessons"), key = { "lesson-${it.text("id")}" }, contentType = { "lesson" }) { row -> ContentRow(row, { open("lessons/${row.text("id")}") }) }
        }
    }
    }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable private fun AccountScreen(vm: AppViewModel, auth: AuthState.SignedIn, open: (String) -> Unit) {
    var tool by rememberSaveable { mutableStateOf("profile") }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("profile" to R.string.profile,"khatmah" to R.string.khatmah,"bookmarks" to R.string.bookmarks,"favorites" to R.string.favorites,"export" to R.string.export_data).forEach { (key,label) -> FilterChip(tool == key, { tool = key }, label = { Text(stringResource(label)) }) }
        }
        Box(Modifier.weight(1f)) { if (tool == "profile") AccountProfileScreen(vm, auth) else AccountToolsScreen(vm.graph, tool, open) }
    }
}

@Composable private fun AccountProfileScreen(vm: AppViewModel, auth: AuthState.SignedIn) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by remember(auth.user) { mutableStateOf(auth.user.displayName.orEmpty()) }
    var username by remember(auth.user) { mutableStateOf(auth.user.username.orEmpty()) }
    var current by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf(false) }; var deletePassword by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "profile", contentType = "heading") { AtharScreenIdentity(stringResource(R.string.profile), AtharIcons.Person) }
        item(key = "email", contentType = "text") { Text(auth.user.email) }
        item(key = "name", contentType = "field") { OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.display_name)) }, singleLine = true) }
        item(key = "username", contentType = "field") { OutlinedTextField(username, { username = it.take(24) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.username)) }, singleLine = true) }
        item(key = "save", contentType = "action") { Button(onClick = { vm.perform { vm.graph.session.updateProfile(name, username,auth.user.id) } }, enabled = !operation.busy) { Text(stringResource(R.string.save)) } }
        item(key = "settings", contentType = "heading") { HorizontalDivider(); Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge) }
        item(key = "language", contentType = "controls") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("ar" to R.string.arabic, "en" to R.string.english).forEach { (tag, label) -> OutlinedButton(onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }) { Text(stringResource(label)) } } } }
        item(key = "theme", contentType = "controls") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("light" to R.string.light, "dark" to R.string.dark, "system" to R.string.system_theme).forEach { (key, label) -> FilterChip(settings?.theme == key, { scope.launch { vm.graph.settings.theme(key) } }, label = { Text(stringResource(label)) }) } } }
        item(key = "security", contentType = "heading") { HorizontalDivider(); Text(stringResource(R.string.change_password), style = MaterialTheme.typography.titleLarge) }
        item(key = "password-fields", contentType = "fields") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PasswordField(current, { current = it }, stringResource(R.string.current_password))
            PasswordField(password, { password = it }, stringResource(R.string.new_password))
            PasswordField(confirmation, { confirmation = it }, stringResource(R.string.confirm_password))
            Button(onClick = { if (password != confirmation) vm.invalid("password_mismatch") else vm.perform { vm.graph.session.changePassword(current, password, confirmation,auth.user.id); current = ""; password = ""; confirmation = "" } }, enabled = !operation.busy) { Text(stringResource(R.string.save)) }
        } }
        if (operation.error != null || operation.message != null) item(key = "feedback", contentType = "feedback") { org.athar.app.auth.Feedback(operation) }
        item(key = "signout", contentType = "action") { OutlinedButton(onClick = {vm.logout(auth.user.id)}) { Text(stringResource(R.string.logout)) } }
        item(key = "delete", contentType = "action") { TextButton(onClick = { delete = true }) { Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error) } }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false; deletePassword = "" }, title = { Text(stringResource(R.string.delete_account)) }, text = { Column { Text(stringResource(R.string.delete_account_body)); PasswordField(deletePassword, { deletePassword = it }, stringResource(R.string.current_password)) } }, confirmButton = { TextButton(onClick = { vm.perform { val id = auth.user.id; vm.graph.session.delete(deletePassword,id); vm.graph.database.dao().deleteUser(id) }; delete = false; deletePassword = "" }) { Text(stringResource(R.string.delete_action)) } }, dismissButton = { TextButton(onClick = { delete = false; deletePassword = "" }) { Text(stringResource(R.string.cancel)) } })
}
