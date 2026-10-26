# Source declarations and route evidence

Generated static evidence, not a runtime verification report.

## android/app/src/androidTest/java/org/athar/app/AdhkarJourneyTest.kt

```text
17: class AdhkarJourneyTest {
22:         val app = ApplicationProvider.getApplicationContext<AtharApplication>()
23:         val graph = app.graph
25:         val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
26:         val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
27:         fun label(id: Int) = resources.getString(id)
55:                 val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
```

## android/app/src/androidTest/java/org/athar/app/AtharTestRunner.kt

```text
9: class AtharTestRunner : AndroidJUnitRunner() {
12:         val language = InstrumentationRegistry.getArguments().getString("nativeLanguage")
20: class AtharTestApplication : AtharApplication() {
22:         val fixture = InstrumentationRegistry.getArguments().getString("nativeApiSmoke") == "true"
23:         val fixtureHost = InstrumentationRegistry.getArguments().getString("nativeFixtureHost") ?: "10.0.2.2"
```

## android/app/src/androidTest/java/org/athar/app/AudioPlaybackTest.kt

```text
16: class AudioPlaybackTest {
19:         val app = ApplicationProvider.getApplicationContext<AtharApplication>()
20:         val graph = app.graph
26:         fun await(timeout: Long = 60_000, predicate: () -> Boolean) = runBlocking {
38:                 val reader = runBlocking { graph.content.get("quran/reciters", mapOf("surah" to "1", "per_page" to "50")).data.array("items").first { it.text("id") == "1" } }
56:                 val verseSelection = vm.selection.value.copy(verseMode = true, ayah = 2)
61:                 val timing = vm.state.value.timings.first { it.ayah == 2 }
```

## android/app/src/androidTest/java/org/athar/app/AudioSelectionJourneyTest.kt

```text
16: class AudioSelectionJourneyTest {
20:         val app = ApplicationProvider.getApplicationContext<AtharApplication>()
21:         val graph = app.graph
23:         val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
24:         val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
25:         fun label(id: Int) = resources.getString(id)
26:         val surahName = runBlocking { graph.quran.data().index.surahs[1].name }
57:                 val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
```

## android/app/src/androidTest/java/org/athar/app/AuthFeedbackJourneyTest.kt

```text
16: class AuthFeedbackJourneyTest {
20:         val app = ApplicationProvider.getApplicationContext<AtharApplication>()
21:         val graph = app.graph
23:         val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
24:         val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
25:         fun label(id: Int) = resources.getString(id)
30:                 val remoteMessage = "Untrusted remote details for $code"
```

## android/app/src/androidTest/java/org/athar/app/CounterStorageTest.kt

```text
19: class CounterStorageTest {
28:         val events=mutableSetOf<String>()
31:                 val user=if(body["email"]=="other@example.test") 2 else 1
49:         val context=ApplicationProvider.getApplicationContext<Context>()
50:         val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
51:         val server=Server();val session=SessionRepository(server,Store());val cipher=PrivateCipher()
55:             val reset=repository.tap(1,3,reset=true)
60:             val pending=repository.observe(1,1,3).first()
62:             val stored=requireNotNull(db.dao().cache(1,"adhkar:1"))
66:             val recovered=repository.observe(1,1,3).first()
71:             val conflicted=repository.observe(1,1,3).first()
80:         val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
81:         val server=Server();val session=SessionRepository(server,Store());session.login("reader@example.test","Fixture123!")
82:         val repository=CounterRepository(db.dao(),PrivateCipher(),session,automaticSync=false)
```

## android/app/src/androidTest/java/org/athar/app/FreshInstallTest.kt

```text
12: class FreshInstallTest {
18:         val activity=compose.activity
21:         val settings=runBlocking{(activity.application as AtharApplication).graph.settings.settings.first()}
```

## android/app/src/androidTest/java/org/athar/app/LessonWatchTest.kt

```text
17: class LessonWatchTest {
21:         val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
23:         val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
24:         val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}).resources
25:         fun label(id:Int)=resources.getString(id)
27:         val before=runBlocking{graph.session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "2")).toString()}
35:             val total=runBlocking{graph.session.request("GET","lessons/2/discussion").asJsonObject["pagination"].asJsonObject["total"].asInt}
46:             val inline = compose.onAllNodesWithContentDescription(label(R.string.watch_expand_comments)).fetchSemanticsNodes().isNotEmpty()
53:                 val body="Native interface comment fixture "+java.util.UUID.randomUUID()
```

## android/app/src/androidTest/java/org/athar/app/NativeStorageTest.kt

```text
23: class NativeStorageTest {
26:         val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
29:             val store=object:TokenStore{var value:StoredSession?=null;override suspend fun load()=value;override suspend fun save(session:StoredSession){value=session};override suspend fun clear(){value=null}}
30:             val transport=object:ApiTransport{
37:             val session=SessionRepository(transport,store);session.login("reader@example.test","Fixture123!")
38:             val cipher=PrivateCipher();val first=org.athar.app.learning.LessonRepository(db.dao(),cipher,session)
40:             val restored=org.athar.app.learning.LessonRepository(db.dao(),cipher,session)
51:         fun approved(url: String) = org.athar.app.media.PlaybackService.approvedMedia(android.net.Uri.parse(url))
60:         val data = QuranRepository(context).data()
68:         val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
70:             val bodies = mutableListOf<Map<String, Any?>>()
71:             val store = object : TokenStore {
77:             val transport = object : ApiTransport {
82:                     val operations = Gson().toJsonTree(body["operations"]).asJsonArray
83:                     val results = JsonArray()
88:             val session = SessionRepository(transport, store)
90:             val repository = NightPrayerRepository(db, session, PrivateCipher())
91:             val id = UUID.randomUUID().toString()
92:             val payload = JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"Africa/Cairo","boundary_source":"manual","entry_type":"manual","verse_count":10,"note":"private test note"}""").asJsonObject
110:         val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
112:             val events = mutableSetOf<String>()
114:             val store = object : TokenStore {
120:             val transport = object : ApiTransport {
130:             val session = SessionRepository(transport, store); session.login("reader@example.test", "NativeTest123!")
131:             val cipher = PrivateCipher()
132:             val repository = org.athar.app.core.data.CounterRepository(db.dao(), cipher, session, automaticSync = false)
137:             val restored = org.athar.app.core.data.CounterRepository(db.dao(), cipher, session, automaticSync = false)
```

## android/app/src/androidTest/java/org/athar/app/NativeUiJourneyTest.kt

```text
18: class NativeUiJourneyTest {
22:         val app = ApplicationProvider.getApplicationContext<AtharApplication>()
23:         val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
25:         val configuration = android.content.res.Configuration(app.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(language)) }
26:         val resources = app.createConfigurationContext(configuration).resources
27:         fun label(id: Int) = resources.getString(id)
28:         val graph = app.graph
77:         val variant = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "phone-light"
78:         val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
```

## android/app/src/androidTest/java/org/athar/app/OfficialPlayerTest.kt

```text
20: class OfficialPlayerTest {
24:         val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
66:             val progress=runBlocking{graph.session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "4"))}
```

## android/app/src/androidTest/java/org/athar/app/OtpInputJourneyTest.kt

```text
14: class OtpInputJourneyTest {
18:         val app = ApplicationProvider.getApplicationContext<AtharApplication>()
19:         val graph = app.graph
21:         val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
22:         val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
23:         fun label(id: Int) = resources.getString(id)
29:             val field = compose.onNode(hasSetTextAction() and hasText(label(R.string.verification_code)))
```

## android/app/src/androidTest/java/org/athar/app/PhysicalBackendSmokeTest.kt

```text
16: class PhysicalBackendSmokeTest {
19:         val app = ApplicationProvider.getApplicationContext<AtharApplication>()
20:         val graph = app.graph
22:         val health = runBlocking { RetrofitTransport(graph.apiBaseUrl).request("GET", "health") }
24:         val settings = runBlocking { graph.settings.settings.first() }
```

## android/app/src/androidTest/java/org/athar/app/ReadingJourneyTest.kt

```text
17: class ReadingJourneyTest {
21:         val app = ApplicationProvider.getApplicationContext<AtharApplication>()
22:         val graph = app.graph
24:         val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
25:         val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
26:         fun label(id: Int) = resources.getString(id)
32:         val before = runBlocking { graph.session.request("GET", "home").asJsonObject["activity"].asJsonObject["quran_sessions"].asString }
45:                 val top = compose.onNodeWithTag("quran-reader-list").fetchSemanticsNode().boundsInRoot.top
53:                 val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
```

## android/app/src/androidTest/java/org/athar/app/ReadingStorageTest.kt

```text
16: class ReadingStorageTest {
18:         val context = ApplicationProvider.getApplicationContext<android.content.Context>()
19:         val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
20:         val index = QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use { it.readText() })
21:         val store = object : TokenStore {
31:         val transport = object : ApiTransport {
34:                     val id = if (body["email"] == "other@example.test") 2 else 1
48:         val session = SessionRepository(transport, store)
50:         val cipher = PrivateCipher()
99:             val before = writes
```

## android/app/src/androidTest/java/org/athar/app/UpcomingJourneyTest.kt

```text
22: class UpcomingJourneyTest {
26:         val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
28:         val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
29:         val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}).resources
30:         fun label(id:Int)=resources.getString(id)
31:         fun capture(stage:String) {
33:             val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
36:         val store=object:TokenStore{var session:StoredSession?=null;override suspend fun load()=session;override suspend fun save(session:StoredSession){this.session=session};override suspend fun clear(){session=null}}
37:         val admin=SessionRepository(RetrofitTransport(graph.apiBaseUrl),store)
38:         fun change(values:Map<String,Any?>)=runBlocking {
39:             val current=admin.request("GET","admin/lessons/5/event").asJsonObject
48:             val current=runBlocking{admin.request("GET","admin/lessons/5/event").asJsonObject["status"].asString}
```

## android/app/src/main/java/org/athar/app/adhkar/AdhkarScreen.kt

