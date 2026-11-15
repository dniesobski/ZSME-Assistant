package com.example.data.network

import com.example.data.model.ApkCandidate
import com.example.data.model.AppUpdateCheckResult
import com.example.data.model.AppVersionHelper
import com.example.data.model.ClassTimetable
import com.example.data.model.ScheduleFilterMatcher
import com.example.data.model.SchoolClass
import com.example.data.model.SubstitutionsSnapshot
import com.example.data.model.TeacherSchoolConfig
import com.example.data.model.TimetableCatalog
import com.example.data.model.TimetableCategory
import com.example.data.model.TimetableTarget
import com.example.data.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class InMemoryCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existing = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(existing) {
            for (cookie in cookies) {
                existing.removeAll { it.name == cookie.name && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) {
                    existing.add(cookie)
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = cookieStore[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return synchronized(list) {
            list.removeAll { it.expiresAt <= now }
            list.filter { it.matches(url) }
        }
    }

    fun clear() {
        cookieStore.clear()
    }
}

class ZsmePortalClient(
    private val cookieJar: InMemoryCookieJar = InMemoryCookieJar()
) {
    private val parsedClassPlansCache = ConcurrentHashMap<String, List<ClassTimetable>>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun invalidateSchoolCache(planUrl: String) {
        val key = cacheKeyForUrl(planUrl)
        if (key.isNotBlank()) {
            parsedClassPlansCache.remove(key)
        }
    }

    private fun cacheKeyForUrl(url: String): String =
        url.trim().trimEnd('/').lowercase()

    private fun ensureDirectoryBaseUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed.contains("?") || trimmed.endsWith("/")) return trimmed
        val lastSeg = trimmed.substringAfterLast('/')
        return if (!lastSeg.contains('.')) "$trimmed/" else trimmed
    }

    internal fun extractSubfolderPrefix(url: String): String {
        return runCatching {
            val rawPath = java.net.URI(ensureDirectoryBaseUrl(url)).path.orEmpty()
            val clean = if (rawPath.endsWith("/")) rawPath else rawPath.substringBeforeLast('/', "") + "/"
            if (clean == "/") "" else clean.lowercase()
        }.getOrDefault("")
    }

    internal fun isWithinSubfolderScope(candidateUrl: String, requiredHost: String, subfolderPrefix: String): Boolean {
        if (subfolderPrefix.isBlank()) return true
        return runCatching {
            val uri = java.net.URI(candidateUrl)
            val candidateHost = uri.host.orEmpty().lowercase().removePrefix("www.")
            val expectedHost = requiredHost.lowercase().removePrefix("www.")
            val sameHost = expectedHost.isBlank() || candidateHost == expectedHost
            val pathLower = uri.path.orEmpty().lowercase()
            val matchesPrefix = pathLower.startsWith(subfolderPrefix) || "$pathLower/" == subfolderPrefix
            sameHost && matchesPrefix
        }.getOrDefault(false)
    }

    suspend fun fetchClassList(
        baseUrl: String = UserPreferences.DEFAULT_PLAN_URL
    ): Result<List<SchoolClass>> = fetchTimetableCatalog(baseUrl).map { it.classes }

    suspend fun fetchTimetableCatalog(
        baseUrl: String = UserPreferences.DEFAULT_PLAN_URL
    ): Result<TimetableCatalog> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = baseUrl.trim().ifBlank { UserPreferences.DEFAULT_PLAN_URL }
            val cacheKey = cacheKeyForUrl(cleanBase)
            val dirInputUrl = ensureDirectoryBaseUrl(cleanBase)
            val inputHost = runCatching { java.net.URI(dirInputUrl).host.orEmpty() }.getOrDefault("")
            val requiredSubfolder = extractSubfolderPrefix(dirInputUrl)

            val mergedClasses = linkedMapOf<String, SchoolClass>()
            val mergedTeachers = linkedMapOf<String, TimetableTarget>()
            val mergedRooms = linkedMapOf<String, TimetableTarget>()

            fun merge(part: TimetableCatalog) {
                part.classes.forEach {
                    if (isWithinSubfolderScope(it.url, inputHost, requiredSubfolder)) {
                        mergedClasses.putIfAbsent(it.url, it)
                    }
                }
                part.teachers.forEach {
                    if (it.url.startsWith(TEACHER_SCAN_URL_PREFIX) ||
                        isWithinSubfolderScope(it.url, inputHost, requiredSubfolder)
                    ) {
                        mergedTeachers.putIfAbsent(it.url, it)
                    }
                }
                part.rooms.forEach {
                    if (isWithinSubfolderScope(it.url, inputHost, requiredSubfolder)) {
                        mergedRooms.putIfAbsent(it.url, it)
                    }
                }
            }

            val visitedUrls = mutableSetOf<String>()

            // Fetch rootPage; if folder root returns 403/404 or redirects outside subfolder, probe subfolder entry files directly
            val rootPage = runCatching { fetchHtml(cleanBase) }.getOrNull()
                ?: (if (dirInputUrl != cleanBase) runCatching { fetchHtml(dirInputUrl) }.getOrNull() else null)
                ?: listOf("index.html", "index.htm", "index.php", "lista.html", "plany/lista.html", "menu.html")
                    .firstNotNullOfOrNull { entry ->
                        runCatching { fetchHtml(ZsmeHtmlParser.resolveUrl(dirInputUrl, entry)) }.getOrNull()
                    }
                ?: fetchHtml(cleanBase)

            visitedUrls.add(rootPage.finalUrl)
            val rootStillInSubfolder = isWithinSubfolderScope(rootPage.finalUrl, inputHost, requiredSubfolder)
            val effectiveRootBase = if (rootStillInSubfolder) {
                ensureDirectoryBaseUrl(rootPage.finalUrl)
            } else {
                // Server redirected root folder to main domain; stay anchored in user's subfolder!
                dirInputUrl
            }

            if (rootStillInSubfolder) {
                merge(ZsmeHtmlParser.parseTimetableCatalog(rootPage.html, effectiveRootBase))
            }

            val extractedFromRoot = if (rootStillInSubfolder) {
                (
                    ZsmeHtmlParser.extractFrameUrls(rootPage.html, effectiveRootBase) +
                        ZsmeHtmlParser.extractCatalogSubpageUrls(rootPage.html, effectiveRootBase)
                    ).filter { isWithinSubfolderScope(it, inputHost, requiredSubfolder) }
            } else {
                emptyList()
            }

            val probeBases = listOf(effectiveRootBase, dirInputUrl).distinct()
            val subfolderProbes = probeBases.flatMap { base ->
                listOf(
                    ZsmeHtmlParser.resolveUrl(base, "index.html"),
                    ZsmeHtmlParser.resolveUrl(base, "lista.html"),
                    ZsmeHtmlParser.resolveUrl(base, "plany/lista.html"),
                    ZsmeHtmlParser.resolveUrl(base, "menu.html"),
                    ZsmeHtmlParser.resolveUrl(base, "spis.html"),
                    ZsmeHtmlParser.resolveUrl(base, "plan.html")
                )
            }

            val primaryFrameAndLinks = (extractedFromRoot + subfolderProbes).distinct()

            val subSemaphore = Semaphore(4)
            suspend fun fetchCatalogBatch(urls: List<String>): List<String> {
                val scopedUrls = urls.filter { isWithinSubfolderScope(it, inputHost, requiredSubfolder) }
                val pending = scopedUrls.filter { visitedUrls.add(it) }
                if (pending.isEmpty()) return emptyList()
                val results = coroutineScope {
                    pending.map { url ->
                        async {
                            subSemaphore.withPermit {
                                runCatching { fetchHtml(url) }.getOrNull()
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                val nextLinks = mutableListOf<String>()
                for (subPage in results) {
                    visitedUrls.add(subPage.finalUrl)
                    if (!isWithinSubfolderScope(subPage.finalUrl, inputHost, requiredSubfolder)) {
                        continue
                    }
                    val subBase = ensureDirectoryBaseUrl(subPage.finalUrl)
                    merge(ZsmeHtmlParser.parseTimetableCatalog(subPage.html, subBase))
                    nextLinks += ZsmeHtmlParser.extractFrameUrls(subPage.html, subBase)
                    nextLinks += ZsmeHtmlParser.extractCatalogSubpageUrls(subPage.html, subBase)
                }
                return nextLinks.distinct()
                    .filter { isWithinSubfolderScope(it, inputHost, requiredSubfolder) }
                    .filter { !visitedUrls.contains(it) }
            }

            val secondHop = fetchCatalogBatch(primaryFrameAndLinks)
            val thirdHop = if (secondHop.isNotEmpty()) {
                fetchCatalogBatch(secondHop.take(20))
            } else {
                emptyList()
            }
            if (mergedClasses.isEmpty() && thirdHop.isNotEmpty()) {
                fetchCatalogBatch(thirdHop.take(20))
            }

            // Probe dedicated Optivum teacher/room/class list pages inside the subfolder if still missing lists
            if (mergedClasses.isEmpty() || mergedTeachers.isEmpty() || mergedRooms.isEmpty()) {
                val extraProbes = probeBases.flatMap { probeBase ->
                    listOf(
                        ZsmeHtmlParser.resolveUrl(probeBase, "nauczyciele.html"),
                        ZsmeHtmlParser.resolveUrl(probeBase, "sale.html"),
                        ZsmeHtmlParser.resolveUrl(probeBase, "oddzialy.html"),
                        ZsmeHtmlParser.resolveUrl(probeBase, "plany/nauczyciele.html"),
                        ZsmeHtmlParser.resolveUrl(probeBase, "plany/sale.html"),
                        ZsmeHtmlParser.resolveUrl(probeBase, "plany/oddzialy.html"),
                        ZsmeHtmlParser.resolveUrl(probeBase, "plany/"),
                        ZsmeHtmlParser.resolveUrl(probeBase, "lista_n.html"),
                        ZsmeHtmlParser.resolveUrl(probeBase, "lista_s.html")
                    )
                }
                fetchCatalogBatch(extraProbes)
            }

            // Scan ALL class timetables to populate room/teacher links and harvest every teacher abbreviation
            // present across all class schedules (even when school omits dedicated n*.html pages).
            if (mergedClasses.isNotEmpty()) {
                val semaphore = Semaphore(10)
                val scannedPages = java.util.Collections.synchronizedList(mutableListOf<Pair<TimetableCatalog, ClassTimetable>>())
                withTimeoutOrNull(45_000L) {
                    coroutineScope {
                        mergedClasses.values.map { cls ->
                            async {
                                semaphore.withPermit {
                                    runCatching { fetchHtml(cls.url) }.getOrNull()?.let { page ->
                                        val partCatalog = ZsmeHtmlParser.parseTimetableCatalog(page.html, page.finalUrl)
                                        val parsedPlan = ZsmeHtmlParser.parseTimetable(
                                            html = page.html,
                                            className = cls.name,
                                            classUrl = page.finalUrl,
                                            category = TimetableCategory.CLASS
                                        )
                                        scannedPages.add(partCatalog to parsedPlan)
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }

                val parsedPlans = scannedPages.map { it.second }
                if (parsedPlans.isNotEmpty()) {
                    parsedClassPlansCache[cacheKey] = parsedPlans
                    if (rootStillInSubfolder) {
                        val finalKey = cacheKeyForUrl(rootPage.finalUrl)
                        if (finalKey.isNotBlank()) parsedClassPlansCache[finalKey] = parsedPlans
                    }
                }
                scannedPages.forEach { (partCatalog, _) -> merge(partCatalog) }

                val scannedAbbrs = ZsmeHtmlParser.extractTeacherAbbreviationsFromTimetables(parsedPlans)
                for (abbr in scannedAbbrs) {
                    val alreadyPresent = mergedTeachers.values.any { existing ->
                        existing.name.equals(abbr, ignoreCase = true) ||
                            ScheduleFilterMatcher.matchesTeacherToken(existing.name, abbr)
                    }
                    if (!alreadyPresent) {
                        val scanUrl = "$TEACHER_SCAN_URL_PREFIX$abbr"
                        mergedTeachers.putIfAbsent(
                            scanUrl,
                            TimetableTarget(
                                id = "scan_${abbr.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
                                name = abbr,
                                url = scanUrl,
                                category = TimetableCategory.TEACHER
                            )
                        )
                    }
                }
            }

            val finalCatalog = TimetableCatalog(
                classes = mergedClasses.values.sortedWith(
                    compareBy(
                        { it.name.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 99 },
                        { it.name }
                    )
                ),
                teachers = mergedTeachers.values.sortedBy { it.name.lowercase() },
                rooms = mergedRooms.values.sortedWith(
                    compareBy(
                        { it.name.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                        { it.name.lowercase() }
                    )
                )
            )

            if (finalCatalog.classes.isEmpty() && finalCatalog.teachers.isEmpty() && finalCatalog.rooms.isEmpty()) {
                error("Nie znaleziono listy planów (klas, nauczycieli ani sal) pod adresem $cleanBase")
            }
            finalCatalog
        }
    }

    suspend fun fetchTimetable(schoolClass: SchoolClass): Result<ClassTimetable> =
        fetchTargetTimetable(
            TimetableTarget(
                id = schoolClass.id,
                name = schoolClass.name,
                url = schoolClass.url,
                category = TimetableCategory.CLASS
            )
        )

    suspend fun fetchTargetTimetable(
        target: TimetableTarget,
        baseUrl: String = UserPreferences.DEFAULT_PLAN_URL,
        knownCatalog: TimetableCatalog = TimetableCatalog(),
        forceRescan: Boolean = false
    ): Result<ClassTimetable> = withContext(Dispatchers.IO) {
        runCatching {
            if (target.category == TimetableCategory.TEACHER) {
                val abbr = if (target.url.startsWith(TEACHER_SCAN_URL_PREFIX)) {
                    target.url.removePrefix(TEACHER_SCAN_URL_PREFIX).ifBlank { target.name }
                } else {
                    target.name
                }

                // If target has a dedicated HTTP page (n*.html) and isn't forced to scan all plans, try it first
                if (!target.url.startsWith(TEACHER_SCAN_URL_PREFIX) && target.url.startsWith("http", ignoreCase = true)) {
                    val directParsed = runCatching {
                        val page = fetchHtml(target.url)
                        ZsmeHtmlParser.parseTimetable(
                            html = page.html,
                            className = target.name,
                            classUrl = page.finalUrl,
                            category = TimetableCategory.TEACHER
                        )
                    }.getOrNull()
                    if (directParsed != null && directParsed.days.any { it.lessons.isNotEmpty() }) {
                        return@runCatching directParsed
                    }
                }

                return@runCatching fetchTeacherScheduleAcrossAllPlans(
                    teacherAbbr = abbr,
                    knownCatalog = knownCatalog,
                    baseUrl = baseUrl,
                    forceRescan = forceRescan
                ).getOrThrow()
            }

            val page = fetchHtml(target.url)
            val parsed = ZsmeHtmlParser.parseTimetable(
                html = page.html,
                className = target.name,
                classUrl = page.finalUrl,
                category = target.category
            )
            if (parsed.days.all { it.lessons.isEmpty() } && !ZsmeHtmlParser.hasTimetableStructure(page.html)) {
                error("Plan dla „${target.name}” ma nierozpoznany format pod adresem ${page.finalUrl}.")
            }
            parsed
        }
    }

    suspend fun fetchTeacherScheduleAcrossAllPlans(
        teacherAbbr: String,
        knownCatalog: TimetableCatalog,
        baseUrl: String = UserPreferences.DEFAULT_PLAN_URL,
        forceRescan: Boolean = false
    ): Result<ClassTimetable> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanAbbr = teacherAbbr.trim()
            if (cleanAbbr.isBlank()) error("Wpisz swój skrót nauczyciela w Ustawieniach.")
            val cleanBase = baseUrl.trim().ifBlank { UserPreferences.DEFAULT_PLAN_URL }
            val cacheKey = cacheKeyForUrl(cleanBase)

            // Reuse recently scanned class timetables unless a fresh rescan was requested
            if (!forceRescan) {
                val cachedPlans = parsedClassPlansCache[cacheKey]
                if (!cachedPlans.isNullOrEmpty()) {
                    return@runCatching ZsmeHtmlParser.aggregateTeacherScheduleFromTimetables(
                        teacherAbbr = cleanAbbr,
                        sourceTimetables = cachedPlans,
                        sourceUrl = cleanBase
                    )
                }
            } else {
                parsedClassPlansCache.remove(cacheKey)
            }

            var catalogFetchedFreshly = false
            val catalog = if (forceRescan || (knownCatalog.classes.isEmpty() && knownCatalog.teachers.isEmpty() && knownCatalog.rooms.isEmpty())) {
                catalogFetchedFreshly = true
                fetchTimetableCatalog(cleanBase).getOrElse { knownCatalog }
            } else {
                knownCatalog
            }

            // If fetchTimetableCatalog freshly populated the class plans cache, aggregate immediately without double scanning
            val refreshedCache = parsedClassPlansCache[cacheKey]
            if (catalogFetchedFreshly && !refreshedCache.isNullOrEmpty()) {
                val fromClasses = ZsmeHtmlParser.aggregateTeacherScheduleFromTimetables(
                    teacherAbbr = cleanAbbr,
                    sourceTimetables = refreshedCache,
                    sourceUrl = cleanBase
                )
                if (fromClasses.days.any { it.lessons.isNotEmpty() }) {
                    return@runCatching fromClasses
                }
            }

            // Try dedicated HTTP teacher page (n*.html) if present in catalog
            val directTarget = catalog.teachers.firstOrNull { target ->
                target.url.startsWith("http", ignoreCase = true) &&
                    (target.name.equals(cleanAbbr, ignoreCase = true) ||
                        ScheduleFilterMatcher.matchesTeacherToken(target.name, cleanAbbr))
            }
            if (directTarget != null) {
                val directParsed = runCatching {
                    val page = fetchHtml(directTarget.url)
                    ZsmeHtmlParser.parseTimetable(
                        html = page.html,
                        className = directTarget.name,
                        classUrl = page.finalUrl,
                        category = TimetableCategory.TEACHER
                    )
                }.getOrNull()
                if (directParsed != null && directParsed.days.any { it.lessons.isNotEmpty() }) {
                    return@runCatching directParsed
                }
            }

            if (!refreshedCache.isNullOrEmpty()) {
                return@runCatching ZsmeHtmlParser.aggregateTeacherScheduleFromTimetables(
                    teacherAbbr = cleanAbbr,
                    sourceTimetables = refreshedCache,
                    sourceUrl = cleanBase
                )
            }

            // Otherwise scan ALL class (or fallback room) timetables to extract lessons containing teacherAbbr
            val targetsToScan = if (catalog.classes.isNotEmpty()) {
                catalog.classes.map {
                    TimetableTarget(id = it.id, name = it.name, url = it.url, category = TimetableCategory.CLASS)
                }
            } else {
                catalog.rooms
            }

            if (targetsToScan.isEmpty()) {
                // Final check: maybe cleanBase itself is a direct timetable page (e.g. teacher.php or custom HTML)
                val directPage = runCatching { fetchHtml(cleanBase) }.getOrNull()
                if (directPage != null && ZsmeHtmlParser.hasTimetableStructure(directPage.html)) {
                    val parsedDirect = ZsmeHtmlParser.parseTimetable(
                        html = directPage.html,
                        className = cleanAbbr,
                        classUrl = directPage.finalUrl,
                        category = TimetableCategory.TEACHER
                    )
                    if (parsedDirect.days.any { it.lessons.isNotEmpty() }) {
                        return@runCatching parsedDirect
                    }
                }
                error("Brak planów do przeszukania pod adresem $cleanBase")
            }

            val semaphore = Semaphore(10)
            val parsedPlans = coroutineScope {
                targetsToScan.map { target ->
                    async {
                        semaphore.withPermit {
                            runCatching {
                                val page = fetchHtml(target.url)
                                ZsmeHtmlParser.parseTimetable(
                                    html = page.html,
                                    className = target.name,
                                    classUrl = page.finalUrl,
                                    category = target.category
                                )
                            }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (catalog.classes.isNotEmpty() && parsedPlans.isNotEmpty()) {
                parsedClassPlansCache[cacheKey] = parsedPlans
            }

            ZsmeHtmlParser.aggregateTeacherScheduleFromTimetables(
                teacherAbbr = cleanAbbr,
                sourceTimetables = parsedPlans,
                sourceUrl = cleanBase
            )
        }
    }

    suspend fun fetchMultiSchoolTeacherSchedule(
        schools: List<TeacherSchoolConfig>,
        knownPrimaryCatalog: TimetableCatalog,
        primaryBaseUrl: String,
        forceRescan: Boolean = false
    ): Result<ClassTimetable> = withContext(Dispatchers.IO) {
        runCatching {
            if (schools.isEmpty()) {
                error("Wpisz swój skrót nauczyciela w Ustawieniach.")
            }

            val normalizedPrimary = cacheKeyForUrl(primaryBaseUrl)
            val fetchedPairs = mutableListOf<Pair<TeacherSchoolConfig, ClassTimetable>>()
            val failedDetails = mutableListOf<String>()
            var lastError: Throwable? = null

            val results = coroutineScope {
                schools.map { school ->
                    async {
                        val isPrimary = school.id == UserPreferences.PRIMARY_SCHOOL_ID ||
                            cacheKeyForUrl(school.planUrl) == normalizedPrimary
                        val res = fetchSingleSchoolTeacherPlan(
                            school = school,
                            knownCatalog = if (isPrimary) knownPrimaryCatalog else TimetableCatalog(),
                            forceRescan = forceRescan
                        )
                        school to res
                    }
                }.awaitAll()
            }

            for ((school, res) in results) {
                res.onSuccess { tt ->
                    fetchedPairs += (school to tt)
                }.onFailure { err ->
                    lastError = err
                    val reason = err.message?.take(60).orEmpty()
                    failedDetails += if (reason.isNotBlank()) "${school.schoolName} ($reason)" else school.schoolName
                }
            }

            if (fetchedPairs.isEmpty()) {
                throw (lastError ?: IllegalStateException("Nie udało się pobrać planu z żadnej placówki."))
            }

            ZsmeHtmlParser.mergeMultiSchoolTeacherTimetables(
                schoolSchedules = fetchedPairs,
                failedSchools = failedDetails
            )
        }
    }

    private suspend fun fetchSingleSchoolTeacherPlan(
        school: TeacherSchoolConfig,
        knownCatalog: TimetableCatalog,
        forceRescan: Boolean
    ): Result<ClassTimetable> {
        val cleanUrl = school.planUrl.trim()
        val pathOnly = cleanUrl.substringBefore('?')
        val looksLikeDirectTeacherPage =
            Regex("""/n\d+[^/]*\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(pathOnly) ||
                pathOnly.contains("/nauczyciel", ignoreCase = true)

        if (looksLikeDirectTeacherPage) {
            val direct = runCatching {
                val page = fetchHtml(cleanUrl)
                val parsed = ZsmeHtmlParser.parseTimetable(
                    html = page.html,
                    className = school.teacherAbbreviation,
                    classUrl = page.finalUrl,
                    category = TimetableCategory.TEACHER
                )
                if (parsed.days.all { it.lessons.isEmpty() } && !ZsmeHtmlParser.hasTimetableStructure(page.html)) {
                    error("Strona $cleanUrl nie zawiera rozpoznawalnej tabeli planu")
                }
                parsed
            }
            if (direct.isSuccess) {
                return direct
            }
        }
        return fetchTeacherScheduleAcrossAllPlans(
            teacherAbbr = school.teacherAbbreviation,
            knownCatalog = knownCatalog,
            baseUrl = cleanUrl,
            forceRescan = forceRescan
        )
    }

    suspend fun fetchSubstitutions(
        accessKey: String = UserPreferences.DEFAULT_SECRETARIAT_KEY,
        baseUrl: String = UserPreferences.DEFAULT_SUBSTITUTIONS_URL
    ): Result<SubstitutionsSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = baseUrl.trim().ifBlank { UserPreferences.DEFAULT_SUBSTITUTIONS_URL }
            var page = fetchHtmlWithOptionalBasicAuth(targetUrl, accessKey)

            // If a login form is present (e.g., user configured root sekretariat.zsme.pl), submit accessKey
            val authForm = ZsmeHtmlParser.detectAuthForm(page.html, page.finalUrl, accessKey)
            if (authForm != null) {
                page = submitForm(authForm)
            }

            var snapshot = ZsmeHtmlParser.parseSubstitutions(page.html, page.finalUrl)

            // Follow iframe / meta-refresh if main page holds no substitution items yet
            if (snapshot.items.isEmpty()) {
                val frameOrRefreshUrls = ZsmeHtmlParser.extractFrameUrls(page.html, page.finalUrl)
                for (subUrl in frameOrRefreshUrls) {
                    val subPage = runCatching { fetchHtmlWithOptionalBasicAuth(subUrl, accessKey) }.getOrNull() ?: continue
                    val parsed = ZsmeHtmlParser.parseSubstitutions(subPage.html, subPage.finalUrl)
                    if (parsed.items.isNotEmpty() || parsed.announcements.isNotEmpty()) {
                        snapshot = parsed
                        break
                    }
                }
            }

            // Follow AJAX data URLs if page renders substitutions dynamically via fetch/jQuery
            if (snapshot.items.isEmpty()) {
                val ajaxUrls = ZsmeHtmlParser.extractAjaxDataUrls(page.html, page.finalUrl)
                for (ajaxUrl in ajaxUrls) {
                    val ajaxPage = runCatching { fetchHtmlWithOptionalBasicAuth(ajaxUrl, accessKey) }.getOrNull() ?: continue
                    val parsed = ZsmeHtmlParser.parseSubstitutions(ajaxPage.html, ajaxPage.finalUrl)
                    if (parsed.items.isNotEmpty() || parsed.announcements.isNotEmpty()) {
                        snapshot = parsed
                        break
                    }
                }
            }

            // If user opened root landing page and there is a link to tv.php / zastepstwa, follow it
            if (snapshot.items.isEmpty() && !targetUrl.contains("tv.php", ignoreCase = true)) {
                val subpageUrl = ZsmeHtmlParser.findSubstitutionSubpageUrl(page.html, page.finalUrl)
                if (subpageUrl != null) {
                    val subPage = runCatching { fetchHtmlWithOptionalBasicAuth(subpageUrl, accessKey) }.getOrNull()
                    if (subPage != null) {
                        val subAuthForm = ZsmeHtmlParser.detectAuthForm(subPage.html, subPage.finalUrl, accessKey)
                        val resolvedPage = if (subAuthForm != null) submitForm(subAuthForm) else subPage
                        val subSnapshot = ZsmeHtmlParser.parseSubstitutions(resolvedPage.html, resolvedPage.finalUrl)
                        if (subSnapshot.items.isNotEmpty() || subSnapshot.announcements.isNotEmpty()) {
                            snapshot = subSnapshot
                        }
                    }
                }
            }

            snapshot
        }
    }

    suspend fun checkAppUpdateInRepo(
        repoUrl: String = UserPreferences.DEFAULT_APP_UPDATE_URL,
        currentVersionName: String,
        installedLastUpdateMillis: Long,
        prefs: UserPreferences
    ): Result<AppUpdateCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRepo = repoUrl.trim().ifBlank { UserPreferences.DEFAULT_APP_UPDATE_URL }
            val probeReq = Request.Builder()
                .url(cleanRepo)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
                .get()
                .build()

            var selectedCandidate: ApkCandidate? = null
            client.newCall(probeReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Repozytorium zwróciło kod HTTP ${resp.code}")
                }
                val finalUrl = resp.request.url.toString()
                val contentType = resp.header("Content-Type").orEmpty()
                if (contentType.contains("android.package-archive", ignoreCase = true) ||
                    finalUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)
                ) {
                    val fileName = finalUrl.substringBefore('?').substringAfterLast('/').ifBlank { "zsme-update.apk" }
                    val lastMod = resp.header("Last-Modified").orEmpty()
                    selectedCandidate = ApkCandidate(
                        fileName = fileName,
                        downloadUrl = finalUrl,
                        versionName = AppVersionHelper.extractVersionFromText(fileName),
                        lastModifiedHeader = lastMod,
                        lastModifiedMillis = parseHttpDateMillis(lastMod),
                        eTag = resp.header("ETag").orEmpty(),
                        contentLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                    )
                } else {
                    val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
                    val html = decodeHtmlBytes(bodyBytes, resp.body?.contentType()?.charset(null))
                    val baseForLinks = if (finalUrl.endsWith("/")) finalUrl else "$finalUrl/"
                    val list = ZsmeHtmlParser.parseAppRepositoryApks(html, baseForLinks)
                    selectedCandidate = list.firstOrNull()
                }
            }

            val baseCandidate = selectedCandidate
                ?: return@runCatching AppUpdateCheckResult(
                    candidate = null,
                    updateAvailable = false,
                    reason = "Nie znaleziono pliku APK pod adresem $cleanRepo",
                    currentVersionName = currentVersionName
                )

            val enriched = fetchApkHeaders(baseCandidate)
            val remoteLastMod = enriched.lastModifiedHeader.ifBlank { enriched.directoryDateText }

            val higherSemanticVersion = enriched.versionName.isNotBlank() &&
                AppVersionHelper.compareVersions(enriched.versionName, currentVersionName) > 0
            val changedVersionVsStored = prefs.lastApkUrl.isNotBlank() &&
                enriched.versionName.isNotBlank() &&
                enriched.versionName != prefs.lastApkVersion
            val changedDateVsStored = prefs.lastApkUrl.isNotBlank() &&
                remoteLastMod.isNotBlank() &&
                remoteLastMod != prefs.lastApkLastModified
            val changedFileOrUrl = prefs.lastApkUrl.isNotBlank() &&
                enriched.downloadUrl != prefs.lastApkUrl
            val changedETagOrSize = prefs.lastApkUrl.isNotBlank() && (
                (enriched.eTag.isNotBlank() && prefs.lastApkETag.isNotBlank() && enriched.eTag != prefs.lastApkETag) ||
                    (enriched.contentLength > 0L && prefs.lastApkContentLength > 0L && enriched.contentLength != prefs.lastApkContentLength)
                )
            val firstCheckNewerBuild = prefs.lastApkUrl.isBlank() &&
                enriched.lastModifiedMillis > 0L &&
                enriched.lastModifiedMillis > installedLastUpdateMillis

            val updateAvailable = higherSemanticVersion ||
                changedVersionVsStored ||
                changedDateVsStored ||
                changedFileOrUrl ||
                changedETagOrSize ||
                firstCheckNewerBuild

            val reason = when {
                higherSemanticVersion ->
                    "Dostępna nowa wersja ${enriched.versionName} (obecna: $currentVersionName)."
                changedVersionVsStored ->
                    "Zmieniła się wersja pliku APK w repozytorium (${enriched.versionName})."
                changedDateVsStored || firstCheckNewerBuild ->
                    "Wykryto nowszą datę modyfikacji pliku APK (${remoteLastMod.ifBlank { "nowy plik" }})."
                changedFileOrUrl || changedETagOrSize ->
                    "Wykryto nowy plik APK na serwerze (${enriched.fileName})."
                else ->
                    "Masz najnowszą wersję aplikacji (${enriched.versionName.ifBlank { currentVersionName }})."
            }

            AppUpdateCheckResult(
                candidate = enriched,
                updateAvailable = updateAvailable,
                reason = reason,
                currentVersionName = currentVersionName
            )
        }
    }

    suspend fun downloadApkToCache(
        cacheDir: File,
        candidate: ApkCandidate,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(cacheDir, "updates").apply { mkdirs() }
            val safeName = candidate.fileName
                .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
                .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
            val outFile = File(updatesDir, safeName)

            val req = Request.Builder()
                .url(candidate.downloadUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Błąd pobierania APK: HTTP ${resp.code}")
                }
                val body = resp.body ?: error("Pusta odpowiedź serwera podczas pobierania APK")
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes > 0L) {
                                val pct = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            }
            onProgress(100)
            outFile
        }
    }

    private fun fetchApkHeaders(candidate: ApkCandidate): ApkCandidate {
        val headReq = Request.Builder()
            .url(candidate.downloadUrl)
            .header("User-Agent", USER_AGENT)
            .head()
            .build()

        val headResult = runCatching {
            client.newCall(headReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val lastMod = resp.header("Last-Modified").orEmpty()
                val eTag = resp.header("ETag").orEmpty()
                val len = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                candidate.copy(
                    lastModifiedHeader = lastMod.ifBlank { candidate.lastModifiedHeader },
                    lastModifiedMillis = parseHttpDateMillis(lastMod).takeIf { it > 0L }
                        ?: parseDirectoryDateMillis(candidate.directoryDateText),
                    eTag = eTag,
                    contentLength = len
                )
            }
        }.getOrNull()

        if (headResult != null) return headResult

        // Fallback when server rejects HEAD (405 Method Not Allowed): read headers from aborted GET
        return runCatching {
            val getReq = Request.Builder()
                .url(candidate.downloadUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(getReq).execute().use { resp ->
                val lastMod = resp.header("Last-Modified").orEmpty()
                val eTag = resp.header("ETag").orEmpty()
                val len = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                candidate.copy(
                    lastModifiedHeader = lastMod.ifBlank { candidate.lastModifiedHeader },
                    lastModifiedMillis = parseHttpDateMillis(lastMod).takeIf { it > 0L }
                        ?: parseDirectoryDateMillis(candidate.directoryDateText),
                    eTag = eTag,
                    contentLength = len
                )
            }
        }.getOrElse {
            candidate.copy(
                lastModifiedMillis = parseDirectoryDateMillis(candidate.directoryDateText)
            )
        }
    }

    private fun parseHttpDateMillis(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }.parse(raw.trim())?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return parseDirectoryDateMillis(raw)
    }

    private fun parseDirectoryDateMillis(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd-MMM-yyyy HH:mm",
            "dd.MM.yyyy HH:mm",
            "dd.MM.yyyy"
        )
        for (pattern in patterns) {
            for (loc in listOf(Locale.US, Locale("pl", "PL"))) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, loc).parse(raw.trim())?.time
                }.getOrNull()
                if (parsed != null) return parsed
            }
        }
        return 0L
    }

    private data class FetchedPage(val finalUrl: String, val html: String)

    private fun fetchHtmlWithOptionalBasicAuth(url: String, accessKey: String): FetchedPage {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 && accessKey.isNotBlank()) {
                val authCandidates = listOf(
                    Credentials.basic("uczen", accessKey),
                    Credentials.basic("zsme", accessKey),
                    Credentials.basic("", accessKey)
                )
                for (cred in authCandidates) {
                    val authReq = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Authorization", cred)
                        .get()
                        .build()
                    client.newCall(authReq).execute().use { authResp ->
                        if (authResp.isSuccessful) {
                            return readResponse(authResp)
                        }
                    }
                }
            }
            if (!response.isSuccessful) {
                error("Serwer zwrócił kod HTTP ${response.code}")
            }
            return readResponse(response)
        }
    }

    private fun fetchHtml(url: String): FetchedPage {
        return try {
            executeHtmlGet(url)
        } catch (err: Exception) {
            if (url.startsWith("https://", ignoreCase = true)) {
                val httpFallback = "http://" + url.substring(8)
                runCatching { executeHtmlGet(httpFallback) }.getOrElse { throw err }
            } else {
                throw err
            }
        }
    }

    private fun executeHtmlGet(url: String): FetchedPage {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Błąd pobierania ($url): HTTP ${response.code}")
            }
            return readResponse(response)
        }
    }

    private fun submitForm(payload: AuthFormPayload): FetchedPage {
        val request = if (payload.method == "GET") {
            val urlBuilder = payload.actionUrl.toHttpUrlOrNullOrThrow().newBuilder()
            payload.fields.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "pl-PL,pl;q=0.9")
                .get()
                .build()
        } else {
            val formBuilder = FormBody.Builder(Charsets.UTF_8)
            payload.fields.forEach { (k, v) -> formBuilder.add(k, v) }
            Request.Builder()
                .url(payload.actionUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "pl-PL,pl;q=0.9")
                .header("Referer", payload.actionUrl)
                .post(formBuilder.build())
                .build()
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Błąd logowania do sekretariatu: HTTP ${response.code}")
            }
            return readResponse(response)
        }
    }

    private fun readResponse(response: Response): FetchedPage {
        val bodyBytes = response.body?.bytes() ?: ByteArray(0)
        val contentTypeCharset = response.body?.contentType()?.charset(null)
        val html = decodeHtmlBytes(bodyBytes, contentTypeCharset)
        return FetchedPage(
            finalUrl = response.request.url.toString(),
            html = html
        )
    }

    private fun decodeHtmlBytes(bytes: ByteArray, headerCharset: Charset?): String {
        if (headerCharset != null) return String(bytes, headerCharset)
        val asciiPeek = String(bytes.take(4096).toByteArray(), Charsets.ISO_8859_1)
        val detected = when {
            asciiPeek.contains("iso-8859-2", ignoreCase = true) -> Charset.forName("ISO-8859-2")
            asciiPeek.contains("windows-1250", ignoreCase = true) -> Charset.forName("windows-1250")
            else -> Charsets.UTF_8
        }
        return String(bytes, detected)
    }

    private fun String.toHttpUrlOrNullOrThrow(): HttpUrl {
        return okhttp3.HttpUrl.Builder().apply {
            val parsed = java.net.URI(this@toHttpUrlOrNullOrThrow)
            scheme(parsed.scheme ?: "https")
            host(parsed.host ?: "sekretariat.zsme.pl")
            if (parsed.port > 0) port(parsed.port)
            if (!parsed.rawPath.isNullOrBlank()) encodedPath(parsed.rawPath)
        }.build()
    }

    companion object {
        const val TEACHER_SCAN_URL_PREFIX = "zsme-teacher-scan://"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
