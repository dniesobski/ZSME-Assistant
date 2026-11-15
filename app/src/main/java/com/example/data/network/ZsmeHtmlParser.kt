package com.example.data.network

import com.example.data.model.ApkCandidate
import com.example.data.model.AppVersionHelper
import com.example.data.model.ClassTimetable
import com.example.data.model.DaySchedule
import com.example.data.model.LessonEntry
import com.example.data.model.ScheduleFilterMatcher
import com.example.data.model.SchoolClass
import com.example.data.model.SubstitutionItem
import com.example.data.model.SubstitutionsSnapshot
import com.example.data.model.TeacherSchoolConfig
import com.example.data.model.TimetableCatalog
import com.example.data.model.TimetableCategory
import com.example.data.model.TimetableTarget
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import java.security.MessageDigest

data class AuthFormPayload(
    val actionUrl: String,
    val method: String,
    val fields: Map<String, String>
)

object ZsmeHtmlParser {

    private val defaultDays = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek")
    private val classTokenRegex = Regex("""\b([1-6]\s?[A-Za-zĄĆĘŁŃÓŚŹŻąćęłńóśźż]{1,5}\d?)\b""")
    private val dateRegex = Regex("""\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b""")

    fun extractFrameUrls(html: String, baseUrl: String): List<String> {
        val doc = Jsoup.parse(html, baseUrl)
        val urls = mutableListOf<String>()
        doc.select("frame[src], iframe[src]").forEach { el ->
            val rawSrc = el.attr("src").trim()
            val abs = if (rawSrc.isNotBlank()) {
                resolveUrl(baseUrl, rawSrc).ifBlank { el.absUrl("src") }
            } else {
                el.absUrl("src")
            }
            if (abs.isNotBlank()) urls.add(abs)
        }
        doc.select("meta[http-equiv~=refresh]").forEach { meta ->
            val content = meta.attr("content")
            val match = Regex("""url\s*=\s*['"]?([^'";\s]+)""", RegexOption.IGNORE_CASE).find(content)
            val rawUrl = match?.groupValues?.get(1).orEmpty()
            if (rawUrl.isNotBlank()) {
                urls.add(resolveUrl(baseUrl, rawUrl))
            }
        }
        val jsRedirectRegex = Regex(
            """(?:window\.)?location(?:\.href)?\s*=\s*['"]([^'"]+\.(?:html?|php)(?:\?[^'"]*)?)['"]|location\.replace\s*\(\s*['"]([^'"]+)['"]\s*\)""",
            RegexOption.IGNORE_CASE
        )
        jsRedirectRegex.findAll(html).forEach { match ->
            val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty().trim()
            if (raw.isNotBlank() && !raw.startsWith("javascript:", ignoreCase = true)) {
                urls.add(resolveUrl(baseUrl, raw))
            }
        }
        return urls.distinct()
    }