```text
26: fun AdhkarScreen(graph: AppGraph) {
31:     val scope = rememberCoroutineScope()
32:     val snackbar = remember { SnackbarHostState() }
33:     val entriesScroll = rememberLazyListState()
34:     val failed = stringResource(R.string.network_error)
37:             val categories = data.asJsonArray.toList()
38:             val selected = category ?: categories.firstOrNull()?.text("category")
48:                         val rows = catalog.array("items")
49:                         val more = catalog.asJsonObject["pagination"].text("has_more") == "true"
50:                         val index = position.coerceAtMost((rows.size - 1).coerceAtLeast(0))
52:                         fun advance() {
66:                                 val id = dhikr.text("id").toInt()
67:                                 val target = dhikr.text("repeat_count").toIntOrNull()?.takeIf { it in 1..65535 }
76:                                                 val owner = requireNotNull(graph.session.userId)
82:                                                 val counter by remember(id, target) { graph.counters.observe(requireNotNull(graph.session.userId), id, target) }.collectAsStateWithLifecycle(initialValue = CounterState(id,
87:                                                     val originalPage = page
91:                                                             val next = graph.counters.tap(id, target)
```

## android/app/src/main/java/org/athar/app/AppViewModel.kt

```text
11: data class OperationState(val busy: Boolean = false, val error: String? = null, val message: String? = null)
12: class AppViewModel(val graph: AppGraph) : ViewModel() {
13:     val auth = graph.session.state
14:     val settings: StateFlow<AppSettings?> = graph.settings.settings.map<AppSettings, AppSettings?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
16:     val operation = mutableOperation.asStateFlow()
18:     fun completeOnboarding() { viewModelScope.launch { graph.settings.completeOnboarding() } }
19:     fun clearFeedback() { mutableOperation.value = OperationState() }
20:     fun invalid(code: String) { mutableOperation.value = OperationState(error = code) }
21:     fun login(email: String, password: String) = perform { graph.session.login(email, password) }
22:     fun register(name: String, username: String, email: String, password: String, confirmation: String) {
27:     fun verify(code: String) = perform { graph.session.verify(code) }
28:     fun resend() = perform { mutableOperation.value = OperationState(message = if (graph.session.resend() == "accepted") "mail_accepted" else "mail_unavailable") }
29:     fun forgot(email: String) = perform { graph.session.forgot(email); mutableOperation.value = OperationState(message = "reset_sent") }
30:     fun reset(email: String, code: String, password: String, confirmation: String) {
34:     fun logout() = perform { graph.session.logout() }
35:     fun perform(block: suspend () -> Unit) {
```

## android/app/src/main/java/org/athar/app/AtharApplication.kt

```text
17:             val manager=getSystemService(android.app.LocaleManager::class.java)
22: class AppGraph(application: Application, val apiBaseUrl: String = BuildConfig.API_BASE_URL) {
23:     val partition = java.security.MessageDigest.getInstance("SHA-256").digest(apiBaseUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(12)
24:     val cipher = PrivateCipher()
25:     val database = Room.databaseBuilder(application, AppDatabase::class.java, "athar-$partition.db").build()
26:     val settings = SettingsStore(application)
27:     val session = SessionRepository(RetrofitTransport(apiBaseUrl), KeystoreTokenStore(application, cipher, namespace = partition), language = { AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "ar" }
28:     val content = ContentRepository(session, database.dao(), cipher, language = { AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "ar" } })
29:     val quran = QuranRepository(application)
30:     val reading = org.athar.app.quran.ReadingRepository(database.dao(), cipher, session, index = { quran.data().index })
31:     val nightPrayer = org.athar.app.nightprayer.NightPrayerRepository(database, session, cipher)
32:     val counters = CounterRepository(database.dao(), cipher, session)
33:     val lessons = org.athar.app.learning.LessonRepository(database.dao(), cipher, session)
```

## android/app/src/main/java/org/athar/app/auth/AuthScreens.kt

```text
36:     val state by vm.operation.collectAsStateWithLifecycle()
37:     val focus = LocalFocusManager.current
38:     fun changeMode(next: String) { mode = next; password = ""; confirmation = ""; code = ""; vm.clearFeedback() }
70:     val state by vm.operation.collectAsStateWithLifecycle()
```

## android/app/src/main/java/org/athar/app/auth/OnboardingIllustration.kt

```text
16:     val colors = MaterialTheme.colorScheme
19:             val green = colors.primary; val soft = colors.primaryContainer; val paper = colors.surfaceContainerLow; val gold = colors.secondary
24:                     val cover = Path().apply { moveTo(55f, 79f); quadraticTo(108f, 55f, 160f, 83f); quadraticTo(212f, 55f, 265f, 79f); lineTo(265f, 188f); quadraticTo(212f, 169f, 160f, 199f); quadraticTo(108f, 169f, 55f,
26:                     val pages = Path().apply { moveTo(65f, 69f); quadraticTo(112f, 51f, 160f, 80f); quadraticTo(208f, 51f, 255f, 69f); lineTo(255f, 174f); quadraticTo(208f, 158f, 160f, 187f); quadraticTo(112f, 158f, 65f,
```

## android/app/src/main/java/org/athar/app/auth/OnboardingScreen.kt

```text
26:     val pager = rememberPagerState(pageCount = { 4 })
27:     val scope = rememberCoroutineScope()
28:     val titles = listOf(R.string.onboard_quran_title, R.string.onboard_knowledge_title, R.string.onboard_worship_title, R.string.onboard_private_title)
29:     val bodies = listOf(R.string.onboard_quran_body, R.string.onboard_knowledge_body, R.string.onboard_worship_body, R.string.onboard_private_body)
30:     val reduced = reducedMotion()
44:             val width by animateDpAsState(if (index == pager.currentPage) 24.dp else 7.dp, tween(if (reduced) 0 else AtharMotion.Fast), label = "onboarding-step")
```

## android/app/src/main/java/org/athar/app/auth/OtpInput.kt

```text
5:     fun normalize(value: String): String = buildString {
7:             val digit = when (character) {
```

## android/app/src/main/java/org/athar/app/auth/SessionRepository.kt

```text
20:     data class Pending(val user: UserProfile, val deliveryStatus: String? = null) : AuthState
21:     data class SignedIn(val user: UserProfile, val offline: Boolean = false) : AuthState
25: class SessionRepository(private val transport: ApiTransport, private val store: TokenStore, private val language: () -> String = { "ar" }, private val now: () -> Long = System::currentTimeMillis) {
32:     val state = mutableState.asStateFlow()
33:     val userId: Long? get() = stored?.user?.id
36:         val epoch = generation.get()
37:         val saved = store.load()
42:             val profile = gson.fromJson(request("GET", "me"), UserProfile::class.java)
43:             val current = stored ?: return
58:         val epoch = generation.get()
71:             val updated = stored ?: throw ApiException(401, "unauthenticated", "")
79:         val epoch = generation.get()
80:         val current = stored ?: throw ApiException(401, "unauthenticated", "")
87:         val epoch = generation.get()
88:         val user = gson.fromJson(request("PATCH", "me", mapOf("display_name" to name, "username" to username)), UserProfile::class.java)
94:         val token = stored?.credentials?.accessToken
102:         val reply = gson.fromJson(data, AuthReply::class.java)
103:         val user = reply.user ?: stored?.user ?: throw IOException("Invalid authentication response")
105:         val session = StoredSession(user, reply.session, now() + reply.session.expiresIn * 1000)
```

## android/app/src/main/java/org/athar/app/core/data/AppDatabase.kt

```text
7: data class CachedDocument(val userId: Long, val cacheKey: String, val payloadCipher: String, val updatedAt: Long)
9: data class LocalNightSession(val userId: Long, val id: String, val nightDate: String, val verseCount: Int, val measuredSeconds: Int?, val payloadCipher: String, val localRevision: Int, val serverRevision: Int, val delete
11: data class OutboxMutation(@PrimaryKey(autoGenerate = true) val sequence: Long = 0, val userId: Long, val mutationId: String, val sessionId: String, val operation: String, val expectedRevision: Int, val payloadCipher: Str
13: data class PrivateExemptions(val userId: Long, val encryptedPayload: String)
16: interface AtharDao {
```

## android/app/src/main/java/org/athar/app/core/data/ContentRepository.kt

```text
16: data class CachedResult(val data: JsonElement, val offline: Boolean)
17: class ContentRepository(private val session: SessionRepository, private val dao: AtharDao, private val cipher: PrivateCipher, private val language: () -> String = { "ar" }) {
19:     val revision = changes.asStateFlow()
22:         val user = session.userId ?: return
27:         val user = session.userId ?: throw ApiException(401, "unauthenticated", "")
28:         val key = cacheKey(path, query)
30:             val data = session.request("GET", path, query = query,expectedUser=user)
36:             val cached = if (allowCache) dao.cache(user, key) else null
```

## android/app/src/main/java/org/athar/app/core/data/CounterRepository.kt

```text
20: class CounterRepository(private val dao: AtharDao, private val cipher: PrivateCipher, private val session: SessionRepository, private val automaticSync: Boolean = true) : AutoCloseable {
37:     fun observe(user: Long, item: Int, target: Int): Flow<CounterState> = dao.observeCache(user, key(item)).map { it?.let(::decode) ?: CounterState(item, target) }.flowOn(Dispatchers.Default)
40:         val user = requireNotNull(session.userId)
50:         val updated = local.withLock {
51:             val previous = state(user, item, target)
54:             val count = if (reset || previous.target != target) 0 else (previous.count + 1).coerceAtMost(target)
56:             val event = if (count == target && previous.count < target) UUID.randomUUID().toString() else null
57:             val next = previous.copy(target = target, count = count, localVersion = previous.localVersion + 1, pending = true, events = previous.events + listOfNotNull(event), lastCompletion = event ?: previous.lastCompl
67:         val user = requireNotNull(session.userId)
69:             val snapshot = local.withLock { decode(dao.cache(user, document.cacheKey) ?: document) }
78:                 val response = session.request("PUT", "me/adhkar-progress", mapOf("item_id" to snapshot.item, "current_count" to snapshot.count, "expected_revision" to snapshot.revision, "event_id" to snapshot.lastComple
83:                 val server = fetchServer(user,snapshot.item)
86:                     val latest = state(user, snapshot.item, snapshot.target)
87:                     val acknowledged = server!=null && server.target==snapshot.target && server.count==snapshot.count && server.revision>snapshot.revision
94:         val row = session.request("GET", "me/adhkar-progress",query=mapOf("item_id" to "$item"),expectedUser=user).asJsonArray.firstOrNull { it.asJsonObject["item_key"].asString == "curated:$item" }?.asJsonObject ?: retu
95:         val count=row["current_count"].asInt;val target=row["repeat_count"].asInt;val revision=row["revision"].asInt
101:         val user=requireNotNull(session.userId)
103:         val server=fetchServer(user,item)
106:             val previous=state(user,item,target)
114:         val user = requireNotNull(session.userId)
115:         val server = fetchServer(user,item)
```

