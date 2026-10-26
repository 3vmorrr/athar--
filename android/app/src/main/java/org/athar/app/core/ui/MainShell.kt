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
import androidx.compose.ui.platform.LocalContext
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
import org.athar.app.auth.AccountInput
import org.athar.app.core.data.cleanupDeletedAccount
import org.athar.app.notifications.NotificationDestination
import org.athar.app.notifications.NotificationLaunch
import org.athar.app.quran.QuranScreen
import org.athar.app.adhkar.AdhkarScreen
import org.athar.app.books.BookDetailScreen
import org.athar.app.books.BookReaderScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.graphicsLayer

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MainShell(
    vm: AppViewModel,
    auth: AuthState.SignedIn,
    notificationLaunch: NotificationLaunch? = null,
    onNotificationLaunchConsumed: (NotificationLaunch) -> Unit = {},
) {
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
        if (path == "night-prayer" || path == "upcoming") {
            navigation.navigate(path) { launchSingleTop = true }
        } else if (path == "catalog-books") {
            navigation.navigate("catalog/books") { launchSingleTop = true }
        } else {
            val parts = path.split('/')
            when {
                parts.size == 3 &&
                    parts[0] == "quran" &&
                    (parts[1].toIntOrNull() ?: 0) in 1..114 &&
                    (parts[2].toIntOrNull() ?: 0) > 0 -> {
                    navigation.navigate(path) { launchSingleTop = true }
                }
                parts.size == 2 &&
                    parts[0] == "upcoming" &&
                    (parts[1].toIntOrNull() ?: 0) > 0 -> {
                    navigation.navigate(path) { launchSingleTop = true }
                }
                parts.size == 2 &&
                    parts[0] == "book-reader" &&
                    (parts[1].toIntOrNull() ?: 0) > 0 -> {
                    navigation.navigate(path) { launchSingleTop = true }
                }
                parts.size == 2 &&
                    parts[0] in listOf("books","scholars","series","lessons") &&
                    (parts[1].toIntOrNull() ?: 0) > 0 -> {
                    navigation.navigate("content/${parts[0]}/${parts[1]}") {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
    LaunchedEffect(notificationLaunch, auth.user.id) {
        val launch = notificationLaunch ?: return@LaunchedEffect
        if (launch.ownerId != auth.user.id) {
            onNotificationLaunchConsumed(launch)
            return@LaunchedEffect
        }
        when (val target = launch.destination) {
            is NotificationDestination.Lesson -> open("lessons/" + target.id)
            is NotificationDestination.Series -> open("series/" + target.id)
            NotificationDestination.Quran -> navigateTab("quran")
            NotificationDestination.Adhkar -> navigateTab("adhkar")
            NotificationDestination.NightPrayer -> open("night-prayer")
            NotificationDestination.Inbox -> {
                // WP16 registers the private inbox destination. Never misroute it to another account/screen.
            }
        }
        onNotificationLaunchConsumed(launch)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(
                modifier = Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                header = { Brand(Modifier.size(76.dp)) },
            ) {
                Spacer(Modifier.height(24.dp))
                destinations.forEach { destination ->
                    val active = selected == destination.key
                    NavigationRailItem(
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
                                        route.startsWith("upcoming") -> R.string.upcoming_lessons
                                        route.startsWith("book-reader/") -> R.string.books
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
                if (!wide && topLevel) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                    ) {
                        destinations.forEach { destination ->
                            val active = selected == destination.key
                            NavigationBarItem(
                                selected = active,
                                onClick = { navigateTab(destination.key) },
                                icon = { AtharNavIcon(destination.icon, active) },
                                label = { Text(stringResource(destination.title), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    NavHost(navigation, startDestination = "home", enterTransition = { fadeIn(tween(if (reduced) 0 else AtharMotion.Normal, easing = AtharMotion.Enter)) }, exitTransition = { fadeOut(tween(if (reduced) 0 else AtharMotion.Fast, easing = AtharMotion.Exit)) }) {
                        composable("home") { HomeScreen(vm.graph, auth, navigateTab, open) }
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
                        composable("knowledge") { KnowledgeScreen(vm.graph, open) }
                        composable("adhkar") { AdhkarScreen(vm.graph) }
                        composable("account") { AccountScreen(vm, auth, open) }
                        composable("night-prayer") { org.athar.app.nightprayer.NightPrayerScreen(vm.graph) }
                        composable("upcoming") { org.athar.app.learning.UpcomingScreen(vm.graph,open=open) }
                        composable("upcoming/{id}",arguments=listOf(navArgument("id"){type=NavType.IntType})) { entry -> org.athar.app.learning.UpcomingScreen(vm.graph,entry.arguments?.getInt("id"),open) }
                        composable("catalog/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry -> KnowledgeScreen(vm.graph, open, entry.arguments?.getString("kind")?.takeIf { it in listOf("books","lessons","series","scholars") } ?: "lessons") }
                        composable("content/{kind}/{id}", arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType })) { entry -> CatalogDetail(vm.graph, "${entry.arguments?.getString("kind")}/${entry.arguments?.getInt("id")}", open) }
                        composable(
                            "book-reader/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.IntType }),
                        ) { entry ->
                            BookReaderScreen(
                                owner = requireNotNull(vm.graph.session.userId),
                                bookId = entry.arguments?.getInt("id") ?: 0,
                            )
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
        TextButton(onClick={open("upcoming")},modifier=Modifier.padding(horizontal=16.dp)){Icon(AtharIcons.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.upcoming_lessons))}
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { tabs.forEach { tab -> FilterChip(type == tab.first, { type = tab.first; page = 1; query = ""; input = "" }, label = { Text(stringResource(tab.second)) }) } }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it.take(100) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.search)) })
            IconButton(onClick = { query = input.trim(); page = 1 }) { Icon(AtharIcons.Search, stringResource(R.string.search)) }
        }
        DataScreen(graph, type, mapOf("q" to query, "page" to "$page")) { data, _ ->
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                val rows = data.array("items")
                if (rows.isEmpty()) item { Text(stringResource(R.string.empty_state), Modifier.padding(16.dp)) }
                items(rows, key = { it.text("id") }, contentType = { type }) { item -> ContentRow(item, { open("$type/${item.text("id")}") }) }
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
    if(path.startsWith("books/")){
        val bookId=path.substringAfter('/').toInt()
        BookDetailScreen(
            graph=graph,
            bookId=bookId,
            openReader={open("book-reader/$bookId")},
        )
        return
    }
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
    DataScreen(graph, path) { item, _ ->
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "heading", contentType = "heading") { Text(item.text("title").ifBlank { item.text("name") }, style = MaterialTheme.typography.headlineMedium) }
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
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable private fun AccountScreen(vm: AppViewModel, auth: AuthState.SignedIn, open: (String) -> Unit) {
    var tool by rememberSaveable { mutableStateOf("profile") }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "profile" to R.string.profile,
                "activity" to R.string.activity,
                "khatmah" to R.string.khatmah,
                "bookmarks" to R.string.bookmarks,
                "favorites" to R.string.favorites,
                "export" to R.string.export_data,
            ).forEach { (key,label) ->
                FilterChip(
                    tool == key,
                    { tool = key },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Box(Modifier.weight(1f)) { if (tool == "profile") AccountProfileScreen(vm, auth) else AccountToolsScreen(vm.graph, tool, open) }
    }
}

@Composable private fun AccountProfileScreen(
    vm: AppViewModel,
    auth: AuthState.SignedIn,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    var name by remember(auth.user) {
        mutableStateOf(auth.user.displayName.orEmpty())
    }
    var username by remember(auth.user) {
        mutableStateOf(auth.user.username.orEmpty())
    }
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }

    val normalizedName = name.trim()
    val normalizedUsername = AccountInput.normalizeUsernameInput(username).trim()
    val nameValid = AccountInput.validDisplayName(name)
    val usernameValid = AccountInput.validUsername(username)
    val profileValid = AccountInput.validProfile(name, username)
    val profileChanged =
        normalizedName != auth.user.displayName.orEmpty() ||
            normalizedUsername != auth.user.username.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "profile", contentType = "heading") {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.Generic,
                    eyebrow = stringResource(R.string.account),
                    title = auth.user.displayName.orEmpty().ifBlank {
                        auth.user.username.orEmpty()
                    },
                    supportingText = auth.user.email,
                    statusText = if (auth.offline) {
                        stringResource(R.string.offline_notice)
                    } else {
                        null
                    },
                    statusTone = if (auth.offline) {
                        AtharHeroStatusTone.Offline
                    } else {
                        AtharHeroStatusTone.Neutral
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "name", contentType = "field") {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(80)
                    if (operation.error != null || operation.message != null) {
                        vm.clearFeedback()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.display_name)) },
                supportingText = {
                    Text(stringResource(R.string.display_name_hint))
                },
                isError = name.isNotBlank() && !nameValid,
                singleLine = true,
            )
        }

        item(key = "username", contentType = "field") {
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = AccountInput.normalizeUsernameInput(it)
                    if (operation.error != null || operation.message != null) {
                        vm.clearFeedback()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.username)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (operation.error == "account_conflict") {
                                R.string.username_unavailable
                            } else {
                                R.string.username_hint
                            },
                        ),
                    )
                },
                isError = !usernameValid ||
                    operation.error == "account_conflict",
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                ),
            )
        }

        item(key = "save", contentType = "action") {
            Button(
                onClick = {
                    vm.clearFeedback()
                    vm.perform(successMessage = "saved") {
                        vm.graph.session.updateProfile(
                            normalizedName,
                            normalizedUsername,
                            auth.user.id,
                        )
                    }
                },
                enabled = !operation.busy && profileValid && profileChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }

        item(key = "settings", contentType = "heading") {
            HorizontalDivider()
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        item(key = "language", contentType = "controls") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "ar" to R.string.arabic,
                    "en" to R.string.english,
                ).forEach { (tag, label) ->
                    OutlinedButton(
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(tag),
                            )
                        },
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
        }

        item(key = "theme", contentType = "controls") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "light" to R.string.light,
                    "dark" to R.string.dark,
                    "system" to R.string.system_theme,
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = settings?.theme == key,
                        onClick = {
                            scope.launch { vm.graph.settings.theme(key) }
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }

        item(key = "security", contentType = "heading") {
            HorizontalDivider()
            Text(
                stringResource(R.string.change_password),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        item(key = "password-fields", contentType = "fields") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(
                    current,
                    { current = it.take(200) },
                    stringResource(R.string.current_password),
                )
                PasswordField(
                    password,
                    { password = it.take(200) },
                    stringResource(R.string.new_password),
                )
                PasswordField(
                    confirmation,
                    { confirmation = it.take(200) },
                    stringResource(R.string.confirm_password),
                )
                Button(
                    onClick = {
                        if (password != confirmation) {
                            vm.invalid("password_mismatch")
                        } else {
                            vm.perform(successMessage = "saved") {
                                vm.graph.session.changePassword(
                                    current,
                                    password,
                                    confirmation,
                                    auth.user.id,
                                )
                                current = ""
                                password = ""
                                confirmation = ""
                            }
                        }
                    },
                    enabled = !operation.busy &&
                        current.isNotBlank() &&
                        AccountInput.validPassword(password) &&
                        confirmation.isNotBlank() &&
                        password == confirmation,
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }

        item(key = "revoke-other-sessions", contentType = "action") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.padding(AtharSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    Text(
                        stringResource(R.string.revoke_other_sessions),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.revoke_other_sessions_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            vm.perform(successMessage = "sessions_revoked") {
                                vm.graph.session.revokeOtherSessions(auth.user.id)
                            }
                        },
                        enabled = !operation.busy,
                        modifier = Modifier.heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.revoke_other_sessions))
                    }
                }
            }
        }

        if (operation.error != null || operation.message != null) {
            item(key = "feedback", contentType = "feedback") {
                org.athar.app.auth.Feedback(operation)
            }
        }

        item(key = "signout", contentType = "action") {
            OutlinedButton(
                onClick = { vm.logout(auth.user.id) },
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.logout))
            }
        }

        item(key = "delete", contentType = "action") {
            TextButton(
                onClick = { delete = true },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(R.string.delete_account),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (delete) {
        AlertDialog(
            onDismissRequest = {
                delete = false
                deletePassword = ""
            },
            title = { Text(stringResource(R.string.delete_account)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.delete_account_body))
                    PasswordField(
                        deletePassword,
                        { deletePassword = it.take(200) },
                        stringResource(R.string.current_password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.perform {
                            val id = auth.user.id
                            vm.graph.session.delete(deletePassword, id)
                            cleanupDeletedAccount(
                                context = context,
                                graph = vm.graph,
                                owner = id,
                            )
                        }
                        delete = false
                        deletePassword = ""
                    },
                    enabled = deletePassword.isNotBlank() && !operation.busy,
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        delete = false
                        deletePassword = ""
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