    fun extractAjaxDataUrls(html: String, baseUrl: String): List<String> {
        val urls = mutableListOf<String>()
        val patterns = listOf(
            Regex("""fetch\s*\(\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""\$\.(?:get|post|getJSON)\s*\(\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""url\s*:\s*['"]([^'"]+(?:php|json|html)[^'"]*)['"]""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.findAll(html).forEach { match ->
                val raw = match.groupValues[1].trim()
                if (raw.isNotBlank() && !raw.startsWith("http", ignoreCase = true) || raw.contains("zsme.pl")) {
                    val resolved = resolveUrl(baseUrl, raw)
                    if (resolved.isNotBlank() && resolved != baseUrl) {
                        urls.add(resolved)
                    }
                }
            }
        }
        return urls.distinct()
    }

    fun parseClassList(html: String, baseUrl: String): List<SchoolClass> {
        return parseTimetableCatalog(html, baseUrl).classes
    }

    fun parseTimetableCatalog(html: String, baseUrl: String): TimetableCatalog {
        val doc = Jsoup.parse(html, baseUrl)
        val classes = linkedMapOf<String, SchoolClass>()
        val teachers = linkedMapOf<String, TimetableTarget>()
        val rooms = linkedMapOf<String, TimetableTarget>()

        val ignoredNavFiles = setOf(
            "index.html", "index.htm", "index.php",
            "lista.html", "lista.htm", "menu.html", "menu.htm",
            "szablon.html", "pomoc.html", "help.html", "o_programie.html", "logo.html"
        )
        val classDisplayTextRegex = Regex("""^[1-8]\s?[A-Za-zĄĆĘŁŃÓŚŹŻ]{1,5}\d?(?:[-_][A-Za-z0-9]+)?(?:\s*\(.*\))?$""")

        fun inferCategoryFromContext(
            rawHref: String,
            rawText: String,
            cssClass: String,
            contextHint: String
        ): TimetableCategory? {
            val cleanPath = rawHref.substringBefore('?').substringBefore('#').trim().lowercase()
            val fullLower = rawHref.trim().lowercase()
            val clsLower = cssClass.lowercase()
            val fileName = cleanPath.substringAfterLast('/')
            if (fileName in ignoredNavFiles) return null

            if (clsLower.split(Regex("""\s+""")).any { it == "o" } ||
                Regex("""(^|.*/)o\d+[^/]*\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(cleanPath) ||
                Regex("""(^|.*/)o\d+$""", RegexOption.IGNORE_CASE).containsMatchIn(cleanPath) ||
                cleanPath.contains("plany/o") ||
                cleanPath.contains("oddzialy/") ||
                cleanPath.contains("klasy/") ||
                Regex("""(?:^|[?&])(oddzial|klasa|kl|class|o|id_klasy)=""", RegexOption.IGNORE_CASE).containsMatchIn(fullLower)
            ) {
                return TimetableCategory.CLASS
            }
            if (clsLower.split(Regex("""\s+""")).any { it == "n" } ||
                Regex("""(^|.*/)n\d+[^/]*\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(cleanPath) ||
                Regex("""(^|.*/)n\d+$""", RegexOption.IGNORE_CASE).containsMatchIn(cleanPath) ||
                cleanPath.contains("plany/n") ||
                cleanPath.contains("nauczyciele/") ||
                Regex("""(?:^|[?&])(nauczyciel|teacher|n|id_n)=""", RegexOption.IGNORE_CASE).containsMatchIn(fullLower)
            ) {
                return TimetableCategory.TEACHER
            }
            if (clsLower.split(Regex("""\s+""")).any { it == "s" } ||
                Regex("""(^|.*/)s\d+[^/]*\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(cleanPath) ||
                Regex("""(^|.*/)s\d+$""", RegexOption.IGNORE_CASE).containsMatchIn(cleanPath) ||
                cleanPath.contains("plany/s") ||
                cleanPath.contains("sale/") ||
                Regex("""(?:^|[?&])(sala|room|s|id_s)=""", RegexOption.IGNORE_CASE).containsMatchIn(fullLower)
            ) {
                return TimetableCategory.ROOM
            }

            // Context-aware fallback for bottom-left sections (e.g. Sale / Nauczyciele / Oddziały)
            // even when filenames don't follow standard s\d+.html naming
            val looksLikePlanPage = fileName.endsWith(".html") || fileName.endsWith(".htm") ||
                fileName.endsWith(".php") || fullLower.contains("?")
            if (looksLikePlanPage) {
                when {
                    contextHint.contains("sal") || contextHint.contains("pracown") ||
                        contextHint.contains("pomieszcz") || contextHint.contains("room") ||
                        fileName.startsWith("s_") || fileName.startsWith("s-") || fileName.startsWith("room") ->
                        return TimetableCategory.ROOM
                    contextHint.contains("nauczyciel") || contextHint.contains("teacher") ||
                        fileName.startsWith("n_") || fileName.startsWith("n-") || fileName.startsWith("teacher") ->
                        return TimetableCategory.TEACHER
                    contextHint.contains("oddz") || contextHint.contains("klas") ||
                        fileName.startsWith("o_") || fileName.startsWith("o-") || fileName.startsWith("klasa") || fileName.startsWith("class") ->
                        return TimetableCategory.CLASS
                }

                // If link sits inside a timetable folder or looks like a class code (e.g. 1A, 2TI, 3TP)
                val cleanText = rawText.trim()
                val baseLower = baseUrl.lowercase()
                val inPlanDirectory = cleanPath.contains("plany/") || baseLower.contains("lista") || baseLower.contains("menu") || baseLower.contains("oddz")
                if (inPlanDirectory &&
                    cleanText.length in 2..16 &&
                    classDisplayTextRegex.matches(cleanText) &&
                    !contextHint.contains("sal") &&
                    !contextHint.contains("nauczyc")
                ) {
                    return TimetableCategory.CLASS
                }
            }
            return null
        }

        fun resolveOptionUrl(rawValue: String, selectEl: Element, selectContext: String): String {
            val trimmed = rawValue.trim()
            if (trimmed.isBlank()) return ""
            val onchange = selectEl.attr("onchange")
            val hasHtmlExt = trimmed.contains(".htm", ignoreCase = true) || trimmed.contains(".php", ignoreCase = true) || trimmed.contains("?")
            val normalized = when {
                !hasHtmlExt && Regex("""^[ons]\d+$""", RegexOption.IGNORE_CASE).matches(trimmed) -> {
                    if (onchange.contains("plany/", ignoreCase = true) || !baseUrl.contains("/plany/", ignoreCase = true)) {
                        "plany/$trimmed.html"
                    } else {
                        "$trimmed.html"
                    }
                }
                !hasHtmlExt && Regex("""^\d+$""").matches(trimmed) -> {
                    val prefix = when {
                        onchange.contains("'o'", ignoreCase = true) || onchange.contains("/o", ignoreCase = true) ||
                            selectContext.contains("oddz") || selectContext.contains("klas") -> "o"
                        onchange.contains("'n'", ignoreCase = true) || onchange.contains("/n", ignoreCase = true) ||
                            selectContext.contains("nauczyc") -> "n"
                        onchange.contains("'s'", ignoreCase = true) || onchange.contains("/s", ignoreCase = true) ||
                            selectContext.contains("sal") -> "s"
                        else -> ""
                    }
                    if (prefix.isNotBlank()) {
                        val file = "$prefix$trimmed.html"
                        if (onchange.contains("plany/", ignoreCase = true) || !baseUrl.contains("/plany/", ignoreCase = true)) {
                            "plany/$file"
                        } else {
                            file
                        }
                    } else {
                        trimmed
                    }
                }
                else -> trimmed
            }
            return resolveUrl(baseUrl, normalized)
        }

        fun registerEntry(
            rawHref: String,
            rawText: String,
            absUrl: String,
            cssClass: String = "",
            contextHint: String = ""
        ) {
            val text = rawText.trim()
            if (text.isBlank() || absUrl.isBlank()) return
            if (rawHref.startsWith("javascript:", ignoreCase = true) || rawHref.startsWith("#")) return
            val category = inferCategoryFromContext(rawHref, text, cssClass, contextHint) ?: return
            val id = rawHref.substringBefore('?').substringBefore('#')
                .substringAfterLast('/').substringBeforeLast('.').ifBlank { text.lowercase() }

            when (category) {
                TimetableCategory.CLASS -> {
                    if (classes.values.none { it.url == absUrl }) {
                        classes[absUrl] = SchoolClass(id = id, name = text, url = absUrl)
                    }
                }
                TimetableCategory.TEACHER -> {
                    val cleanName = text.replace(Regex("""\s+"""), " ").trim()
                    if (teachers.values.none { it.url == absUrl }) {
                        teachers[absUrl] = TimetableTarget(
                            id = id,
                            name = cleanName,
                            url = absUrl,
                            category = TimetableCategory.TEACHER
                        )
                    }
                }
                TimetableCategory.ROOM -> {
                    val cleanRoom = text.removePrefix("Sala ").removePrefix("sala ").trim()
                    if (rooms.values.none { it.url == absUrl }) {
                        rooms[absUrl] = TimetableTarget(
                            id = id,
                            name = cleanRoom.ifBlank { text },
                            url = absUrl,
                            category = TimetableCategory.ROOM
                        )
                    }
                }
            }
        }

        // 1. Parse all <a> links (always inspect section context too)
        doc.select("a[href]").forEach { a ->
            val href = a.attr("href").trim()
            val absUrl = resolveUrl(baseUrl, href).ifBlank { a.absUrl("href") }
            val sectionHint = findNearbyHeadingText(a)
            registerEntry(href, a.text(), absUrl, a.className(), sectionHint)
        }

        // 2. ALWAYS parse <select><option> dropdowns too (even when <a> links exist!)
        doc.select("select").forEach { selectEl ->
            val selectContext = "${selectEl.attr("name")} ${selectEl.attr("id")} ${selectEl.attr("onchange")} ${findNearbyHeadingText(selectEl)}".lowercase()
            selectEl.select("option[value]").forEach { opt ->
                val value = opt.attr("value").trim()
                val optText = opt.text().trim()
                if (value.isBlank() || optText.startsWith("--") || optText.startsWith("Wybierz", ignoreCase = true)) {
                    return@forEach
                }
                val absUrl = resolveOptionUrl(value, selectEl, selectContext)
                val normalizedHref = if (Regex("""^\d+$""").matches(value)) absUrl else value
                registerEntry(normalizedHref, optText, absUrl, "", selectContext)
            }
        }

        // 3. Parse onclick / JS references like open('plany/n5.html') or location.href='plany/s2.html'
        val scriptUrlRegex = Regex("""['"]((?:plany/|sale/|nauczyciele/|oddzialy/)?[onsr]?\w+\.(?:html?|php)(?:\?[^'"]*)?)['"]""", RegexOption.IGNORE_CASE)
        doc.select("[onclick]").forEach { el ->
            val match = scriptUrlRegex.find(el.attr("onclick")) ?: return@forEach
            val rel = match.groupValues[1]
            registerEntry(rel, el.text(), resolveUrl(baseUrl, rel), el.className(), findNearbyHeadingText(el))
        }

        val sortedClasses = classes.values.sortedWith(
            compareBy(
                { it.name.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 99 },
                { it.name }
            )
        )
        val sortedTeachers = teachers.values.sortedBy { it.name.lowercase() }
        val sortedRooms = rooms.values.sortedWith(
            compareBy(
                { it.name.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                { it.name.lowercase() }
            )
        )

        return TimetableCatalog(
            classes = sortedClasses,
            teachers = sortedTeachers,
            rooms = sortedRooms
        )
    }

    private fun findNearbyHeadingText(el: Element): String {
        val sectionKeywords = listOf("sale", "sala", "pracown", "pomieszcz", "nauczyc", "oddzia", "oddział", "klasy", "room", "teacher")
        var cur: Element? = el
        repeat(8) {
            val parent = cur?.parent() ?: return@repeat

            val ownAttrHint = "${cur?.id().orEmpty()} ${cur?.className().orEmpty()} ${parent.id()} ${parent.className()}".lowercase()
            if (sectionKeywords.any { ownAttrHint.contains(it) }) {
                return ownAttrHint
            }

            val prevHeading = cur?.previousElementSiblings()?.firstOrNull { sib ->
                val tag = sib.tagName().lowercase()
                if (tag in setOf("h1", "h2", "h3", "h4", "h5", "th", "p", "strong", "b", "label", "dt", "caption", "legend")) {
                    val txt = sib.text().trim().lowercase()
                    txt.length <= 60 && sectionKeywords.any { txt.contains(it) } || tag in setOf("h1", "h2", "h3", "h4", "th", "caption")
                } else if (tag in setOf("tr", "div", "span", "li", "td")) {
                    val txt = sib.text().trim().lowercase()
                    txt.length in 3..50 && sectionKeywords.any { txt.contains(it) }
                } else {
                    false
                }
            }
            if (prevHeading != null) return prevHeading.text().lowercase()

            val ownHeading = parent.selectFirst("> h1, > h2, > h3, > h4, > h5, > p, > th, > label, > .blk, > caption, > strong, > b")
            if (ownHeading != null && ownHeading != cur) {
                val txt = ownHeading.text().trim().lowercase()
                if (txt.length <= 60 && (sectionKeywords.any { txt.contains(it) } || ownHeading.tagName().lowercase() in setOf("h1", "h2", "h3", "h4", "th"))) {
                    return txt
                }
            }
            cur = parent
        }
        return ""
    }

    fun extractCatalogSubpageUrls(html: String, baseUrl: String): List<String> {
        val doc = Jsoup.parse(html, baseUrl)
        val urls = mutableListOf<String>()
        doc.select("a[href]").forEach { a ->
            val href = a.attr("href").trim()
            val lowerHref = href.lowercase()
            val lowerText = a.text().trim().lowercase()
            if (href.isBlank() || lowerHref.startsWith("javascript:") || lowerHref.startsWith("#")) return@forEach
            val cleanHref = lowerHref.substringBefore('?').substringBefore('#').trimEnd('/')
            val looksLikeList = lowerHref.contains("lista") ||
                lowerHref.contains("menu") ||
                lowerHref.contains("spis") ||
                lowerHref.contains("nauczyc") ||
                lowerHref.contains("sale") ||
                lowerHref.contains("oddz") ||
                cleanHref == "plany" ||
                cleanHref.endsWith("/plany") ||
                lowerText.contains("nauczyc") ||
                lowerText.contains("sale") ||
                lowerText.contains("lista")
            if (looksLikeList) {
                val abs = resolveUrl(baseUrl, href).ifBlank { a.absUrl("href") }
                if (abs.isNotBlank() && abs != baseUrl) urls.add(abs)
            }
        }
        return urls.distinct()
    }

    fun hasTimetableStructure(html: String): Boolean {
        val doc = Jsoup.parse(html)
        return doc.selectFirst("table.tabela") != null ||
            doc.select("table").any { table ->
                val trs = table.select("> tbody > tr, > thead > tr, > tr")
                trs.size >= 3 && (trs.firstOrNull()?.select("> th, > td")?.size ?: 0) >= 5
            }
    }

    fun parseTimetable(
        html: String,
        className: String,
        classUrl: String,
        category: TimetableCategory = TimetableCategory.CLASS
    ): ClassTimetable {
        val doc = Jsoup.parse(html, classUrl)
        val table = doc.selectFirst("table.tabela")
            ?: doc.select("table").maxByOrNull { it.select("> tbody > tr, > tr").size }
            ?: return ClassTimetable(
                className = className,
                classUrl = classUrl,
                category = category,
                days = defaultDays.mapIndexed { idx, name -> DaySchedule(idx, name, emptyList()) },
                fetchedAtMillis = System.currentTimeMillis()
            )

        val rows = table.select("> tbody > tr, > thead > tr, > tr")
        if (rows.isEmpty()) {
            return ClassTimetable(
                className = className,
                classUrl = classUrl,
                category = category,
                fetchedAtMillis = System.currentTimeMillis()
            )
        }

        // Expand table with rowspan/colspan support while preserving raw cell Element
        class GridSlot(val element: Element, val isColSpanContinuation: Boolean)
        val rowCount = rows.size
        val cellGrid = MutableList(rowCount) { mutableListOf<GridSlot?>() }
        for ((rIdx, tr) in rows.withIndex()) {
            val rowCells = tr.select("> th, > td")
            var cIdx = 0
            for (cell in rowCells) {
                while (cIdx < cellGrid[rIdx].size && cellGrid[rIdx][cIdx] != null) {
                    cIdx++
                }
                val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val colspan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                for (dr in 0 until rowspan) {
                    val targetRow = rIdx + dr
                    if (targetRow >= rowCount) break
                    while (cellGrid[targetRow].size < cIdx + colspan) {
                        cellGrid[targetRow].add(null)
                    }
                    for (dc in 0 until colspan) {
                        cellGrid[targetRow][cIdx + dc] = GridSlot(
                            element = cell,
                            isColSpanContinuation = dc > 0
                        )
                    }
                }
                cIdx += colspan
            }
        }

        val headerRow = cellGrid.firstOrNull().orEmpty()
        val headerTexts = headerRow.map { it?.element?.text()?.trim().orEmpty() }
        val dayHeaders = mutableListOf<String>()
        val dayColStart = when {
            headerRow.size >= 7 -> 2
            headerRow.size == 6 -> 1
            else -> 2
        }
        for (i in dayColStart until headerTexts.size) {
            val label = headerTexts[i]
            if (label.isNotBlank()) dayHeaders.add(label)
        }
        val finalDayNames = if (dayHeaders.size >= 5) dayHeaders.take(5) else defaultDays

        val lessonsByDay = Array(5) { mutableListOf<LessonEntry>() }

        for (rowIndex in 1 until cellGrid.size) {
            val cells = cellGrid[rowIndex]
            if (cells.size < 3) continue

            val firstCellText = cells.getOrNull(0)?.element?.text()?.trim().orEmpty()
            val secondCellText = cells.getOrNull(1)?.element?.text()?.trim().orEmpty()

            val (periodNum, timeRange) = if (dayColStart == 2) {
                val pNum = firstCellText.filter { it.isDigit() }.toIntOrNull() ?: rowIndex
                val range = secondCellText.replace(" ", "")
                pNum to range
            } else {
                val isTimeFirst = firstCellText.contains(":")
                if (isTimeFirst) {
                    rowIndex to firstCellText.replace(" ", "")
                } else {
                    (firstCellText.filter { it.isDigit() }.toIntOrNull() ?: rowIndex) to ""
                }
            }

            for (dayIdx in 0 until 5) {
                val cellIndex = dayColStart + dayIdx
                if (cellIndex >= cells.size) continue
                val slot = cells[cellIndex] ?: continue
                if (slot.isColSpanContinuation) continue
                val parsedEntries = parseLessonCell(slot.element, periodNum, timeRange)
                lessonsByDay[dayIdx].addAll(parsedEntries)
            }
        }

        val generatedInfo = doc.selectFirst("td.op, .op, #generated, footer")?.text()?.trim().orEmpty()
        val days = finalDayNames.mapIndexed { idx, name ->
            DaySchedule(
                dayIndex = idx,
                dayName = name,
                lessons = lessonsByDay[idx].sortedBy { it.period }
            )
        }
        val hashSource = days.joinToString("|") { d ->
            "${d.dayName}:${d.lessons.joinToString(";") { "${it.period}:${it.subject}:${it.teacher}:${it.room}:${it.classGroupLabel}:${it.groupTag}" }}"
        }

        return ClassTimetable(
            className = doc.selectFirst("span.tytulnapis, .tytulnapis, h1")?.text()?.trim()?.ifBlank { className } ?: className,
            classUrl = classUrl,
            category = category,
            generatedInfo = generatedInfo,
            days = days,
            fetchedAtMillis = System.currentTimeMillis(),
            contentHash = sha256(hashSource)
        )
    }

    private fun parseLessonCell(cell: Element, periodNum: Int, timeRange: String): List<LessonEntry> {
        val rawCellText = cell.text().trim()
        if (rawCellText.isBlank() || rawCellText == "-" || rawCellText == "\u00A0") {
            return emptyList()
        }

        val segments = splitCellIntoSegments(cell)
        val entries = mutableListOf<LessonEntry>()

        for (segHtml in segments) {
            val segDoc = Jsoup.parseBodyFragment(segHtml)
            val segText = segDoc.body().text().trim()
            if (segText.isBlank() || segText == "-") continue

            val teacherLinks = segDoc.select("a.n, span.n, .n, [class*=nauczyciel]").map { it.text().trim() } +
                segDoc.select("a[href]").filter {
                    val h = it.attr("href").substringBefore('?')
                    Regex("""(^|.*/)n\d+[^/]*\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(h) ||
                        h.contains("nauczyciel", ignoreCase = true)
                }.map { it.text().trim() }
            val teacher = teacherLinks.filter { it.isNotBlank() }.distinct().joinToString(", ")

            val roomLinks = segDoc.select("a.s, span.s, .s").map { it.text().trim() } +
                segDoc.select("a[href]").filter {
                    Regex("""(^|.*/)s\d+[^/]*\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(it.attr("href").substringBefore('?'))
                }.map { it.text().trim() }
            val room = roomLinks.filter { it.isNotBlank() }.distinct().joinToString(", ")

            val classLinks = segDoc.select("a.o, span.o, .o").map { it.text().trim() } +
                segDoc.select("a[href]").filter {
                    Regex("""(^|.*/)o\d+[^/]*\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(it.attr("href").substringBefore('?'))
                }.map { it.text().trim() }
            val classGroupLabel = classLinks.filter { it.isNotBlank() }.distinct().joinToString(", ")

            val subject = segDoc.select("span.p, .p").map { it.text().trim() }.filter { it.isNotBlank() }.joinToString(" ").trim()
                .ifBlank { segText.replace(Regex("""\s+"""), " ").trim() }
            val groupTag = ScheduleFilterMatcher.detectGroupTag("$subject $classGroupLabel $segText")

            entries.add(
                LessonEntry(
                    period = periodNum,
                    timeRange = timeRange,
                    subject = subject,
                    teacher = teacher,
                    room = room,
                    classGroupLabel = classGroupLabel,
                    groupTag = groupTag,
                    rawText = segText
                )
            )
        }
        return entries
    }

    private fun splitCellIntoSegments(cell: Element): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val s = current.toString().trim()
            if (s.isNotBlank()) segments.add(s)
            current.setLength(0)
        }

        fun walk(node: Node) {
            when (node) {
                is TextNode -> current.append(node.wholeText)
                is Element -> {
                    val tag = node.tagName().lowercase()
                    if (tag == "br" || tag == "hr") {
                        flush()
                    } else if (tag == "div" || tag == "p" || tag == "li") {
                        if (current.isNotEmpty()) flush()
                        current.append(node.outerHtml())
                        flush()
                    } else if (tag == "span" && node.select("span.p, .p").isNotEmpty() && node.select("br").isEmpty() && !node.hasClass("p")) {
                        if (current.toString().contains("class=\"p\"") || current.toString().contains("class='p'")) {
                            flush()
                        }
                        current.append(node.outerHtml())
                    } else {
                        current.append(node.outerHtml())
                    }
                }
            }
        }

        cell.childNodes().forEach { walk(it) }
        flush()

        return if (segments.isEmpty()) listOf(cell.html()) else segments
    }

    fun detectAuthForm(html: String, currentUrl: String, accessKey: String): AuthFormPayload? {
        val doc = Jsoup.parse(html, currentUrl)
        val forms = doc.select("form")
        for (form in forms) {
            val inputs = form.select("input")
            val passwordInput = inputs.firstOrNull { it.attr("type").equals("password", ignoreCase = true) }
            val keyLikeInput = inputs.firstOrNull { input ->
                val name = input.attr("name").lowercase()
                val placeholder = input.attr("placeholder").lowercase()
                val id = input.attr("id").lowercase()
                listOf("klucz", "key", "haslo", "hasło", "pass", "password", "kod", "code", "pin", "token", "access")
                    .any { kw -> name.contains(kw) || placeholder.contains(kw) || id.contains(kw) }
            }
            val targetInput = passwordInput ?: keyLikeInput ?: continue

            val fields = linkedMapOf<String, String>()
            for (input in inputs) {
                val name = input.attr("name").trim()
                if (name.isBlank()) continue
                val type = input.attr("type").lowercase()
                if (type == "checkbox" || type == "radio") {
                    if (input.hasAttr("checked")) {
                        fields[name] = input.attr("value").ifBlank { "on" }
                    }
                } else {
                    fields[name] = input.attr("value")
                }
            }
            val targetName = targetInput.attr("name").trim()
            if (targetName.isNotBlank()) {
                fields[targetName] = accessKey
            }

            form.select("button[type=submit][name], input[type=submit][name]").firstOrNull()?.let { btn ->
                val bName = btn.attr("name").trim()
                if (bName.isNotBlank()) {
                    fields[bName] = btn.attr("value").ifBlank { "1" }
                }
            }

            val rawAction = form.attr("action").trim()
            val actionUrl = if (rawAction.isBlank()) currentUrl else form.absUrl("action").ifBlank { resolveUrl(currentUrl, rawAction) }
            val method = form.attr("method").trim().uppercase().ifBlank { "POST" }
            return AuthFormPayload(actionUrl = actionUrl, method = method, fields = fields)
        }
        return null
    }

    fun findSubstitutionSubpageUrl(html: String, currentUrl: String): String? {
        val doc = Jsoup.parse(html, currentUrl)
        val candidates = doc.select("a[href]")
        for (a in candidates) {
            val text = a.text().lowercase()
            val href = a.attr("href").lowercase()
            if (href.startsWith("javascript:") || href.startsWith("#")) continue
            if (text.contains("zastępstw") || text.contains("zastepstw") ||
                href.contains("zastepstw") || href.contains("zastępstw") ||
                href.contains("tv.php")
            ) {
                val abs = a.absUrl("href").ifBlank { resolveUrl(currentUrl, a.attr("href")) }
                if (abs.isNotBlank() && abs != currentUrl) return abs
            }
        }
        return null
    }

    fun parseSubstitutions(html: String, sourceUrl: String): SubstitutionsSnapshot {
        val doc = Jsoup.parse(html, sourceUrl)
        // Strip non-visual tags before reading body text
        doc.select("script, style, noscript, svg").remove()

        val bodyText = doc.body()?.text()?.trim().orEmpty()
        val hasPasswordForm = doc.select("form input[type=password]").isNotEmpty()
        val looksLikeSubstitutions = bodyText.contains("zastępstw", ignoreCase = true) ||
            bodyText.contains("zastepstw", ignoreCase = true) ||
            doc.select("table tr").size >= 2 ||
            classTokenRegex.containsMatchIn(bodyText)

        val dateHeading = extractDateHeading(doc)
        val items = mutableListOf<SubstitutionItem>()
        val announcements = mutableListOf<String>()
        val notes = mutableListOf<String>()

        // 1. Classify HTML tables into substitution tables vs info/uwagi box tables
        val allTables = doc.select("table")
        val subTables = mutableSetOf<Element>()
        for (table in allTables) {
            if (isSubstitutionTable(table)) {
                subTables.add(table)
                parseSubstitutionTable(table, dateHeading, items, announcements, notes)
            }
        }

        // 2. Extract Informacje (top of page) and Uwagi (bottom of page) in DOM order
        collectInfoAndNotesFromDom(
            doc = doc,
            dateHeading = dateHeading,
            subTables = subTables,
            outAnnouncements = announcements,
            outNotes = notes
        )

        // 3. Parse structured div/card/list substitution items if no substitution table matched
        if (items.isEmpty()) {
            parseDivAndListSubstitutions(doc, dateHeading, items, announcements, notes)
        }

        // 4. Final readable-text fallback if page has content that didn't fit tables or card selectors
        if (items.isEmpty() && bodyText.isNotBlank() && !(hasPasswordForm && !looksLikeSubstitutions)) {
            parseRawTextLinesFallback(doc, dateHeading, items, announcements, notes)
        }

        val cleanAnnouncements = announcements.distinct().take(20)
        val cleanNotes = notes.distinct().take(20)

        val hashInput = items.joinToString("|") {
            "${it.dateLabel}:${it.className}:${it.period}:${it.subject}:${it.substituteTeacher}:${it.room}:${it.note}"
        } + "::" + cleanAnnouncements.joinToString("|") + "::" + cleanNotes.joinToString("|")

        return SubstitutionsSnapshot(
            dateHeading = dateHeading,
            items = items.distinctBy { it.id },
            announcements = cleanAnnouncements,
            notes = cleanNotes,
            fetchedAtMillis = System.currentTimeMillis(),
            rawHash = sha256(hashInput),
            authSucceeded = !(hasPasswordForm && !looksLikeSubstitutions),
            sourceUrl = sourceUrl
        )
    }

    private fun extractDateHeading(doc: Document): String {
        val headings = doc.select("h1, h2, h3, h4, .date, .data, .header, caption, strong, b")
        for (h in headings) {
            val text = h.text().trim()
            if (text.length in 4..120 && (dateRegex.containsMatchIn(text) ||
                    text.contains("zastępstw", ignoreCase = true) ||
                    text.contains("zastepstw", ignoreCase = true))
            ) {
                return text
            }
        }
        val bodyMatch = dateRegex.find(doc.body()?.text().orEmpty())
        return bodyMatch?.value?.let { "Zastępstwa • $it" } ?: "Zastępstwa"
    }

    private fun isSubstitutionTable(table: Element): Boolean {
        val rows = table.select("> tbody > tr, > thead > tr, > tr")
        if (rows.isEmpty()) return false
        val firstCells = rows.firstOrNull()?.select("> th, > td")?.map { it.text().trim() }.orEmpty()
        if (looksLikeHeaderTexts(firstCells)) return true

        val maxCols = rows.maxOfOrNull { it.select("> th, > td").size } ?: 0
        val tableText = table.text().trim()
        val lower = tableText.lowercase()
        if (maxCols <= 2 && (lower.startsWith("informacj") || lower.startsWith("uwag") || lower.startsWith("komunikat"))) {
            return false
        }
        val hasClassRows = rows.count { row ->
            val cells = row.select("> th, > td").map { it.text().trim() }
            cells.any { isLikelyClassCell(it) || classTokenRegex.containsMatchIn(it) }
        }
        return (rows.size >= 2 && maxCols >= 3) || hasClassRows >= 2
    }

    private data class ExpandedCell(
        val text: String,
        val isHeaderTag: Boolean,
        val colspan: Int
    )

    private fun expandTableGrid(table: Element): List<List<ExpandedCell>> {
        val trs = table.select("> tbody > tr, > thead > tr, > tr")
        val rowCount = trs.size
        if (rowCount == 0) return emptyList()

        val grid = MutableList(rowCount) { mutableListOf<ExpandedCell?>() }

        for ((rIdx, tr) in trs.withIndex()) {
            val rowCells = tr.select("> th, > td")
            var cIdx = 0
            for (cell in rowCells) {
                while (cIdx < grid[rIdx].size && grid[rIdx][cIdx] != null) {
                    cIdx++
                }
                val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val colspan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val text = cellTextWithBreaks(cell)
                val isTh = cell.tagName().equals("th", ignoreCase = true)

                for (dr in 0 until rowspan) {
                    val targetRow = rIdx + dr
                    if (targetRow >= rowCount) break
                    while (grid[targetRow].size < cIdx + colspan) {
                        grid[targetRow].add(null)
                    }
                    for (dc in 0 until colspan) {
                        grid[targetRow][cIdx + dc] = ExpandedCell(
                            text = text,
                            isHeaderTag = isTh,
                            colspan = colspan
                        )
                    }
                }
                cIdx += colspan
            }
        }

        return grid.map { row ->
            row.map { it ?: ExpandedCell("", isHeaderTag = false, colspan = 1) }
        }
    }

    private fun cellTextWithBreaks(cell: Element): String {
        val clone = cell.clone()
        clone.select("br").forEach { it.replaceWith(TextNode("\n")) }
        clone.select("p, div, li, tr").forEach { it.appendChild(TextNode("\n")) }
        return clone.wholeText()
            .lines()
            .map { it.replace(Regex("""[ \t\r\u00A0]+"""), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun parseSubstitutionTable(
        table: Element,
        defaultDate: String,
        outItems: MutableList<SubstitutionItem>,
        outAnnouncements: MutableList<String>,
        outNotes: MutableList<String>
    ) {
        val grid = expandTableGrid(table)
        if (grid.isEmpty()) return

        var colClass = -1
        var colPeriod = -1
        var colAbsent = -1
        var colSubject = -1
        var colSub = -1
        var colRoom = -1
        var colNote = -1

        var currentSectionTeacher = ""
        var currentSectionClass = ""
        var currentSectionDate = defaultDate

        val firstRow = grid.first()
        var startRow = 0
        if (firstRow.any { it.isHeaderTag } || looksLikeHeaderTexts(firstRow.map { it.text })) {
            startRow = 1
            firstRow.forEachIndexed { idx, cell ->
                val h = cell.text.trim().lowercase()
                when {
                    h.contains("klas") || h.contains("oddz") || (h.contains("grup") && colClass == -1) -> colClass = idx
                    h.contains("lekc") || h.contains("godz") || h == "nr" || h.contains("l.p") -> colPeriod = idx
                    h.contains("nieobec") || (h.contains("nauczyciel") && colAbsent == -1) -> colAbsent = idx
                    h.contains("przedm") || h.contains("zaję") || h.contains("zaje") -> colSubject = idx
                    h.contains("zastęp") || h.contains("zastep") || h.contains("zmian") || h.contains("kto") || h.contains("nowy") -> colSub = idx
                    h.contains("sal") -> colRoom = idx
                    h.contains("uwag") || h.contains("opis") || h.contains("koment") || h.contains("info") -> colNote = idx
                }
            }
        }

        for (rIdx in startRow until grid.size) {
            val row = grid[rIdx]
            if (row.isEmpty()) continue
            val values = row.map { it.text.trim() }
            if (values.all { it.isBlank() }) continue

            // Detect section header row (single cell or all cells identical due to colspan >= 2)
            val distinctNonEmpty = values.filter { it.isNotBlank() }.distinct()
            if (distinctNonEmpty.size == 1 && (row.first().colspan >= 2 || row.size == 1)) {
                val single = distinctNonEmpty.first()
                val lowerSingle = single.lowercase()
                when {
                    lowerSingle.startsWith("uwag") -> {
                        cleanSectionLines(single, isNote = true).forEach { outNotes.add(it) }
                    }
                    lowerSingle.startsWith("informacj") || lowerSingle.startsWith("komunikat") -> {
                        cleanSectionLines(single, isNote = false).forEach { outAnnouncements.add(it) }
                    }
                    dateRegex.containsMatchIn(single) -> currentSectionDate = single
                    single.contains("klasa", ignoreCase = true) ||
                        single.matches(Regex("""^[1-6]\s?[A-Za-zĄĆĘŁŃÓŚŹŻ]{1,5}\d?$""")) -> {
                        currentSectionClass = single
                    }
                    looksLikeHeaderTexts(listOf(single)) -> Unit
                    single.length in 3..90 -> currentSectionTeacher = single
                }
                continue
            }

            if (looksLikeHeaderTexts(values)) continue

            val parsedClass = values.getOrNull(colClass)?.takeIf { it.isNotBlank() }
                ?: currentSectionClass.takeIf { it.isNotBlank() }
                ?: values.firstOrNull { isLikelyClassCell(it) }
                ?: values.firstOrNull { classTokenRegex.containsMatchIn(it) }?.let {
                    classTokenRegex.find(it)?.groupValues?.get(1)
                }
                ?: ""

            val period = values.getOrNull(colPeriod).orEmpty()
                .ifBlank { values.firstOrNull { it.matches(Regex("""^\d{1,2}\.?(\s*[-–]\s*\d{1,2})?(\s*\(.*\))?$""")) }.orEmpty() }

            val absent = values.getOrNull(colAbsent).orEmpty().ifBlank { currentSectionTeacher }
            val subject = values.getOrNull(colSubject).orEmpty()
            val substitute = values.getOrNull(colSub).orEmpty()
            val room = values.getOrNull(colRoom).orEmpty()
                .ifBlank {
                    if (colRoom == -1) values.firstOrNull { isLikelyRoomCell(it) }.orEmpty() else ""
                }
            val note = values.getOrNull(colNote).orEmpty()

            val remainingText = values.filterIndexed { idx, v ->
                idx != colClass && idx != colPeriod && idx != colAbsent &&
                    idx != colSubject && idx != colSub && idx != colRoom && idx != colNote &&
                    v.isNotBlank() && v != parsedClass && v != period && v != room
            }.joinToString(" • ")

            val resolvedSubject = subject.ifBlank { remainingText }
            val resolvedSubstitute = substitute.ifBlank {
                if (colSub == -1 && subject.isNotBlank()) remainingText else ""
            }

            val combined = "$parsedClass $period $absent $resolvedSubject $resolvedSubstitute $room $note"
            if (combined.isBlank()) continue

            val isCancelled = isCancellationText(combined)
            val groupTag = ScheduleFilterMatcher.detectGroupTag(combined)
            val id = sha256("$currentSectionDate|$parsedClass|$period|$resolvedSubject|$resolvedSubstitute|$room|$rIdx")

            outItems.add(
                SubstitutionItem(
                    id = id,
                    dateLabel = currentSectionDate,
                    className = parsedClass,
                    period = period,
                    timeRange = "",
                    absentTeacher = absent,
                    subject = resolvedSubject,
                    substituteTeacher = resolvedSubstitute,
                    room = room,
                    note = note,
                    groupTag = groupTag,
                    isCancellation = isCancelled
                )
            )
        }
    }

    private fun collectInfoAndNotesFromDom(
        doc: Document,
        dateHeading: String,
        subTables: Set<Element>,
        outAnnouncements: MutableList<String>,
        outNotes: MutableList<String>
    ) {
        val body = doc.body() ?: return
        var seenSubstitutions = false
        var activeSectionHint: Boolean? = null // false = Informacje, true = Uwagi

        fun isBoilerplate(text: String): Boolean {
            val lower = text.lowercase()
            if (text.equals(dateHeading, ignoreCase = true)) return true
            if (lower.contains("klucz") && lower.contains("zaloguj")) return true
            if (lower == "zaloguj" || lower == "wyloguj" || lower == "odśwież" || lower == "odswiez") return true
            if (lower.startsWith("brak zastępstw") || lower.startsWith("brak zastepstw")) return false
            return false
        }

        fun detectHint(el: Element, rawText: String): Boolean? {
            val attrs = "${el.id()} ${el.className()}".lowercase()
            val lower = rawText.trim().lowercase()
            return when {
                attrs.contains("uwag") || attrs.contains("note") || attrs.contains("footer") ||
                    lower.startsWith("uwagi") || lower.startsWith("uwaga:") || lower == "uwaga" -> true
                attrs.contains("info") || attrs.contains("komunikat") || attrs.contains("notice") ||
                    attrs.contains("alert") || attrs.contains("announcement") ||
                    lower.startsWith("informacj") || lower.startsWith("komunikat") || lower.startsWith("ogłoszen") -> false
                else -> null
            }
        }

        fun recordLines(rawText: String, isNote: Boolean) {
            cleanSectionLines(rawText, isNote = isNote).forEach { line ->
                if (!isBoilerplate(line)) {
                    if (isNote) {
                        if (!outNotes.contains(line)) outNotes.add(line)
                    } else {
                        if (!outAnnouncements.contains(line)) outAnnouncements.add(line)
                    }
                }
            }
        }

        fun walk(el: Element) {
            if (el in subTables) {
                seenSubstitutions = true
                activeSectionHint = null
                return
            }
            if (el.tagName().equals("form", ignoreCase = true)) return

            val containsSubTable = el.select("table").any { it in subTables }
            if (containsSubTable) {
                el.children().forEach { walk(it) }
                return
            }

            val tag = el.tagName().lowercase()
            val rawText = cellTextWithBreaks(el)
            if (rawText.isBlank()) return

            // Do not treat div/card substitution rows as Informacje/Uwagi boxes
            val hasSubstitutionClassCue = classTokenRegex.containsMatchIn(rawText) && (
                rawText.contains("lekcj", ignoreCase = true) ||
                    rawText.contains("lek.", ignoreCase = true) ||
                    rawText.contains("godz", ignoreCase = true) ||
                    isCancellationText(rawText) ||
                    rawText.contains("zastęp", ignoreCase = true) ||
                    rawText.contains("zastep", ignoreCase = true)
                )
            if (hasSubstitutionClassCue && detectHint(el, rawText) == null) {
                seenSubstitutions = true
                return
            }

            // Standalone section header like <h3>Informacje</h3> or <h3>Uwagi</h3>
            val trimmedLower = rawText.trim().lowercase().trimEnd(':')
            if (tag in setOf("h1", "h2", "h3", "h4", "h5", "strong", "b", "legend", "caption") ||
                trimmedLower in setOf("informacje", "informacja", "komunikaty", "uwagi", "uwaga")
            ) {
                when {
                    trimmedLower.startsWith("uwag") -> {
                        activeSectionHint = true
                        return
                    }
                    trimmedLower.startsWith("informacj") || trimmedLower.startsWith("komunikat") || trimmedLower.startsWith("ogłoszen") -> {
                        activeSectionHint = false
                        return
                    }
                }
                if (rawText.equals(dateHeading, ignoreCase = true)) return
            }

            val childBlockTags = el.children().filter { child ->
                val cTag = child.tagName().lowercase()
                cTag in setOf("div", "section", "aside", "fieldset", "blockquote", "table", "p", "ul", "ol", "h1", "h2", "h3", "h4")
            }

            val explicitHint = detectHint(el, rawText)
            val isBoxElement = tag in setOf("fieldset", "aside", "blockquote", "section", "table") ||
                (tag == "div" && (explicitHint != null || el.attr("style").contains("border", ignoreCase = true) || childBlockTags.isEmpty()))

            if (isBoxElement && (childBlockTags.isEmpty() || explicitHint != null || tag in setOf("fieldset", "aside", "blockquote", "table"))) {
                val targetIsNote = explicitHint ?: activeSectionHint ?: seenSubstitutions
                recordLines(rawText, isNote = targetIsNote)
                activeSectionHint = null
                return
            }

            if (childBlockTags.isNotEmpty()) {
                el.children().forEach { walk(it) }
            } else {
                val targetIsNote = explicitHint ?: activeSectionHint ?: seenSubstitutions
                recordLines(rawText, isNote = targetIsNote)
                if (explicitHint != null) activeSectionHint = null
            }
        }

        body.children().forEach { walk(it) }
    }

    private fun cleanSectionLines(rawText: String, isNote: Boolean): List<String> {
        val headerRegex = if (isNote) {
            Regex("""^(?:uwagi|uwaga)\s*[:\-–]?\s*""", RegexOption.IGNORE_CASE)
        } else {
            Regex("""^(?:informacje|informacja|komunikaty|komunikat|ogłoszenia|ogloszenia)\s*[:\-–]?\s*""", RegexOption.IGNORE_CASE)
        }
        return rawText
            .split("\n")
            .mapIndexed { idx, line ->
                val trimmed = line.trim()
                if (idx == 0) trimmed.replace(headerRegex, "").trim() else trimmed
            }
            .filter { line ->
                line.length >= 3 &&
                    !line.equals("informacje", ignoreCase = true) &&
                    !line.equals("uwagi", ignoreCase = true) &&
                    !line.equals("komunikaty", ignoreCase = true)
            }
    }

    private fun isLikelyClassCell(value: String): Boolean {
        val clean = value.trim()
        if (clean.length !in 2..20) return false
        return Regex("""^(?:kl\.?\s*)?[1-6]\s?[A-Za-zĄĆĘŁŃÓŚŹŻ]{1,5}\d?(?:\s*[-/,(].*)?$""", RegexOption.IGNORE_CASE)
            .matches(clean)
    }

    private fun isLikelyRoomCell(value: String): Boolean {
        val clean = value.trim()
        return Regex("""^(?:sala\s+|s\.?\s*)?\d{2,3}[A-Za-z]?$|^gim\w*$|^aula$""", RegexOption.IGNORE_CASE)
            .matches(clean)
    }

    private fun looksLikeHeaderTexts(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ") { it.trim().lowercase() }
        val keywords = listOf("klasa", "oddział", "oddzial", "lekcja", "nauczyciel", "zastępstwo", "zastepstwo", "zastępujący", "zastepujacy", "przedmiot", "sala", "uwagi", "godz")
        return keywords.count { joined.contains(it) } >= 2
    }

    private fun parseDivAndListSubstitutions(
        doc: Document,
        defaultDate: String,
        outItems: MutableList<SubstitutionItem>,
        outAnnouncements: MutableList<String>,
        outNotes: MutableList<String>
    ) {
        val periodRegex = Regex("""(?:lekcja|lek\.?|godz\.?)\s*(\d{1,2})|^(\d{1,2})\.\s""", RegexOption.IGNORE_CASE)

        val candidateBlocks = doc.select("div.zastepstwo, div.substitution, div.card, div.item, div.row, div.entry, li, p, article")
        for ((idx, block) in candidateBlocks.withIndex()) {
            val childBlocks = block.select("> div, > p, > ul, > table")
            if (block.tagName().equals("div", ignoreCase = true) && childBlocks.size > 2) continue

            val rawWithBreaks = cellTextWithBreaks(block)
            val lines = rawWithBreaks.split("\n").map { it.trim() }.filter { it.length >= 6 }

            for ((lineIdx, line) in lines.withIndex()) {
                if (line.contains("klucz", ignoreCase = true) && line.contains("zaloguj", ignoreCase = true)) continue
                val classMatch = classTokenRegex.find(line)
                if (classMatch != null && (periodRegex.containsMatchIn(line) || line.contains("lekcj", ignoreCase = true) || isCancellationText(line) || line.contains("sal", ignoreCase = true))) {
                    val cls = classMatch.groupValues[1].replace(" ", "").uppercase()
                    val pMatch = periodRegex.find(line)
                    val period = pMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }.orEmpty()
                    val isCancelled = isCancellationText(line)

                    outItems.add(
                        SubstitutionItem(
                            id = sha256("$defaultDate|$cls|$line|$idx|$lineIdx"),
                            dateLabel = defaultDate,
                            className = cls,
                            period = period,
                            subject = line,
                            groupTag = ScheduleFilterMatcher.detectGroupTag(line),
                            isCancellation = isCancelled
                        )
                    )
                } else if (line.lowercase().startsWith("uwag")) {
                    cleanSectionLines(line, isNote = true).forEach { cleaned ->
                        if (!outNotes.contains(cleaned)) outNotes.add(cleaned)
                    }
                } else if (line.length in 15..450 && !outNotes.contains(line) && !outAnnouncements.contains(line)) {
                    outAnnouncements.add(line)
                }
            }
        }
    }

    private fun parseRawTextLinesFallback(
        doc: Document,
        defaultDate: String,
        outItems: MutableList<SubstitutionItem>,
        outAnnouncements: MutableList<String>,
        outNotes: MutableList<String>
    ) {
        val body = doc.body() ?: return
        val text = cellTextWithBreaks(body)
        val lines = text.split("\n").map { it.trim() }.filter { it.length >= 4 }
        for ((idx, line) in lines.withIndex()) {
            if (line.equals(defaultDate, ignoreCase = true)) continue
            if (line.contains("klucz", ignoreCase = true) && line.contains("zaloguj", ignoreCase = true)) continue
            val classMatch = classTokenRegex.find(line)
            if (classMatch != null && (line.contains("lekcj", ignoreCase = true) || isCancellationText(line) || line.contains("sal", ignoreCase = true))) {
                val cls = classMatch.groupValues[1].replace(" ", "").uppercase()
                outItems.add(
                    SubstitutionItem(
                        id = sha256("raw|$defaultDate|$cls|$line|$idx"),
                        dateLabel = defaultDate,
                        className = cls,
                        subject = line,
                        groupTag = ScheduleFilterMatcher.detectGroupTag(line),
                        isCancellation = isCancellationText(line)
                    )
                )
            } else if (line.lowercase().startsWith("uwag")) {
                cleanSectionLines(line, isNote = true).forEach { cleaned ->
                    if (!outNotes.contains(cleaned)) outNotes.add(cleaned)
                }
            } else if (line.length >= 12 && !outNotes.contains(line) && !outAnnouncements.contains(line)) {
                outAnnouncements.add(line)
            }
        }
    }

    private fun isCancellationText(text: String): Boolean {
        return text.contains("odwołan", ignoreCase = true) ||
            text.contains("odwolan", ignoreCase = true) ||
            text.contains("zwoln", ignoreCase = true) ||
            text.contains("okienk", ignoreCase = true) ||
            text.contains("brak zajęć", ignoreCase = true) ||
            text.contains("brak zajec", ignoreCase = true) ||
            text.contains("do domu", ignoreCase = true)
    }

    fun aggregateTeacherScheduleFromTimetables(
        teacherAbbr: String,
        sourceTimetables: List<ClassTimetable>,
        sourceUrl: String
    ): ClassTimetable {
        val cleanAbbr = teacherAbbr.trim().uppercase()
        val lessonsByDay = Array(5) { linkedMapOf<String, LessonEntry>() }

        for (plan in sourceTimetables) {
            for (day in plan.days) {
                if (day.dayIndex !in 0..4) continue
                val dayBucket = lessonsByDay[day.dayIndex]
                for (lesson in day.lessons) {
                    val matches = ScheduleFilterMatcher.matchesTeacherToken(lesson.teacher, cleanAbbr) ||
                        ((lesson.teacher.isBlank() || lesson.teacher.length > cleanAbbr.length + 2) &&
                            ScheduleFilterMatcher.matchesTeacherToken(lesson.rawText, cleanAbbr))
                    if (!matches) continue

                    val clsLabel = lesson.classGroupLabel.ifBlank { plan.className }.trim()
                    val slotKey = "${lesson.period}|${lesson.timeRange}|${lesson.subject}|${lesson.room}|${lesson.groupTag.orEmpty()}"
                    val existing = dayBucket[slotKey]
                    if (existing != null) {
                        val combinedClasses = (existing.classGroupLabel.split(",") + clsLabel.split(","))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(", ")
                        dayBucket[slotKey] = existing.copy(classGroupLabel = combinedClasses)
                    } else {
                        dayBucket[slotKey] = lesson.copy(
                            classGroupLabel = clsLabel,
                            teacher = lesson.teacher.ifBlank { cleanAbbr }
                        )
                    }
                }
            }
        }

        val days = defaultDays.mapIndexed { idx, name ->
            DaySchedule(
                dayIndex = idx,
                dayName = name,
                lessons = lessonsByDay[idx].values.sortedBy { it.period }
            )
        }
        val hashSource = days.joinToString("|") { d ->
            "${d.dayName}:${d.lessons.joinToString(";") { "${it.period}:${it.subject}:${it.teacher}:${it.room}:${it.classGroupLabel}:${it.groupTag}" }}"
        }

        return ClassTimetable(
            className = cleanAbbr,
            classUrl = sourceUrl,
            category = TimetableCategory.TEACHER,
            generatedInfo = "Przeszukano plany: ${sourceTimetables.size}",
            days = days,
            fetchedAtMillis = System.currentTimeMillis(),
            contentHash = sha256(hashSource)
        )
    }

    fun tagLessonsWithSchool(
        timetable: ClassTimetable,
        school: TeacherSchoolConfig
    ): ClassTimetable {
        return timetable.copy(
            days = timetable.days.map { day ->
                day.copy(
                    lessons = day.lessons.map { lesson ->
                        lesson.copy(
                            schoolId = school.id,
                            schoolName = school.schoolName,
                            schoolColorIndex = school.colorIndex
                        )
                    }
                )
            }
        )
    }

    fun mergeMultiSchoolTeacherTimetables(
        schoolSchedules: List<Pair<TeacherSchoolConfig, ClassTimetable>>,
        failedSchools: List<String> = emptyList()
    ): ClassTimetable {
        val lessonsByDay = Array(5) { mutableListOf<LessonEntry>() }
        val summaryParts = mutableListOf<String>()

        for ((school, rawPlan) in schoolSchedules) {
            val taggedPlan = tagLessonsWithSchool(rawPlan, school)
            var countInSchool = 0
            for (day in taggedPlan.days) {
                if (day.dayIndex !in 0..4) continue
                lessonsByDay[day.dayIndex].addAll(day.lessons)
                countInSchool += day.lessons.size
            }
            summaryParts += "${school.schoolName} (${school.teacherAbbreviation}): $countInSchool lek."
        }

        val days = defaultDays.mapIndexed { idx, name ->
            val sortedLessons = lessonsByDay[idx].sortedWith(
                compareBy(
                    { extractStartMinutesForSort(it.timeRange) ?: (it.period * 100) },
                    { it.period },
                    { it.schoolColorIndex }
                )
            )
            DaySchedule(
                dayIndex = idx,
                dayName = name,
                lessons = sortedLessons
            )
        }

        val hashSource = days.joinToString("|") { d ->
            "${d.dayName}:${d.lessons.joinToString(";") { "${it.schoolId}:${it.period}:${it.subject}:${it.teacher}:${it.room}:${it.classGroupLabel}:${it.groupTag}" }}"
        }

        val primaryTitle = schoolSchedules.map { it.first.teacherAbbreviation }
            .distinct()
            .joinToString(" / ")
            .ifBlank { "Nauczyciel" }
        val primaryUrl = schoolSchedules.firstOrNull()?.second?.classUrl.orEmpty()
        val infoSummary = buildString {
            append(summaryParts.joinToString(" • "))
            if (failedSchools.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("Błąd pobierania: ${failedSchools.joinToString(", ")}")
            }
        }

        return ClassTimetable(
            className = primaryTitle,
            classUrl = primaryUrl,
            category = TimetableCategory.TEACHER,
            generatedInfo = infoSummary,
            days = days,
            fetchedAtMillis = System.currentTimeMillis(),
            contentHash = sha256(hashSource)
        )
    }

    private fun extractStartMinutesForSort(timeRange: String): Int? {
        val startPart = timeRange.split("-", "–").firstOrNull()?.trim() ?: return null
        val clean = startPart.replace(".", ":")
        val pieces = clean.split(":")
        if (pieces.size != 2) return null
        val h = pieces[0].filter { it.isDigit() }.toIntOrNull() ?: return null
        val m = pieces[1].filter { it.isDigit() }.toIntOrNull() ?: return null
        return h * 60 + m
    }

    fun extractTeacherAbbreviationsFromTimetables(timetables: List<ClassTimetable>): List<String> {
        val seenNormalized = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (plan in timetables) {
            for (day in plan.days) {
                for (lesson in day.lessons) {
                    if (lesson.teacher.isBlank()) continue
                    val tokens = lesson.teacher
                        .split(Regex("""[,;/]+"""))
                        .map { it.trim() }
                        .filter { token ->
                            token.isNotBlank() &&
                                token.length <= 24 &&
                                token != "-" &&
                                token != "—" &&
                                token.any { ch -> ch.isLetter() } &&
                                !token.equals("brak", ignoreCase = true) &&
                                !token.equals("odwołane", ignoreCase = true)
                        }
                    for (token in tokens) {
                        val key = token.uppercase()
                        if (seenNormalized.add(key)) {
                            result.add(token)
                        }
                    }
                }
            }
        }
        return result.sortedBy { it.lowercase() }
    }

    fun parseAppRepositoryApks(html: String, baseUrl: String): List<ApkCandidate> {
        val doc = Jsoup.parse(html, baseUrl)
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val dateRegex = Regex(
            """(\d{4}-\d{2}-\d{2}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?|\d{2}[-./]\w{3,9}[-./]\d{4}(?:\s+\d{1,2}:\d{2})?|\d{2}\.\d{2}\.\d{4}(?:\s+\d{1,2}:\d{2})?)"""
        )

        val candidates = linkedMapOf<String, ApkCandidate>()
        for (a in doc.select("a[href]")) {
            val rawHref = a.attr("href").trim()
            val cleanHref = rawHref.substringBefore('?').substringBefore('#')
            if (!cleanHref.endsWith(".apk", ignoreCase = true)) continue

            val absUrl = a.absUrl("href").takeIf { it.isNotBlank() } ?: resolveUrl(normalizedBase, rawHref)
            val fileName = cleanHref.substringAfterLast('/').ifBlank { a.text().trim() }.ifBlank { "app.apk" }
            val rowContext = a.closest("tr")?.text()?.trim()
                ?: a.parent()?.text()?.trim()
                ?: a.text().trim()
            val extractedVersion = AppVersionHelper.extractVersionFromText("$fileName ${a.text()}")
            val dateText = dateRegex.find(rowContext)?.groupValues?.get(1).orEmpty()

            candidates[absUrl] = ApkCandidate(
                fileName = fileName,
                downloadUrl = absUrl,
                versionName = extractedVersion,
                directoryDateText = dateText
            )
        }

        return candidates.values.sortedWith(
            Comparator { a, b ->
                val verCmp = AppVersionHelper.compareVersions(b.versionName, a.versionName)
                if (verCmp != 0) verCmp
                else b.directoryDateText.compareTo(a.directoryDateText)
            }
        )
    }

    fun resolveUrl(baseUrl: String, relative: String): String {
        return try {
            URI(baseUrl).resolve(relative).toString()
        } catch (_: Exception) {
            relative
        }
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