## android/app/src/main/java/org/athar/app/core/data/SettingsStore.kt

```text
11: data class AppSettings(val onboardingComplete: Boolean = false, val theme: String = "light", val quranFontSize: Int = 28)
12: class SettingsStore(private val context: Context) {
16:     val settings = context.preferences.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { AppSettings(it[onboarding] ?: false, it[theme] ?: "light", (it[size] ?: 28).coerceIn(18, 48)) }
```

## android/app/src/main/java/org/athar/app/core/network/ApiTransport.kt

```text
15: data class Envelope(val data: JsonElement?, val error: RemoteError?)
16: data class RemoteError(val code: String?, val message: String?, val fields: JsonObject?)
17: class ApiException(val status: Int, val code: String, override val message: String, val fields: JsonObject? = null) : IOException(message)
19: interface ApiTransport {
31: class RetrofitTransport(baseUrl: String, private val gson: Gson = Gson()) : ApiTransport {
38:         val authorization = bearer?.let { "Bearer $it" }
39:         val response = when (method) {
47:         val envelope = if (response.isSuccessful) response.body() else {
48:             val raw = response.errorBody()?.string().orEmpty()
```

## android/app/src/main/java/org/athar/app/core/security/PrivateCipher.kt

```text
12: class PrivateCipher {
15:         val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
21:     fun seal(value: String): String {
22:         val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
23:         val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
26:     fun open(value: String): String {
27:         val bytes = Base64.decode(value, Base64.NO_WRAP)
29:         val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
```

## android/app/src/main/java/org/athar/app/core/security/TokenStore.kt

```text
11: data class UserProfile(val id: Long, val email: String, @SerializedName("display_name") val displayName: String?, val username: String?, val role: String, @SerializedName("email_verified") val emailVerified: Boolean)
12: data class Credentials(val id: String, @SerializedName("access_token") val accessToken: String, @SerializedName("refresh_token") val refreshToken: String, @SerializedName("expires_in") val expiresIn: Long, val scope: Str
13: data class StoredSession(val user: UserProfile, val credentials: Credentials, val expiresAt: Long)
14: interface TokenStore { suspend fun load(): StoredSession?; suspend fun save(session: StoredSession); suspend fun clear() }
16: class KeystoreTokenStore(context: Context, private val cipher: PrivateCipher, private val gson: Gson = Gson(), namespace: String = "default") : TokenStore {
26:         val output = file.startWrite()
```

## android/app/src/main/java/org/athar/app/core/ui/AccountToolsScreen.kt

```text
20:     val scope = rememberCoroutineScope()
24:     val context = LocalContext.current
25:     val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
29:                 val user = requireNotNull(graph.session.userId)
30:                 val data = graph.session.request("GET", "me/export").asJsonObject
33:                     val local = com.google.gson.JsonArray()
71:             val path = if (tool == "bookmarks") "quran/bookmarks" else "me/favorites"
73:                 val rows = if (data.isJsonArray) data.asJsonArray.toList() else emptyList()
```

## android/app/src/main/java/org/athar/app/core/ui/AtharIcons.kt

```text
12:     val Share: ImageVector by lazy {
18:     val PlayArrow: ImageVector by lazy {
24:     val CheckCircle: ImageVector by lazy {
30:     val Fullscreen: ImageVector by lazy {
36:     val Close: ImageVector by lazy {
42:     val ChatBubble: ImageVector by lazy {
48:     val ExpandMore: ImageVector by lazy {
54:     val Home: ImageVector by lazy {
60:     val MenuBook: ImageVector by lazy {
66:     val School: ImageVector by lazy {
72:     val Spa: ImageVector by lazy {
78:     val Person: ImageVector by lazy {
84:     val ArrowBack: ImageVector by lazy {
90:     val ArrowForward: ImageVector by lazy {
96:     val Search: ImageVector by lazy {
102:     val Visibility: ImageVector by lazy {
108:     val VisibilityOff: ImageVector by lazy {
114:     val CalendarMonth: ImageVector by lazy {
120:     val NightsStay: ImageVector by lazy {
126:     val Lock: ImageVector by lazy {
132:     val BookmarkAdd: ImageVector by lazy {
138:     val Bookmark: ImageVector by lazy {
144:     val BookmarkFilled: ImageVector by lazy {
150:     val Favorite: ImageVector by lazy {
156:     val FavoriteFilled: ImageVector by lazy {
```

## android/app/src/main/java/org/athar/app/core/ui/Components.kt

```text
31:     val bitmap = ImageBitmap.imageResource(R.drawable.athar_logo)
32:     val description = stringResource(R.string.brand_description)
33:     val backdrop = if (MaterialTheme.colorScheme.surface.luminance() < 0.2f) Color(0xFFF4F3ED) else Color.Transparent
36:         val scale = minOf((size.width - 8.dp.toPx()).coerceAtLeast(1f) / 628f, (size.height - 8.dp.toPx()).coerceAtLeast(1f) / 580f)
37:         val width = (628 * scale).toInt(); val height = (580 * scale).toInt()
```

## android/app/src/main/java/org/athar/app/core/ui/DataScreen.kt

```text
20: data class DataState(val loading: Boolean = true, val result: CachedResult? = null, val error: String? = null)
21: class DataViewModel(private val graph: AppGraph, private val path: String, private val query: Map<String, String>) : ViewModel() {
23:     val state = mutable.asStateFlow()
25:     fun load() {
36:     val identity = "${graph.session.userId}:${androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()}:$path" + query.toSortedMap().toString()
37:     val vm: DataViewModel = viewModel(key = identity, factory = object : ViewModelProvider.Factory {
40:     val state by vm.state.collectAsStateWithLifecycle()
41:     val revision by graph.content.revision.collectAsStateWithLifecycle()
```

## android/app/src/main/java/org/athar/app/core/ui/DateField.kt

```text
17:         val selected = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
18:         val state = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1900..LocalDate.now().year + 5, selectableDates = object : SelectableD
```

## android/app/src/main/java/org/athar/app/core/ui/HomeScreen.kt

```text
46:                     val activity = data.asJsonObject["activity"]
82:     val interaction = remember { MutableInteractionSource() }
83:     val pressed by interaction.collectIsPressedAsState()
84:     val reduced = reducedMotion()
85:     val scale by animateFloatAsState(if (pressed) 0.985f else 1f, if (reduced) snap() else spring(stiffness = AtharMotion.SmallSpringStiffness, dampingRatio = AtharMotion.SmallSpringDamping), label = "content-card-press"
```

## android/app/src/main/java/org/athar/app/core/ui/LoadingSkeleton.kt

```text
17:     val reduced = reducedMotion()
18:     val opacity: State<Float>? = if (reduced) null else rememberInfiniteTransition(label = "loading").animateFloat(0.55f, 1f, infiniteRepeatable(tween(AtharMotion.LoadingPulse), RepeatMode.Reverse), label = "loading-opac
19:     val loading = stringResource(R.string.loading)
20:     val color = MaterialTheme.colorScheme.outlineVariant
21:     val shape = MaterialTheme.shapes.small
```

## android/app/src/main/java/org/athar/app/core/ui/MainShell.kt

```text
49:     val navigation = rememberNavController()
50:     val currentEntry by navigation.currentBackStackEntryAsState()
51:     val route = currentEntry?.destination?.route ?: "home"
52:     val topLevel = destinations.any { it.key == route }
53:     val reduced = reducedMotion()
54:     val settings by vm.settings.collectAsStateWithLifecycle()
56:     val navigateTab: (String) -> Unit = { key ->
60:     val open: (String) -> Unit = { path ->
64:             val parts = path.split('/')
70:         val wide = maxWidth >= 840.dp
112:     val tabs = listOf("lessons" to R.string.lessons, "series" to R.string.series, "scholars" to R.string.scholars, "books" to R.string.books)
126:                 val rows = data.array("items")
143:     val uri = LocalUriHandler.current
144:     val scope = rememberCoroutineScope()
146:     val snackbar = remember { SnackbarHostState() }
150:             val type = if (path.startsWith("series/")) "series" else path.substringBefore('/').removeSuffix("s")
154:     val saved = stringResource(R.string.saved); val failed = stringResource(R.string.network_error)
189:     val settings by vm.settings.collectAsStateWithLifecycle()
190:     val operation by vm.operation.collectAsStateWithLifecycle()
191:     val scope = rememberCoroutineScope()
```

## android/app/src/main/java/org/athar/app/core/ui/MarkToggle.kt

```text
24:     val reduced = reducedMotion()
25:     val haptic = LocalHapticFeedback.current
26:     val scope = rememberCoroutineScope()
27:     val failed = stringResource(R.string.network_error)
28:     val pending = stringResource(R.string.syncing)
29:     val scale by animateFloatAsState(if (selected) 1.04f else 1f, if (reduced) snap() else spring(dampingRatio = AtharMotion.SmallSpringDamping, stiffness = AtharMotion.SmallSpringStiffness), label = "mark-feedback")
30:     val label = stringResource(if (favorite) { if (selected) R.string.remove_favorite else R.string.add_favorite } else { if (selected) R.string.remove_bookmark else R.string.bookmark })
33:             val previous = selected; selected = next; busy = true
```

