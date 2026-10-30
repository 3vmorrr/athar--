package org.athar.app.khatmah

import com.google.gson.*
import java.time.LocalDate
import java.util.UUID

internal data class KhatmahIntent(
    val id: String,
    val kind: String,
    val draft: KhatmahDraft? = null,
    val expectedRevision: Int? = null,
)

/** Immutable intent journal. Server revisions and visible local edits have separate identities. */
internal data class KhatmahJournal(
    val owner: Long,
    val namespace: String,
    val version: Long = 0,
    val serverKnown: Boolean = false,
    val acknowledged: KhatmahPlan? = null,
    val intents: List<KhatmahIntent> = emptyList(),
    val conflicted: Boolean = false,
    val serverConflict: KhatmahPlan? = null,
    val rejected: Int? = null,
) {
    val blocked: Boolean get() = conflicted || rejected != null
    fun projected(): KhatmahPlan? = intents.fold(acknowledged, ::applyIntent)

    fun checked(): KhatmahJournal {
        require(owner > 0 && namespace.matches(Regex("[a-f0-9]{12}")) && version in 0 until Long.MAX_VALUE)
        require(acknowledged == null || (serverKnown && acknowledged.revision > 0))
        require(serverConflict == null || (conflicted && serverConflict.revision > 0))
        require(rejected == null || (intents.isNotEmpty() && rejected in setOf(400, 404, 405, 410, 422)))
        require(intents.size <= MAX_INTENTS && intents.map { it.id }.toSet().size == intents.size)
        intents.forEachIndexed { index, intent ->
            require(intent.id.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")))
            require(intent.kind in setOf("save", "pause", "resume", "finish", "restart"))
            require((intent.kind in setOf("save", "restart")) == (intent.draft != null))
            require(intent.expectedRevision == null || (index == 0 && intent.expectedRevision == (acknowledged?.revision ?: 0)))
        }
        projected() // Invalid retained transitions are unreadable, never silently repaired.
        return this
    }

    fun append(kind: String, draft: KhatmahDraft?, expectedVersion: Long, today: LocalDate): KhatmahJournal {
        checked(); require(version == expectedVersion && !blocked)
        val current = projected()
        if (kind in setOf("save", "restart")) requireNotNull(draft).values(today, if (kind == "restart") null else current)
        if (kind == "resume") requireNotNull(current).let { require(!it.target.isBefore(today)) }
        val intent = KhatmahIntent(UUID.randomUUID().toString(), kind, draft)
        val next = applyIntent(current, intent)
        if (kind == "save" && sameValues(current, next)) return this
        // Only a never-dispatched trailing save can be replaced. Lifecycle boundaries stay ordered.
        val queue = if (kind == "save" && intents.lastOrNull()?.let { it.kind == "save" && it.expectedRevision == null } == true)
            intents.dropLast(1) + intent else intents + intent
        return copy(version = version + 1, intents = queue).checked()
    }

    fun prepare(): KhatmahJournal {
        checked(); require(!blocked)
        val head = intents.firstOrNull() ?: return this
        return if (head.expectedRevision != null) this else copy(intents = listOf(head.copy(expectedRevision = acknowledged?.revision ?: 0)) + intents.drop(1)).checked()
    }

    fun accept(id: String, server: KhatmahPlan): KhatmahJournal {
        checked()
        if (blocked) return this
        val head = intents.firstOrNull() ?: return this
        if (head.id != id) return this // A former ACK cannot remove a different/newer intent.
        val expected = requireNotNull(head.expectedRevision)
        require(server.revision > 0 && server.revision.toLong() in expected.toLong()..expected.toLong() + 1)
        require(sameValues(applyIntent(acknowledged, head), server))
        // Newer queued edits remain; acknowledging an old head does not reset the user's edit version.
        return copy(serverKnown = true, acknowledged = server, intents = intents.drop(1), rejected = null).checked()
    }

    fun conflict(server: KhatmahPlan?): KhatmahJournal = copy(
        version = version + 1, conflicted = true, serverConflict = server, rejected = null,
    ).checked()

    fun reject(status: Int): KhatmahJournal = copy(version = version + 1, rejected = status).checked()

    fun refreshed(server: KhatmahPlan?): KhatmahJournal {
        checked()
        if (intents.isNotEmpty() || blocked) return this
        if (acknowledged != null && (server == null || server.revision < acknowledged.revision)) return conflict(server)
        val changed = !sameValues(acknowledged, server)
        return copy(serverKnown = true, acknowledged = server, version = version + if (changed) 1 else 0).checked()
    }

    fun useServer(server: KhatmahPlan?, expectedVersion: Long): KhatmahJournal {
        checked(); require(version == expectedVersion && (blocked || intents.isEmpty()))
        return KhatmahJournal(owner, namespace, version + 1, true, server).checked()
    }

    /** A deliberate rebase, optionally repairing fields. Finished-server restart needs separate consent. */
    fun keepDevice(server: KhatmahPlan?, expectedVersion: Long, today: LocalDate, correction: KhatmahDraft? = null, allowRestart: Boolean = false): KhatmahJournal {
        checked(); require(version == expectedVersion && (blocked || (intents.isEmpty() && correction != null)))
        var desired = requireNotNull(projected())
        if (correction != null) {
            val (start, target, pages) = correction.values(today, desired)
            if (desired.status == "finished" && pages != 604) require(allowRestart)
            desired = desired.copy(start = start, target = target, completed = pages, status = if (desired.status == "finished" && pages != 604) "active" else desired.status)
        }
        var result = KhatmahJournal(owner, namespace, version + 1, true, server).checked()
        if (sameValues(desired, server)) return result
        val draft = KhatmahDraft.from(desired, today)
        if (server?.status == "finished") {
            require(allowRestart) { "Explicit restart confirmation required" }
            result = result.append("restart", draft.copy(pages = "0"), result.version, today)
        }
        result = result.append("save", draft, result.version, today)
        val current = requireNotNull(result.projected())
        if (current.status != desired.status) {
            val action = when (desired.status) { "paused" -> "pause"; "finished" -> "finish"; else -> "resume" }
            result = result.append(action, null, result.version, today)
        }
        return result.checked()
    }

    companion object {
        const val MAX_INTENTS = 64
        fun sameValues(a: KhatmahPlan?, b: KhatmahPlan?): Boolean = when {
            a == null || b == null -> a == b
            else -> a.start == b.start && a.target == b.target && a.completed == b.completed && a.status == b.status
        }
        fun applyIntent(base: KhatmahPlan?, intent: KhatmahIntent): KhatmahPlan {
            if (intent.kind in setOf("save", "restart")) {
                val draft = requireNotNull(intent.draft)
                val start = KhatmahContract.date(draft.start); val target = KhatmahContract.date(draft.target)
                require(draft.pages.matches(Regex("[0-9]{1,3}")))
                val pages = draft.pages.toInt()
                require(java.time.temporal.ChronoUnit.DAYS.between(start, target) in 0L..1830L)
                if (intent.kind == "restart") { require(base != null && pages == 0); return KhatmahPlan(start, target, 0, "active", base.revision) }
                val result = KhatmahPlan(start, target, pages, base?.status ?: "active", base?.revision ?: 0)
                require(base?.status != "finished" || sameValues(base, result))
                return result
            }
            requireNotNull(base); require(base.status != "finished")
            val status = when (intent.kind) { "pause" -> "paused"; "resume" -> "active"; "finish" -> { require(base.completed == 604); "finished" }; else -> error("Invalid intent") }
            return base.copy(status = status)
        }
    }
}

/** Strict owner/environment-bound plaintext inside PrivateCipher; no permissive Gson field defaults. */
internal object KhatmahJournalCodec {
    const val KEY = "private:khatmah-journal:v3"
    const val MAX_BYTES = 65536
    private fun plan(value: KhatmahPlan?): JsonElement = value?.let {
        JsonObject().apply {
            addProperty("start_date", it.start.toString()); addProperty("target_date", it.target.toString())
            addProperty("completed_pages", it.completed); addProperty("total_pages", 604)
            addProperty("status", it.status); addProperty("revision", it.revision)
        }
    } ?: JsonNull.INSTANCE
    fun encode(journal: KhatmahJournal): String {
        journal.checked()
        val value = JsonObject().apply {
            addProperty("schema", 3); addProperty("owner", journal.owner); addProperty("namespace", journal.namespace)
            addProperty("version", journal.version); addProperty("server_known", journal.serverKnown)
            add("acknowledged", plan(journal.acknowledged)); addProperty("conflicted", journal.conflicted)
            add("server_conflict", plan(journal.serverConflict))
            add("rejected", journal.rejected?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
            add("intents", JsonArray().apply { journal.intents.forEach { intent -> add(JsonObject().apply {
                addProperty("id", intent.id); addProperty("kind", intent.kind)
                add("expected_revision", intent.expectedRevision?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
                add("draft", intent.draft?.let { draft -> JsonObject().apply {
                    addProperty("start", draft.start); addProperty("target", draft.target); addProperty("pages", draft.pages)
                } } ?: JsonNull.INSTANCE)
            }) } })
        }.toString()
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        return value
    }
    fun decode(raw: String, owner: Long, namespace: String): KhatmahJournal {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = JsonParser.parseString(raw).asJsonObject
        require(root.keySet() == setOf("schema", "owner", "namespace", "version", "server_known", "acknowledged", "conflicted", "server_conflict", "rejected", "intents"))
        fun JsonObject.text(key: String): String = requireNotNull(get(key)).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        fun JsonObject.number(key: String): Long = requireNotNull(get(key)).also {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.toString().matches(Regex("[0-9]{1,19}")))
        }.toString().toLong()
        fun JsonObject.flag(key: String): Boolean = requireNotNull(get(key)).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        fun JsonObject.optionalInt(key: String): Int? = requireNotNull(get(key)).let { if (it.isJsonNull) null else number(key).also { n -> require(n <= Int.MAX_VALUE) }.toInt() }
        require(root.number("schema") == 3L && root.number("owner") == owner && root.text("namespace") == namespace)
        val intents = root.getAsJsonArray("intents"); require(intents.size() <= KhatmahJournal.MAX_INTENTS)
        return KhatmahJournal(
            owner, namespace, root.number("version"), root.flag("server_known"), KhatmahContract.parse(requireNotNull(root["acknowledged"])),
            intents.map { element -> val item = element.asJsonObject; require(item.keySet() == setOf("id", "kind", "draft", "expected_revision")); val draft = requireNotNull(item["draft"])
                if (!draft.isJsonNull) require(draft.asJsonObject.keySet() == setOf("start", "target", "pages"))
                KhatmahIntent(item.text("id"), item.text("kind"), if (draft.isJsonNull) null else draft.asJsonObject.let { KhatmahDraft(it.text("start"), it.text("target"), it.text("pages")) }, item.optionalInt("expected_revision"))
            }, root.flag("conflicted"), KhatmahContract.parse(requireNotNull(root["server_conflict"])), root.optionalInt("rejected"),
        ).checked()
    }
}