## android/app/src/main/java/org/athar/app/core/ui/Motion.kt

```text
18:     val Standard = FastOutSlowInEasing
19:     val Enter = LinearOutSlowInEasing
20:     val Exit = FastOutLinearInEasing
27:     val lifecycle = LocalLifecycleOwner.current.lifecycle
29:         val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reduced = !ValueAnimator.areAnimatorsEnabled() }
38:     val reduced = reducedMotion()
```

## android/app/src/main/java/org/athar/app/core/ui/Theme.kt

```text
17: val QuranFont = FontFamily(Font(R.font.amiri_quran))
68:     val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
```

## android/app/src/main/java/org/athar/app/learning/CommentComposer.kt

```text
24:     val focus = remember { FocusRequester() }
```

## android/app/src/main/java/org/athar/app/learning/DiscussionPanel.kt

```text
20:     val state by vm.state.collectAsStateWithLifecycle()
36:                 val id=root.text("id").toInt();val thread=state.threads[id]
60:     val locale=LocalConfiguration.current.locales[0]
61:     val created=row.text("created_at")
62:     val date=remember(created,locale){runCatching {
```

## android/app/src/main/java/org/athar/app/learning/DiscussionViewModel.kt

```text
13: data class CommentThread(val rows: List<JsonElement> = emptyList(), val loading: Boolean = false, val error: Boolean = false, val expanded: Boolean = true, val page: Int = 0, val more: Boolean = false)
14: data class DiscussionPreview(val row: JsonElement? = null, val total: Int = 0)
15: data class DiscussionState(val lesson: Int = 0, val rows: List<JsonElement> = emptyList(), val loading: Boolean = true, val error: Boolean = false, val page: Int = 0, val total: Int = 0, val more: Boolean = false, val so
16: class DiscussionViewModel(private val graph: AppGraph) : ViewModel() {
19:     val preview = state.map { DiscussionPreview(it.rows.firstOrNull(), it.total) }
23:     fun select(id: Int) { if (mutable.value.lesson == id) return; generation++;load?.cancel();mutable.value=DiscussionState(lesson=id);refresh() }
24:     fun draft(value: String) { val end=value.offsetByCodePoints(0,value.codePointCount(0,value.length).coerceAtMost(2000));mutable.value=mutable.value.copy(draft=value.substring(0,end),sendError=null,submitted=false) }
25:     fun reply(row: JsonElement?) { mutable.value=mutable.value.copy(replyTo=row,sendError=null,composerOpen=row!=null) }
26:     fun composer(open: Boolean) { mutable.value=mutable.value.copy(composerOpen=open) }
27:     fun sort(value: String) { if(value!=mutable.value.sort){mutable.value=mutable.value.copy(sort=value);refresh()} }
28:     fun refresh() = roots(true)
29:     fun more() = roots(false)
31:         val snapshot=mutable.value;if(snapshot.lesson<1||(!reset&&snapshot.loading))return
32:         val epoch=generation;val page=if(reset)1 else snapshot.page+1
36:                 val data=graph.session.request("GET","lessons/${snapshot.lesson}/discussion",query=mapOf("page" to "$page","sort" to snapshot.sort))
38:                 val pagination=data.asJsonObject["pagination"]
43:     fun thread(parent: Int, more: Boolean = false) {
44:         val snapshot=mutable.value;val previous=snapshot.threads[parent]
47:         val epoch=generation;val page=if(more)(previous?.page?:0)+1 else 1
51:                 val result=graph.session.request("GET","lessons/${snapshot.lesson}/discussion",query=mapOf("parent_id" to "$parent","page" to "$page","sort" to "oldest"))
53:                 val rows=if(more)previous?.rows.orEmpty()+result.array("items") else result.array("items")
54:                 val updated=CommentThread(rows.distinctBy{it.text("id")},page=page,more=result.asJsonObject["pagination"].text("has_more")=="true")
59:     fun send() {
60:         val snapshot=mutable.value;val trimmed=snapshot.draft.trim();if(snapshot.sending||trimmed.codePointCount(0,trimmed.length) !in 2..2000)return
61:         val parent=snapshot.replyTo?.text("id")?.toInt();val body=snapshot.draft.trim();val fingerprint="${snapshot.lesson}:$parent:$body"
63:         val epoch=generation;mutable.value=snapshot.copy(sending=true,sendError=null)
```

## android/app/src/main/java/org/athar/app/learning/LearningComponents.kt

```text
32:             val video=row.text("youtube_id")
41:                 val duration=row.text("duration_seconds").toIntOrNull()?:0
```

## android/app/src/main/java/org/athar/app/learning/LessonRepository.kt

```text
21: class LessonRepository(private val dao: AtharDao, private val cipher: PrivateCipher, private val session: SessionRepository) : AutoCloseable {
33:     fun checkpoint(id:Int,position:Int,duration:Int,complete:Boolean) {
34:         val user=session.userId ?: return
39:         val user = requireNotNull(session.userId)
41:             val old = saved(user, id)
43:             val bounded = if (duration > 0) position.coerceAtMost(duration) else position
44:             val next = SavedLessonProgress(id, bounded, duration, complete || old?.complete == true, old?.eventId ?: if (complete) UUID.randomUUID().toString() else null, version = (old?.version ?: 0) + 1)
50:         val user = session.userId ?: return@withContext
52:             val snapshot = local.withLock { saved(user, decode(row).lessonId) } ?: continue
58:                 val latest = saved(user, snapshot.lessonId) ?: return@withLock
```

## android/app/src/main/java/org/athar/app/learning/PlayerPolicy.kt

```text
5:     fun validVideo(id: String) = videoPattern.matches(id)
6:     fun safeTime(value: Double): Int? = value.takeIf { it.isFinite() && it in 0.0..86400.0 }?.toInt()
7:     fun youtubeUrl(video: String, position: Int = 0): String {
11:     fun origin(packageName: String): String { require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]+"))); return "https://$packageName/" }
```

## android/app/src/main/java/org/athar/app/learning/ScholarScreen.kt

```text
23: fun ScholarScreen(graph: AppGraph, id: Int, open: (String) -> Unit) {
26:     val uri = LocalUriHandler.current
27:     val path = if (standalone) "lessons" else "series"
28:     val query = buildMap {
42:                         val image = scholar.text("image_url")
44:                             val origin = graph.apiBaseUrl.substringBefore("/api/")
54:                         val source = scholar.text("source_url")
```

## android/app/src/main/java/org/athar/app/learning/SeriesScreen.kt

```text
19:     val snackbar=remember{SnackbarHostState()}
29:             val count=series.text("lesson_count").toIntOrNull()?:0;val complete=series.text("completed_count").toIntOrNull()?:0
```

## android/app/src/main/java/org/athar/app/learning/UpcomingLesson.kt

```text
10: data class EventCountdown(val amount:Int,val unit:CountdownUnit)
12: fun eventCountdown(start:Instant,now:Instant):EventCountdown {
13:     val remaining=Duration.between(now,start)
15:     val seconds=remaining.seconds+if(remaining.nano>0)1 else 0
16:     val minutes=(seconds+59)/60
24: data class UpcomingLesson(
25:     val id:Int,val title:String,val description:String,val status:EventStatus,val start:Instant?,
26:     val scholarId:Int?,val scholarName:String,val seriesId:Int?,val seriesName:String,
27:     val durationMinutes:Int?,val revision:Int,val canWatch:Boolean,
30:         fun from(value:JsonElement):UpcomingLesson {
31:             val status=EventStatus.valueOf(value.text("status").uppercase(java.util.Locale.ROOT))
```

## android/app/src/main/java/org/athar/app/learning/UpcomingScreen.kt

```text
33:     val vm:UpcomingViewModel=viewModel(key="upcoming:${graph.session.userId}:$lesson",factory=object:ViewModelProvider.Factory{
36:     val state by vm.state.collectAsStateWithLifecycle()
37:     val lifecycle=LocalLifecycleOwner.current.lifecycle
57:     val locale=LocalConfiguration.current.locales[0]
58:     val zone=ZoneId.systemDefault()
59:     val date=remember(event.start,locale,zone){event.start?.atZone(zone)?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT).withLocale(locale))}
61:     val reduced=reducedMotion()
64:             val status=when(event.status){EventStatus.LIVE->if(offline)R.string.upcoming_last_live else R.string.upcoming_live;EventStatus.SCHEDULED->R.string.upcoming_scheduled;EventStatus.PUBLISHED->R.string.upcoming_p
85:     val lifecycle=LocalLifecycleOwner.current.lifecycle
87:     val count=eventCountdown(start,now)
88:     val resource=when(count.unit){CountdownUnit.MINUTES->R.plurals.upcoming_minutes;CountdownUnit.HOURS->R.plurals.upcoming_hours;CountdownUnit.DAYS->R.plurals.upcoming_days;else->null}
```

## android/app/src/main/java/org/athar/app/learning/UpcomingViewModel.kt

```text
13: data class UpcomingState(val loading:Boolean=true,val error:Boolean=false,val offline:Boolean=false,val items:List<UpcomingLesson> = emptyList(),val page:Int=0,val hasMore:Boolean=false,val clockOffsetMillis:Long=0)
14: class UpcomingViewModel(private val graph:AppGraph,private val lesson:Int?):ViewModel() {
16:     val state=mutable.asStateFlow()
19:     fun refresh(silent:Boolean=false)=load(reset=true,silent=silent)
20:     fun more(){if(!mutable.value.loading&&mutable.value.hasMore)load(reset=false)}
23:         val previous=mutable.value;val page=if(reset)1 else previous.page+1
27:                 val result=graph.content.get(if(lesson==null)"upcoming-lessons" else "upcoming-lessons/$lesson",if(lesson==null)mapOf("page" to "$page","per_page" to "20") else emptyMap())
28:                 val rows=if(lesson==null)result.data.array("items").map(UpcomingLesson::from) else listOf(UpcomingLesson.from(result.data))
29:                 val items=if(reset)rows else (previous.items+rows).associateBy { it.id }.values.toList()
30:                 val server=result.data.text("server_now").takeIf(String::isNotBlank)?.let(Instant::parse)
31:                 val offset=if(!result.offline&&server!=null)server.toEpochMilli()-System.currentTimeMillis() else 0
```

## android/app/src/main/java/org/athar/app/learning/WatchScreen.kt

```text
32:     val vm:WatchViewModel=viewModel(key="watch:${graph.session.userId}:$initial",factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun<T:ViewModel>create(modelClass:Class<T>):T=WatchViewModel(
33:     val discussion:DiscussionViewModel=viewModel(key="discussion:${graph.session.userId}:$initial",factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>):
34:     val state by vm.state.collectAsStateWithLifecycle();val embed by vm.player.collectAsStateWithLifecycle();val comments by discussion.preview.collectAsStateWithLifecycle()
35:     val progressFailure by graph.lessons.failedUser.collectAsStateWithLifecycle()
38:     val fontScale=LocalDensity.current.fontScale
39:     fun closeFullComments(){fullComments=false;if(!inlineComments)commentsOpen=false}
41:     val scope=rememberCoroutineScope();val snackbar=remember{SnackbarHostState()};val failed=stringResource(R.string.network_error)
42:     val uri=LocalUriHandler.current;val context=LocalContext.current
43:     val detailsScroll=rememberLazyListState()
44:     val lesson=state.lesson;val id=lesson?.text("id")?.toIntOrNull()
49:             val wide=maxWidth>=840.dp || (maxWidth>=600.dp && maxWidth>maxHeight*1.4f)
50:             val availableHeight=maxHeight
51:             val estimatedPlayerHeight=(maxWidth*(if(wide)0.65f else 1f)*9f/16f).coerceAtLeast(200.dp).coerceAtMost(if(wide)(availableHeight-80.dp).coerceAtLeast(200.dp) else androidx.compose.ui.unit.Dp.Infinity)
52:             val inlineFits=availableHeight-estimatedPlayerHeight>=360.dp*fontScale
56:                     val video=lesson.text("youtube_id")
58:                         val playerHeight=(maxWidth*9f/16f).coerceAtLeast(200.dp).coerceAtMost(if(wide)(availableHeight-80.dp).coerceAtLeast(200.dp) else androidx.compose.ui.unit.Dp.Infinity)
73:                             val scholar=lesson.text("scholar_id").toIntOrNull()
95:                                     val preview=comments.row
```

## android/app/src/main/java/org/athar/app/learning/WatchViewModel.kt

```text
11: data class WatchState(val lesson: JsonObject? = null, val loading: Boolean = true, val offline: Boolean = false, val error: Boolean = false, val resume: Int = 0, val completed: Boolean = false, val retry: Int = 0, val en
12: data class EmbedState(val status: String = "loading", val errorCode: Int? = null)
13: class WatchViewModel(private val graph: AppGraph, initial: Int) : ViewModel() {
20:     fun select(id: Int) {
26:                 val result = graph.content.get("lessons/$id/watch")
27:                 val lesson = result.data.asJsonObject
28:                 val local = graph.lessons.saved(requireNotNull(graph.session.userId), id)
29:                 val server = lesson["progress"]?.takeUnless { it.isJsonNull }?.asJsonObject
39:     fun event(video: String, type: String, seconds: Int, length: Int, code: Int) {
40:         val current = mutable.value
56:     fun unavailable(code: Int = -1) { embed.value = EmbedState("error", code) }
57:     fun retry() {
61:     fun flush() {
62:         val current = mutable.value; val id = current.lesson?.text("id")?.toIntOrNull() ?: return
64:         val pos = position; val len = duration; savedPosition = pos
68:         val current = mutable.value; val id = requireNotNull(current.lesson).text("id").toInt();val epoch=generation
```

## android/app/src/main/java/org/athar/app/learning/YouTubePlayer.kt

```text
36:     val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
37:     val language=LocalConfiguration.current.locales[0].language
38:     val latestEvent by rememberUpdatedState(event); val latestFailure by rememberUpdatedState(failure); val latestCheckpoint by rememberUpdatedState(checkpoint)
47:             val observer = LifecycleEventObserver { _, action ->
64:                 val origin=PlayerPolicy.origin(BuildConfig.APPLICATION_ID)
68:                             val raw=message.data.orEmpty()
70:                                 val obj=JSONObject(raw);val id=obj.getString("video")
72:                                 val pos=PlayerPolicy.safeTime(obj.optDouble("position",0.0)) ?: return@runCatching
73:                                 val length=PlayerPolicy.safeTime(obj.optDouble("duration",0.0)) ?: return@runCatching
92:                         val activity=context.activity() ?: return callback.onCustomViewHidden()
94:                         val root=activity.window.decorView as ViewGroup
95:                         val frame=FrameLayout(activity).apply { setBackgroundColor(Color.BLACK);addView(view,FrameLayout.LayoutParams(-1,-1)) }
121:     function emit(type,code){if(!window.atharEvents)return;let p=0,d=0;try{let actual=player.getVideoUrl();if(actual&&new URL(actual).searchParams.get("v")!==current)return;p=player.getCurrentTime()||0;d=player.getDurati
124:     function onYouTubeIframeAPIReady(){player=new YT.Player('player',{width:innerWidth,height:innerHeight,videoId:current,playerVars:{autoplay:0,playsinline:1,fs:1,hl:${JSONObject.quote(if(language=="en")"en" else "ar")}
```

## android/app/src/main/java/org/athar/app/MainActivity.kt

```text
20: class MainActivity : AppCompatActivity() {
26:         val graph = (application as AtharApplication).graph
28:             val vm: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
31:             val settings by vm.settings.collectAsStateWithLifecycle()
32:             val auth by vm.auth.collectAsStateWithLifecycle()
```

## android/app/src/main/java/org/athar/app/media/AudioScreen.kt

```text
30:     val app = LocalContext.current.applicationContext as android.app.Application
31:     val vm: AudioViewModel = viewModel(key = "audio:${graph.session.userId}", factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = Audi
32:     val state by vm.state.collectAsStateWithLifecycle()
33:     val selection by vm.selection.collectAsStateWithLifecycle()
34:     val surah = selection.surah; val ayah = selection.ayah
35:     val selected = selection.reader; val edition = selection.edition
36:     val verseMode = selection.verseMode; val follow = selection.follow
38:     val selectedIsLoaded = !state.radio && state.mediaKey == selection.mediaKey
39:     fun update(value: AudioSelection) = vm.select(value)
44:       val controlsHeight = maxHeight * if (radio) 0.4f else if (follow && selectedIsLoaded) 0.6f else 1f
87:     val lifecycle = LocalLifecycleOwner.current.lifecycle
94:     val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
```

## android/app/src/main/java/org/athar/app/media/AudioSelection.kt

```text
8: data class AudioSelection(
9:     val surah: Int = 1,
10:     val ayah: Int = 1,
11:     val reader: JsonElement? = null,
12:     val edition: JsonElement? = null,
13:     val verseMode: Boolean = false,
14:     val follow: Boolean = false,
16:     fun withReader(value: JsonElement): AudioSelection = copy(reader = value, edition = supported(value).firstOrNull())
17:     fun withSurah(number: Int): AudioSelection {
19:         val next = copy(surah = number, ayah = 1)
20:         val available = next.supported(reader)
23:     fun supported(value: JsonElement? = reader): List<JsonElement> = value?.array("editions")?.filter { edition ->
26:     val mediaKey: String? get() = reader?.let { r -> edition?.let { e -> "${r.text("id")}:${e.text("id")}:$surah:${if (verseMode) ayah else 0}" } }
```

## android/app/src/main/java/org/athar/app/media/AudioViewModel.kt

```text
18: data class AyahTiming(val ayah: Int, val start: Long, val end: Long)
19: data class AudioUiState(val loading: Boolean = false, val error: Boolean = false, val recording: JsonElement? = null, val timings: List<AyahTiming> = emptyList(), val playing: Boolean = false, val buffer: Boolean = false
21: class AudioViewModel(private val graph: AppGraph, application: android.app.Application) : AndroidViewModel(application) {
23:     val state = mutable.asStateFlow()
25:     val selection = mutableSelection.asStateFlow()
26:     fun select(value: AudioSelection) {
27:         val previous = mutableSelection.value
28:         val continuePlayback = mutable.value.playing && !mutable.value.radio && mutable.value.mediaKey == previous.mediaKey
34:     fun startSelection() {
35:         val value = mutableSelection.value
36:         val reader = value.reader ?: return
37:         val edition = value.edition ?: return
40:     fun playSelection() {
54:         val ready = suspendCancellableCoroutine<MediaController> { continuation ->
59:     fun load(reciter: Int, edition: Int, surah: Int, ayah: Int? = null) {
64:                 val player = player(); player.stop(); player.clearMediaItems()
65:                 val query = mapOf("reciter" to "$reciter", "edition" to "$edition", "surah" to "$surah")
66:                 val audio = graph.content.get("quran/audio", query).data
67:                 val mediaKey = "$reciter:$edition:$surah:${ayah ?: 0}"
68:                 val item = MediaItem.Builder().setMediaId(mediaKey).setUri(audio.text("url"))
79:                 val selected = ayah?.let { number -> timings.firstOrNull { it.ayah == number } ?: throw IllegalArgumentException("Timing unavailable") }
86:     fun radio(row: JsonElement) {
93:     fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); it.play() } } }
94:     fun stop() { request?.cancel(); controller?.stop(); mutable.value = AudioUiState() }
95:     fun position(): Long = (controller?.currentPosition ?: 0) + mutable.value.clipStart
```

## android/app/src/main/java/org/athar/app/media/PlaybackService.kt

```text
17: class PlaybackService : MediaSessionService() {
22:         val player = ExoPlayer.Builder(this).build().apply {
33:                 val safe = mediaItems.filter { item -> item.localConfiguration?.uri?.let(::approvedMedia) == true }.toMutableList()
42:         fun approvedMedia(uri: Uri): Boolean {
43:             val host = uri.host?.lowercase() ?: return false
```

## android/app/src/main/java/org/athar/app/nightprayer/NightCalendar.kt

```text
6: data class CalendarNight(val date: LocalDate, val verseCount: Int, val exempt: Boolean)
8:     fun month(month: YearMonth, totals: Map<LocalDate, Int>, exemptions: Set<LocalDate> = emptySet()): List<CalendarNight> =
```

## android/app/src/main/java/org/athar/app/nightprayer/NightCalendarScreen.kt

```text
26:     val state by vm.state.collectAsStateWithLifecycle()
29:     val month = remember(monthKey) { YearMonth.parse(monthKey) }
30:     val selected = remember(selectedKey) { LocalDate.parse(selectedKey) }
33:     val totals = remember(state.rows) { state.rows.groupBy { LocalDate.parse(it.first.nightDate) }.mapValues { (_, rows) -> rows.sumOf { it.first.verseCount } } }
34:     val days = remember(month, totals, exemptions) { NightCalendar.month(month, totals, exemptions) }
35:     val selectedRows = remember(state.rows, selectedKey) { state.rows.filter { it.first.nightDate == selectedKey } }
36:     val locale = Locale.getDefault()
37:     val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale) }
38:     val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL uuuu", locale) }
54:                     val agenda = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
59:                         val offset = month.atDay(1).dayOfWeek.value - 1
60:                         val cells: List<CalendarNight?> = List(offset) { null } + days
84:     val status = stringResource(if (day.exempt) R.string.calendar_local_exemption else if (day.verseCount > 0) R.string.calendar_recorded else R.string.calendar_no_entry)
85:     val description = "${day.date.format(format)}، $status، ${stringResource(R.string.night_session_count, day.verseCount)}"
```

## android/app/src/main/java/org/athar/app/nightprayer/NightPrayerRepository.kt

```text
19: class NightPrayerRepository(private val database: AppDatabase, private val session: SessionRepository, private val cipher: PrivateCipher) {
23:     fun observe(user: Long) = dao.nights(user).map { rows -> rows.map { it to JsonParser.parseString(cipher.open(it.payloadCipher)).asJsonObject } }.flowOn(Dispatchers.Default)
26:         val user = user()
29:             val previous = dao.night(user, id)
31:             val expected = previous?.localRevision ?: 0
32:             val revision = expected + 1
33:             val body = payload.deepCopy().apply { remove("id"); remove("revision"); remove("deleted") }
34:             val mutation = UUID.randomUUID().toString()
35:             val outgoing = body.deepCopy().apply { addProperty("mutation_id", mutation); addProperty("expected_revision", expected) }
36:             val count = if (body["entry_type"].asString == "range") body["end_index"].asInt - body["start_index"].asInt + 1 else body["verse_count"].asInt
44:         val user = user()
46:             val previous = requireNotNull(dao.night(user, id)); if (previous.deleted) return@withTransaction
47:             val mutation = UUID.randomUUID().toString()
48:             val body = JsonObject().apply { addProperty("mutation_id", mutation); addProperty("expected_revision", previous.localRevision) }
55:         val user = user()
61:             val pending = dao.pending(user)
62:             val blocked = pending.filter { it.conflict }.map { it.sessionId }.toSet()
63:             val eligible = pending.filter { it.sessionId !in blocked }
64:             val cursorDoc = dao.cache(user, "night-sync-cursor")
65:             val cursor = cursorDoc?.let { cipher.open(it.payloadCipher).toLong() } ?: 0L
66:             val operations = eligible.map { mapOf("id" to it.sessionId, "operation" to it.operation, "data" to JsonParser.parseString(cipher.open(it.payloadCipher))) }
67:             val reply = session.request("POST", "night-prayer/sync", mapOf("cursor" to cursor, "operations" to operations),expectedUser=user).asJsonObject
71:                     val result = element.asJsonObject; val mutation = eligible[index]
78:                     val item = element.asJsonObject; val id = item["id"].asString
91:         val user = user(); val remote = session.request("GET", "night-prayer/sessions/$id",expectedUser=user).asJsonObject
97:         val deleted = item["deleted"].asBoolean; val revision = item["revision"].asInt
```

## android/app/src/main/java/org/athar/app/nightprayer/NightPrayerScreen.kt

```text
25: data class NightUiState(val rows: List<Pair<LocalNightSession, JsonObject>> = emptyList(), val busy: Boolean = false, val error: Boolean = false, val conflicts: Int = 0)
26: class NightViewModel(val graph: AppGraph) : ViewModel() {
29:     val state = mutable.asStateFlow()
31:     fun sync() = operation { mutable.value = mutable.value.copy(conflicts = graph.nightPrayer.sync()) }
32:     fun save(payload: JsonObject, id: String?, done: () -> Unit) = operation { if (id == null) graph.nightPrayer.save(payload) else graph.nightPrayer.save(payload, id); done() }
33:     fun delete(id: String) = operation { graph.nightPrayer.delete(id) }
34:     fun useServer(id: String) = operation { graph.nightPrayer.useServer(id); mutable.value = mutable.value.copy(conflicts = 0) }
47:     val vm: NightViewModel = viewModel(key = "night:${graph.session.userId}", factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = Nigh
59:     val vm: NightViewModel = viewModel(key = "night:${graph.session.userId}", factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = Nigh
60:     val state by vm.state.collectAsStateWithLifecycle()
64:     val totals = remember(state.rows) { state.rows.groupBy { it.first.nightDate }.mapValues { (_, list) -> list.sumOf { it.first.verseCount } } }
70:             val pace = NightRules.pace(state.rows.mapNotNull { row -> row.first.measuredSeconds?.let { row.first.verseCount to it } })
102:     val initial = editing?.second
111:     val scope = rememberCoroutineScope()
113:         val index = graph.quran.data().index
114:         val start = index.position(initial["start_index"].asInt); val end = index.position(initial["end_index"].asInt)
132:                 val parsed = LocalDate.parse(date); val tz = ZoneId.of(zone); require(parsed <= LocalDate.now(tz) && parsed.year >= 1900)
133:                 val body = JsonObject().apply {
```

## android/app/src/main/java/org/athar/app/nightprayer/NightRules.kt

```text
5: data class NightContext(val date: LocalDate, val zone: ZoneId, val maghrib: Instant, val fajr: Instant) {
7:     val lastThird: Instant get() = maghrib.plusSeconds(Duration.between(maghrib, fajr).seconds * 2 / 3)
8:     fun contains(instant: Instant) = instant >= maghrib && instant < fajr
11:     fun serverTimezone(zone: ZoneId): String = if (zone.normalized() == ZoneOffset.UTC) "UTC" else zone.id
12:     fun milestone(count: Int): Int = when { count >= 1000 -> 1000; count >= 100 -> 100; count >= 10 -> 10; else -> 0 }
13:     fun pace(sessions: List<Pair<Int, Int>>): Double? {
14:         val measured = sessions.filter { it.first > 0 && it.second > 0 }
17:     fun streak(totals: Map<LocalDate, Int>, threshold: Int, current: LocalDate, nightInProgress: Boolean, exemptions: Set<LocalDate> = emptySet()): Pair<Int, Int> {
19:         val eligible = totals.filterValues { it >= threshold }.keys
23:         val first = eligible.minOrNull()!!
26:         val last = eligible.maxOrNull()!!
```

## android/app/src/main/java/org/athar/app/nightprayer/NightToolsScreen.kt

```text
33:     val scope = rememberCoroutineScope()
63:                 val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(context.zone)
```

## android/app/src/main/java/org/athar/app/nightprayer/PrayerCalculator.kt

```text
8:     val methods = listOf("EGYPTIAN", "MUSLIM_WORLD_LEAGUE", "UMM_AL_QURA", "KARACHI", "DUBAI", "MOON_SIGHTING_COMMITTEE")
9:     fun calculate(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId, method: String, hanafi: Boolean = false, highLatitude: String = "MIDDLE_OF_THE_NIGHT"): NightContext {
12:         val parameters = CalculationMethod.valueOf(method).parameters.apply { madhab = if (hanafi) Madhab.HANAFI else Madhab.SHAFI; highLatitudeRule = HighLatitudeRule.valueOf(highLatitude) }
13:         val coordinates = Coordinates(latitude, longitude)
14:         fun prayers(day: LocalDate) = PrayerTimes(coordinates, DateComponents(day.year, day.monthValue, day.dayOfMonth), parameters)
15:         val today = prayers(date); val tomorrow = prayers(date.plusDays(1))
16:         val maghrib = requireNotNull(today.maghrib).toInstant()
17:         val fajr = requireNotNull(tomorrow.fajr).toInstant()
```

## android/app/src/main/java/org/athar/app/quran/QuranIndex.kt

```text
6: data class SurahReference(val number: Int, val name: String, @SerializedName("english_name") val englishName: String, @SerializedName("ayah_count") val ayahCount: Int, @SerializedName("start_index") val startIndex: Int)
7: data class QuranPosition(val surah: Int, val ayah: Int, val globalIndex: Int)
8: data class QuranRange(val start: QuranPosition, val end: QuranPosition) { val verseCount: Int get() = end.globalIndex - start.globalIndex + 1 }
9: data class SectionReference(val number: Int, val surah: Int, val ayah: Int)
12: class QuranIndex(val surahs: List<SurahReference>, val sections: Map<String, List<SectionReference>>) {
18:     fun global(surah: Int, ayah: Int): Int {
20:         val reference = surahs[surah - 1]
24:     fun position(global: Int): QuranPosition {
28:             val middle = (low + high) / 2; val surah = surahs[middle]
33:     fun range(startSurah: Int, startAyah: Int, endSurah: Int, endAyah: Int): QuranRange {
34:         val start = global(startSurah, startAyah); val end = global(endSurah, endAyah)
38:     fun target(surah: Int, ayah: Int, count: Int): QuranRange {
39:         val start = global(surah, ayah)
43:     fun section(kind: String, number: Int): IntRange {
44:         val key = if (kind == "hizb") "hizb_quarter" else kind
45:         val refs = sections[key] ?: error("Invalid section")
46:         val step = if (kind == "hizb") 4 else 1
48:         val offset = (number - 1) * step
49:         val start = global(refs[offset].surah, refs[offset].ayah)
50:         val end = refs.getOrNull(offset + step)?.let { global(it.surah, it.ayah) - 1 } ?: 6236
```

## android/app/src/main/java/org/athar/app/quran/QuranRepository.kt

```text
13: data class AyahText(val position: QuranPosition, val text: String, val bismillah: String?)
14: class QuranRepository(private val context: Context) {
19:             val index = QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use { it.readText() })
20:             val bytes = context.assets.open("quran/tanzil-uthmani.xml").use { it.readBytes() }
21:             val source = JsonParser.parseString(context.assets.open("quran/SOURCE.json").bufferedReader().use { it.readText() }).asJsonObject
22:             val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
24:             val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(bytes.inputStream(), "UTF-8") }
25:             val verses = ArrayList<AyahText>(6236)
39: class QuranData(val index: QuranIndex, val verses: List<AyahText>) {
41:     fun surah(number: Int): List<AyahText> { val start = index.global(number, 1); return verses.subList(start - 1, start - 1 + index.surahs[number - 1].ayahCount) }
42:     fun section(kind: String, number: Int): List<AyahText> { val range = index.section(kind, number); return verses.subList(range.first - 1, range.last) }
43:     fun search(query: String): List<AyahText> { val normalized = normalize(query); require(normalized.length >= 2); return verses.filterIndexed { offset, _ -> searchText[offset].contains(normalized) } }
48:         fun normalize(text: String): String = text.replace(marks, "").replace(alifs, "ا").replace('ى', 'ي').replace(whitespace, " ").trim()
```

## android/app/src/main/java/org/athar/app/quran/QuranScreen.kt

```text
26: data class QuranState(val loading: Boolean = true, val index: QuranIndex? = null, val verses: List<AyahText> = emptyList(), val surah: Int = 1, val error: Boolean = false, val sectionKind: String = "surah", val sectionNu
27: class QuranViewModel(private val graph: AppGraph) : ViewModel() {
29:     val state = mutable.asStateFlow()
31:     val bookmarks = mutableBookmarks.asStateFlow()
32:     val reading = graph.reading.observe(requireNotNull(graph.session.userId)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
42:     fun select(number: Int, ayah: Int = 1) {
49:     fun section(kind: String, number: Int) {
57:     fun search(query: String) {
91:     val vm: QuranViewModel = viewModel(key = "reader:${graph.session.userId}", factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = Qur
92:     val state by vm.state.collectAsStateWithLifecycle()
93:     val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
94:     val reading by vm.reading.collectAsStateWithLifecycle()
95:     val listState = rememberLazyListState()
105:     val scope = rememberCoroutineScope()
106:     val snackbar = remember { SnackbarHostState() }
107:     val saved = stringResource(R.string.saved); val failed = stringResource(R.string.network_error)
144:                 val maximum = when(state.sectionKind) { "page" -> 604; "juz" -> 30; "hizb" -> 60; else -> 114 }
182:         val maximum = when(state.sectionKind) { "page" -> 604; "juz" -> 30; else -> 60 }
```

## android/app/src/main/java/org/athar/app/quran/ReadingRepository.kt

```text
19: data class SavedReadingPosition(
20:     val surah: Int,
21:     val ayah: Int,
22:     val revision: Int = 0,
23:     val version: Long = 1,
24:     val pending: Boolean = false,
25:     val conflict: Boolean = false
30: class ReadingRepository(
53:     fun observe(user: Long): Flow<SavedReadingPosition?> = dao.observeCache(user, key).map { it?.let(::decode) }.flowOn(Dispatchers.Default)
60:         val user = requireNotNull(session.userId)
62:             val old = saved(user)
71:         val data = session.request("GET", "quran/position", expectedUser = user)
77:         val row = data.asJsonObject
78:         val surah = row["surah"].asInt
79:         val ayah = row["ayah"].asInt
80:         val revision = row["revision"].asInt
87:         val user = session.userId ?: return@withContext
88:         val server = fetch(user)
91:             val current = saved(user)
101:         val user = session.userId ?: return@withContext
102:         val snapshot = local.withLock { saved(user) } ?: return@withContext
104:         val server = try {
112:             val current = saved(user) ?: return@withLock
113:             val acknowledged = server != null && server.surah == snapshot.surah && server.ayah == snapshot.ayah && server.revision > snapshot.revision
124:         val user = requireNotNull(session.userId)
125:         val server = fetch(user)
128:             val current = saved(user)
```

## android/app/src/main/java/org/athar/app/quran/TafsirScreen.kt

```text
27:     val scope = rememberCoroutineScope()
46:             val uri = LocalUriHandler.current
```

## android/app/src/test/java/org/athar/app/AudioSelectionTest.kt

```text
8: class AudioSelectionTest {
11:         val first = AudioSelection().withReader(reader)
14:         val second = first.copy(ayah = 5).withSurah(2)
22:         val full = AudioSelection().withReader(reader)
24:         val verse = full.copy(verseMode = true, ayah = 3)
```

## android/app/src/test/java/org/athar/app/DomainTest.kt

```text
10: class DomainTest {
12:         val month = java.time.YearMonth.of(2028, 2)
13:         val first = month.atDay(1); val second = month.atDay(2)
14:         val days = org.athar.app.nightprayer.NightCalendar.month(month, mapOf(first to 100, month.minusMonths(1).atDay(1) to 50), setOf(second))
23:         val original = "ٱلْحَمْدُ لِلَّهِ"
30:         val index = index()
35:         val index = index()
43:         val index = index()
51:         val zone = ZoneId.of("America/New_York")
52:         val maghrib = ZonedDateTime.of(2026, 10, 31, 18, 0, 0, 0, zone).toInstant()
53:         val fajr = ZonedDateTime.of(2026, 11, 1, 5, 0, 0, 0, zone).toInstant()
54:         val context = NightContext(LocalDate.of(2026, 10, 31), zone, maghrib, fajr)
62:         val today = LocalDate.of(2026, 10, 25)
63:         val totals = mapOf(today to 100, today.minusDays(2) to 100)
69:         val date = LocalDate.of(2026, 10, 25); val zone = ZoneId.of("Africa/Cairo")
71:             val context = PrayerCalculator.calculate(date, 30.0444, 31.2357, zone, method)
```

## android/app/src/test/java/org/athar/app/OtpInputTest.kt

```text
7: class OtpInputTest {
19:         val normalized = OtpInput.normalize("۱۲٣456789")
```

## android/app/src/test/java/org/athar/app/PlayerPolicyTest.kt

```text
5: class PlayerPolicyTest {
```

## android/app/src/test/java/org/athar/app/SessionRepositoryTest.kt

```text
13: class SessionRepositoryTest {
16:         val repo=SessionRepository(FakeTransport{path,_->if(path=="auth/login")reply()else{writes++;JsonParser.parseString("{}")}},MemoryStore())
33:         val store = MemoryStore(); val repo = SessionRepository(FakeTransport { _, _ -> reply(scope = "verification", verified = false) }, store)
40:         val repo = SessionRepository(FakeTransport { path, _ -> when(path) { "auth/refresh" -> { rotations++; yield(); reply("b") }; "auth/login" -> reply(); else -> JsonParser.parseString("{}") } }, store, now = { now }
47:         val repo = SessionRepository(FakeTransport { path, _ -> if (path == "auth/refresh") { started.complete(Unit); finish.await(); reply("b") } else reply() }, store, now = { now })
49:         val request = async { runCatching { repo.request("GET", "home") } }
55:         val repo = SessionRepository(FakeTransport { path, _ -> if (path == "auth/refresh") throw ApiException(401, "unauthenticated", "") else reply() }, store, now = { now })
```

## android/app/src/test/java/org/athar/app/UpcomingPolicyTest.kt

```text
9: class UpcomingPolicyTest {
11:         val now=Instant.parse("2026-10-25T17:00:00Z")
22:         val row=JsonParser.parseString("""{"id":5,"title":"Fixture","status":"scheduled","scheduled_at":"2020-01-01T00:00:00Z","revision":2,"can_watch":true}""")
23:         val event=UpcomingLesson.from(row)
```

## app/backup-assets.php

```text
5: function backup_asset_keys(string $kind,array $rows):array
15: function backup_add_assets(ZipArchive $zip,string $storage,string $kind,array $keys):array
27: function backup_read_assets(ZipArchive $zip,string $kind,array $manifest,array $expectedKeys):array
```

## app/backup.php

```text
7: function backup_directory(): string
12: function backup_key(): string
21: function ensure_backup_directory(): string
29: function append_owner_audit(array $event): void
39: function assert_no_maintenance_lock(): void
49: function acquire_maintenance_lock(): void
55: function release_maintenance_lock(): void
60: function write_encrypted_backup(string $sourcePath, string $targetPath, string $key): string
86: function read_exact($stream, int $length): string
98: function decrypt_backup_file(string $sourcePath, string $targetPath, string $key): string
130: function build_backup_archive(int $ownerId): array
205: function expire_backup_artifacts(): void
219: function restore_backup_archive(int $backupId, int $ownerId): void
```

## app/helpers.php

```text
6: function e(string $value): string
11: function db(): PDO
19: function csrf_token(): string
25: function csrf_field(): string
30: function require_csrf(): void
39: function current_user(): ?array
44: function require_login(): array
81: function require_admin(): array
91: function require_owner(): array
101: function flash(string $message, string $type = 'success'): void
106: function take_flash(): ?array
113: function redirect(string $path): void
119: function is_https_url(string $url): bool
126: function quran_ayah_count(int $surah): int
132: function youtube_video_id(string $url): ?string
150: function validated_lesson_scope(int $scholarId, int $seriesId): array
171: function store_scholar_image(array $file, string $storage): string
188: function delete_scholar_image(?string $key, string $storage): void
```

## app/identity.php

```text
3: function web_identity_otp(): \Athar\Identity\OtpService
13: function web_identity_auth(): \Athar\Identity\AuthService
18: function web_identity_rate(string $scope,string $identity,int $limit,int $window): bool
```

## app/mailer.php

```text
4: function platform_setting(string $key, string $default = ''): string
12: function save_platform_setting(string $key, string $value, ?int $actorId): void
17: function setting_cipher_key(): string
47: function seal_platform_secret(string $secret): string
55: function open_platform_secret(string $sealed): string
64: function platform_email_base_url(): string
76: function email_campaign_unsubscribe_url(int $userId): string
82: function platform_email_html(string $subject, string $body, string $footerHtml = '', ?string $customBodyHtml = null): string
99: function platform_secret(string $key, string $environmentName = ''): string
106: function smtp_read_response($socket): array
117: function smtp_command($socket, string $command, array $expected): string
129: function send_platform_email(string $recipient, string $subject, string $body, ?string &$failureReason = null, ?string $htmlBody = null, string $htmlFooter = '', string $category = 'transactional'): bool
```

## database/migrations/001_initial.sql

```text
4: CREATE TABLE IF NOT EXISTS users (
14: CREATE TABLE IF NOT EXISTS quran_bookmarks (
25: CREATE TABLE IF NOT EXISTS quran_positions (
33: CREATE TABLE IF NOT EXISTS khatmah_plans (
48: CREATE TABLE IF NOT EXISTS scholars (
60: CREATE TABLE IF NOT EXISTS lessons (
75: CREATE TABLE IF NOT EXISTS books (
91: CREATE TABLE IF NOT EXISTS adhkar_items (
106: CREATE TABLE IF NOT EXISTS audit_logs (
116: CREATE TABLE IF NOT EXISTS schema_migrations (
```

## database/migrations/002_lesson_series.sql

```text
3: CREATE TABLE IF NOT EXISTS series (
16: ALTER TABLE lessons ADD COLUMN series_id BIGINT UNSIGNED NULL AFTER scholar_id;
17: ALTER TABLE lessons ADD KEY idx_lesson_series_order (series_id, sort_order);
18: ALTER TABLE lessons ADD CONSTRAINT fk_lesson_series FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE SET NULL;
```

## database/migrations/003_favorites_and_video_progress.sql

```text
3: CREATE TABLE user_favorites (
14: CREATE TABLE video_progress (
```

## database/migrations/004_owner_backups_and_auth_tokens.sql

```text
3: ALTER TABLE users MODIFY role ENUM('user','admin','owner') NOT NULL DEFAULT 'user';
4: ALTER TABLE users ADD COLUMN session_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER role;
6: CREATE TABLE auth_tokens (
19: CREATE TABLE backup_artifacts (
36: CREATE TABLE backup_download_grants (
```

## database/migrations/005_account_verification_and_preferences.sql

```text
3: ALTER TABLE users
```

## database/migrations/006_book_catalog_metadata.sql

```text
3: ALTER TABLE books
```

## database/migrations/007_accounts_email_and_activity.sql

```text
3: ALTER TABLE users
8: ALTER TABLE auth_tokens
12: ALTER TABLE books ADD COLUMN download_url VARCHAR(500) NULL AFTER source_url;
14: CREATE TABLE platform_settings (
22: CREATE TABLE account_daily_stats (
33: CREATE TABLE lesson_comments (
```

## database/migrations/008_email_campaigns.sql

```text
3: ALTER TABLE users
6: CREATE TABLE email_campaigns (
18: CREATE TABLE email_campaign_recipients (
35: CREATE TABLE email_send_events (
```

## database/migrations/009_scholar_images.sql

```text
3: ALTER TABLE scholars
```

## database/migrations/010_versioned_identity.sql

```text
2: ALTER TABLE users ADD COLUMN suspended_at DATETIME NULL AFTER session_version;
4: ALTER TABLE auth_tokens
9: CREATE TABLE api_sessions (
31: CREATE TABLE auth_rate_buckets (
```

## database/migrations/011_native_progress.sql

```text
1: ALTER TABLE quran_positions ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 1;
2: ALTER TABLE user_favorites MODIFY target_type ENUM('book','lesson','series') NOT NULL;
4: CREATE TABLE activity_events (
15: CREATE TABLE adhkar_progress (
```

## database/migrations/012_night_prayer.sql

```text
1: CREATE TABLE night_prayer_sessions (
27: CREATE TABLE night_prayer_changes (
38: CREATE TABLE night_prayer_mutations (
48: CREATE TABLE night_prayer_preferences (
```

## database/migrations/013_lesson_discussion.sql

```text
1: ALTER TABLE lesson_comments
```

## database/migrations/014_lesson_events.sql

```text
1: CREATE TABLE lesson_events (
26: CREATE TABLE lesson_translations (
35: CREATE TABLE lesson_event_changes (
```

## public/assets/js/recitation-audio-worklet.js

```text
1: class AtharReviewCaptureProcessor extends AudioWorkletProcessor {
```

## public/assets/js/recitation-worker.js

```text
13: async function fetchJson(url) {
24: async function fetchModel(url) {
54: async function initialize(quran) {
```

## public/index.php

```text
33: if ($path === '/api/v1' || str_starts_with($path, '/api/v1/')) require dirname(__DIR__) . '/backend/api.php';
35: if ($path === '/email-unsubscribe') {
82: if ($path === '/robots.txt' && $method === 'GET') {
90: if ($path === '/sitemap.xml' && $method === 'GET') {
101: function send_account_token(string $email, string $type): bool
109: function allow_session_rate(string $key, int $limit, int $windowSeconds): bool
125: function allow_shared_dorar_rate(int $limit, int $windowSeconds): bool
148: if ($path === '/verify-email') redirect(current_user() ? '/account' : '/login');
150: if ($path === '/forgot-password') {
156: if ($path === '/reset-password') {
174: if ($path === '/api/quran/progress' && $method === 'POST') {
197: if ($path === '/api/favorites' && $method === 'POST') {
211: if ($path === '/api/adhkar/completion' && $method === 'POST') {
218: if ($path === '/api/lesson/progress' && $method === 'POST') {
238: if ($path === '/lesson-comment' && $method === 'POST') {
252: if ($path === '/scholar-image' && $method === 'GET') {
262: if ($path === '/book-download' && $method === 'GET') {
273: if ($path === '/backup-download' && $method === 'POST') {
400: if ($path === '/logout' && $method === 'POST') {
412: if ($path === '/account') {
539: if ($path === '/admin') {
1025: if ($path === '/scholar' && empty($publicScholar['id'])) $isPublicCanonical = false;
1026: if ($path === '/series' && empty($publicSeries['id'])) $isPublicCanonical = false;
1033: if ($path === '/') $seoDescription = 'اقرأ القرآن، وابحث عن الحديث، وتابع دروس العلماء، واستكشف الكتب والأذكار في منصة أثر العربية.';
```

## public/router.php

```text
8: if ($path === '/athar-lan-ca.mobileconfig' && $requestedFile !== false && is_file($requestedFile)) {
```

## tests/api_identity_test.php

```text
14: function expect(bool $condition,string $name):void {global $assertions;$assertions++;if(!$condition)throw new RuntimeException('FAIL: '.$name);}
15: function denied(callable $call,int $status,string $name):void {try{$call();}catch(ApiError $e){expect($e->status===$status,$name);return;}expect(false,$name);}
```

## tests/backup_assets_test.php

```text
5: function check(bool $value,string $message):void{global $assertions;$assertions++;if(!$value)throw new RuntimeException($message);}
6: function fails(callable $call,string $message):void{try{$call();}catch(RuntimeException $e){check(true,$message);return;}check(false,$message);}
```

## tests/catalog_progress_test.php

```text
8: function reject_progress(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $e){assert_progress($e->status===$status,$name);return;}catch(InvalidArgumentException $e){assert_progress($status===422,
```

## tests/domain_test.php

```text
10: function check(bool $condition, string $name): void { global $checks; $checks++; if (!$condition) throw new RuntimeException('FAIL: ' . $name); }
11: function rejects(callable $call, string $name): void { try { $call(); } catch (InvalidArgumentException $e) { check(true, $name); return; } check(false, $name); }
```

## tests/integration_support.php

```text
7: function isolated_database(callable $test):void
17: function test_uuid():string{$bytes=random_bytes(16);$bytes[6]=chr((ord($bytes[6])&15)|64);$bytes[8]=chr((ord($bytes[8])&63)|128);$hex=bin2hex($bytes);return substr($hex,0,8).'-'.substr($hex,8,4).'-'.substr($hex,12,4).'-'
```

## tests/learning_test.php

```text
8: function learning_assert(bool $condition,string $label):void{global $checks;$checks++;if(!$condition)throw new RuntimeException('FAIL: '.$label);}
9: function learning_denied(callable $call,int $status,string $label):void{try{$call();}catch(ApiError $e){learning_assert($e->status===$status,$label);return;}learning_assert(false,$label);}
```

## tests/media_provider_test.php

```text
7: function media_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
8: function media_reject(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $e){media_check($e->status===$status,$name);return;}media_check(false,$name);}
```

## tests/night_prayer_api_test.php

```text
11: function assert_night(bool $condition,string $name):void{global $checks;$checks++;if(!$condition)throw new RuntimeException('FAIL: '.$name);}
```

## tests/tafsir_provider_test.php

```text
7: function tafsir_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
8: function tafsir_reject(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $e){tafsir_check($e->status===$status,$name);return;}catch(InvalidArgumentException $e){tafsir_check($status===422,$name);r
```

## tests/upcoming_lessons_test.php

```text
8: function event_assert(bool $condition,string $label):void{global $checks;$checks++;if(!$condition)throw new RuntimeException('FAIL: '.$label);}
9: function event_denied(callable $call,int $status,string $label):void{try{$call();}catch(ApiError $e){event_assert($e->status===$status,$label);return;}event_assert(false,$label);}
```
