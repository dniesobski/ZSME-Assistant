package com.example

import com.example.data.model.ScheduleFilterMatcher
import com.example.data.model.StudentGroup
import com.example.data.model.SubstitutionItem
import com.example.data.model.TimetableCategory
import com.example.data.model.UserPreferences
import com.example.data.network.ZsmeHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZsmeParserAndMatcherTest {

    @Test
    fun `default substitutions url points to tv php without requiring key`() {
        assertEquals("https://sekretariat.zsme.pl/tv.php", UserPreferences.DEFAULT_SUBSTITUTIONS_URL)
        assertEquals("https://plan.zsme.pl/", UserPreferences.DEFAULT_PLAN_URL)
    }

    @Test
    fun `parseTimetableCatalog extracts classes, teacher abbreviations, and rooms`() {
        val html = """
            <html><body>
              <ul>
                <li><a href="plany/o1.html" target="plan">1A</a></li>
                <li><a href="plany/o12.html" target="plan">2TI</a></li>
                <li><a href="plany/n3.html" target="plan">JK</a></li>
                <li><a href="plany/n7.html" target="plan">AB</a></li>
                <li><a href="plany/s5.html" target="plan">204</a></li>
                <li><a href="plany/s1.html" target="plan">102</a></li>
              </ul>
            </body></html>
        """.trimIndent()

        val catalog = ZsmeHtmlParser.parseTimetableCatalog(html, "https://plan.zsme.pl/lista.html")
        assertEquals(2, catalog.classes.size)
        assertEquals(2, catalog.teachers.size)
        assertEquals(2, catalog.rooms.size)
        assertEquals("AB", catalog.teachers[0].name)
        assertEquals("JK", catalog.teachers[1].name)
        assertEquals("102", catalog.rooms[0].name)
        assertEquals("204", catalog.rooms[1].name)
    }

    @Test
    fun `parseTimetable extracts class label for teacher and room schedules`() {
        val html = """
            <html><body>
              <span class="tytulnapis">Sala 204</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00- 8:45</td>
                  <td class="l"><a class="o">2TI</a> <a class="n">JK</a> <span class="p">Matematyka</span></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val tt = ZsmeHtmlParser.parseTimetable(
            html = html,
            className = "204",
            classUrl = "https://plan.zsme.pl/plany/s5.html",
            category = TimetableCategory.ROOM
        )
        assertEquals(TimetableCategory.ROOM, tt.category)
        val lesson = tt.days[0].lessons[0]
        assertEquals("2TI", lesson.classGroupLabel)
        assertEquals("JK", lesson.teacher)
        assertEquals("Matematyka", lesson.subject)
    }

    @Test
    fun `parseTimetable handles split groups in Optivum cell`() {
        val html = """
            <html><body>
              <span class="tytulnapis">2TI</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00- 8:45</td>
                  <td class="l">
                    <span class="p">J. angielski-1/2</span> <a class="n">AB</a> <a class="s">102</a><br>
                    <span class="p">J. angielski-2/2</span> <a class="n">CD</a> <a class="s">105</a>
                  </td>
                  <td class="l"><span class="p">Matematyka</span> <a class="n">JK</a> <a class="s">204</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val tt = ZsmeHtmlParser.parseTimetable(html, "2TI", "https://plan.zsme.pl/plany/o12.html")
        assertEquals("2TI", tt.className)
        assertEquals(2, tt.days[0].lessons.size)
        assertEquals("1/2", tt.days[0].lessons[0].groupTag)
        assertEquals("2/2", tt.days[0].lessons[1].groupTag)
        assertEquals(1, tt.days[1].lessons.size)
        assertEquals("Matematyka", tt.days[1].lessons[0].subject)
    }

    @Test
    fun `detectAuthForm fills access key into password field`() {
        val html = """
            <html><body>
              <form method="post" action="login.php">
                <input type="hidden" name="csrf" value="abc123" />
                <input type="password" name="klucz" placeholder="Podaj klucz" />
                <button type="submit" name="submit" value="ok">Wejdź</button>
              </form>
            </body></html>
        """.trimIndent()

        val payload = ZsmeHtmlParser.detectAuthForm(html, "https://sekretariat.zsme.pl/", "zsmeedukacja")
        assertNotNull(payload)
        assertEquals("https://sekretariat.zsme.pl/login.php", payload?.actionUrl)
        assertEquals("POST", payload?.method)
        assertEquals("zsmeedukacja", payload?.fields?.get("klucz"))
        assertEquals("abc123", payload?.fields?.get("csrf"))
    }

    @Test
    fun `parseSubstitutions handles rowspan teacher grouping on tv php`() {
        val html = """
            <html><body>
              <h1>Zastępstwa 13.11.2026</h1>
              <table>
                <tr><th>Nauczyciel</th><th>Lekcja</th><th>Klasa</th><th>Przedmiot</th><th>Zastępstwo</th><th>Sala</th></tr>
                <tr>
                  <td rowspan="2">Jan Kowalski</td>
                  <td>1</td>
                  <td>2TI (1/2)</td>
                  <td>Matematyka</td>
                  <td>Anna Nowak</td>
                  <td>204</td>
                </tr>
                <tr>
                  <td>2</td>
                  <td>3TM</td>
                  <td>Fizyka</td>
                  <td>Odwołane</td>
                  <td>-</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val snapshot = ZsmeHtmlParser.parseSubstitutions(html, "https://sekretariat.zsme.pl/tv.php")
        assertTrue(snapshot.authSucceeded)
        assertEquals(2, snapshot.items.size)
        assertEquals("Jan Kowalski", snapshot.items[0].absentTeacher)
        assertEquals("Jan Kowalski", snapshot.items[1].absentTeacher)
        assertEquals("3TM", snapshot.items[1].className)
        assertTrue(snapshot.items[1].isCancellation)
    }

    @Test
    fun `parseSubstitutions handles div card layout without tables`() {
        val html = """
            <html><body>
              <h2>Zastępstwa na piątek 13.11.2026</h2>
              <div class="card">Lekcja 4. Klasa 2TI gr.1 - J. angielski z M. Lis w sali 108</div>
              <div class="card">Lekcja 5. Klasa 1A - Geografia odwołane, uczniowie zwolnieni</div>
            </body></html>
        """.trimIndent()

        val snapshot = ZsmeHtmlParser.parseSubstitutions(html, "https://sekretariat.zsme.pl/tv.php")
        assertEquals(2, snapshot.items.size)
        assertEquals("2TI", snapshot.items[0].className)
        assertEquals("1/2", snapshot.items[0].groupTag)
        assertEquals("1A", snapshot.items[1].className)
        assertTrue(snapshot.items[1].isCancellation)
    }

    @Test
    fun `parseSubstitutions and matcher filter by class and group even with descriptive class names`() {
        val html = """
            <html><body>
              <h2>Zastępstwa na dzień 13.11.2026</h2>
              <table>
                <tr><th>Lekcja</th><th>Klasa</th><th>Nieobecny</th><th>Przedmiot</th><th>Zastępstwo</th><th>Sala</th></tr>
                <tr><td>3</td><td>2TI (1/2)</td><td>A. Nowak</td><td>J. angielski</td><td>M. Kowalski</td><td>102</td></tr>
                <tr><td>4</td><td>2TI (2/2)</td><td>A. Nowak</td><td>Informatyka</td><td>Odwołane - uczniowie zwolnieni</td><td>-</td></tr>
                <tr><td>5</td><td>2TIM</td><td>B. Lis</td><td>Fizyka</td><td>C. Mazur</td><td>210</td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val snapshot = ZsmeHtmlParser.parseSubstitutions(html, "https://sekretariat.zsme.pl/tv.php")
        assertTrue(snapshot.authSucceeded)
        assertEquals(3, snapshot.items.size)

        val group1Matches = snapshot.items.filter {
            ScheduleFilterMatcher.matchesSubstitution(it, "2TI Technik informatyk", StudentGroup.GROUP_1, "")
        }
        assertEquals(1, group1Matches.size)
        assertEquals("J. angielski", group1Matches[0].subject)

        val group2Matches = snapshot.items.filter {
            ScheduleFilterMatcher.matchesSubstitution(it, "2TI", StudentGroup.GROUP_2, "")
        }
        assertEquals(1, group2Matches.size)
        assertTrue(group2Matches[0].isCancellation)

        val unrelated = SubstitutionItem(id = "x", className = "2TIM", subject = "Fizyka")
        assertFalse(ScheduleFilterMatcher.matchesSubstitution(unrelated, "2TI", StudentGroup.ALL, ""))
    }

    @Test
    fun `parseSubstitutions extracts top Informacje frame and bottom Uwagi frame separately`() {
        val html = """
            <html><body>
              <h2>Zastępstwa 13.11.2026</h2>
              <div class="informacje" style="border: 1px solid #000;">
                <strong>Informacje:</strong><br>
                Skrócone lekcje do 30 minut.<br>
                Wyjście klas maturalnych o 11:00.
              </div>
              <table>
                <tr><th>Klasa</th><th>Lekcja</th><th>Nieobecny</th><th>Przedmiot</th><th>Zastępstwo</th><th>Sala</th></tr>
                <tr><td>2TI</td><td>1</td><td>J. Kowalski</td><td>Matematyka</td><td>A. Nowak</td><td>204</td></tr>
              </table>
              <div class="uwagi" style="border: 1px solid #000;">
                <strong>Uwagi:</strong><br>
                Uczniowie proszeni są o zachowanie ciszy na korytarzu podczas egzaminów.
              </div>
            </body></html>
        """.trimIndent()

        val snapshot = ZsmeHtmlParser.parseSubstitutions(html, "https://sekretariat.zsme.pl/tv.php")
        assertEquals(1, snapshot.items.size)
        assertEquals(2, snapshot.announcements.size)
        assertTrue(snapshot.announcements.any { it.contains("Skrócone lekcje") })
        assertTrue(snapshot.announcements.any { it.contains("Wyjście klas") })
        assertEquals(1, snapshot.notes.size)
        assertTrue(snapshot.notes[0].contains("zachowanie ciszy"))
    }

    @Test
    fun `parseTimetableCatalog extracts teachers and rooms from select dropdowns alongside class links`() {
        val html = """
            <html><body>
              <div id="oddzialy">
                <a href="plany/o1.html">1A</a>
                <a href="plany/o2.html">2TI</a>
              </div>
              <form>
                <select name="nauczyciele" onchange="if(this.value) parent.plan.location.href='plany/'+this.value+'.html'">
                  <option value="">-- Wybierz nauczyciela --</option>
                  <option value="n4">MK</option>
                  <option value="n9">AW</option>
                </select>
                <select name="sale" onchange="if(this.value) parent.plan.location.href=this.value">
                  <option value="">Wybierz salę</option>
                  <option value="plany/s12.html">Sala 305</option>
                  <option value="plany/s3.html">104</option>
                </select>
              </form>
            </body></html>
        """.trimIndent()

        val catalog = ZsmeHtmlParser.parseTimetableCatalog(html, "https://plan.zsme.pl/lista.html")
        assertEquals(2, catalog.classes.size)
        assertEquals(2, catalog.teachers.size)
        assertEquals(2, catalog.rooms.size)
        assertEquals("AW", catalog.teachers[0].name)
        assertEquals("https://plan.zsme.pl/plany/n9.html", catalog.teachers[0].url)
        assertEquals("MK", catalog.teachers[1].name)
        assertEquals("https://plan.zsme.pl/plany/n4.html", catalog.teachers[1].url)
        assertEquals("104", catalog.rooms[0].name)
        assertEquals("305", catalog.rooms[1].name)
        assertEquals("https://plan.zsme.pl/plany/s12.html", catalog.rooms[1].url)
    }

    @Test
    fun `parseTimetableCatalog harvests teacher and room links embedded inside class schedule page`() {
        val classPageHtml = """
            <html><body>
              <span class="tytulnapis">2TI</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00-8:45</td>
                  <td class="l">
                    <span class="p">Fizyka</span>
                    <a class="n" href="n11.html">KZ</a>
                    <a class="s" href="s8.html">212</a>
                  </td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val catalog = ZsmeHtmlParser.parseTimetableCatalog(
            classPageHtml,
            "https://plan.zsme.pl/plany/o12.html"
        )
        assertEquals(1, catalog.teachers.size)
        assertEquals("KZ", catalog.teachers[0].name)
        assertEquals("https://plan.zsme.pl/plany/n11.html", catalog.teachers[0].url)
        assertEquals(1, catalog.rooms.size)
        assertEquals("212", catalog.rooms[0].name)
        assertEquals("https://plan.zsme.pl/plany/s8.html", catalog.rooms[0].url)
    }

    @Test
    fun `parseTimetable handles 6-column and rowspan teacher tables with multiple classes`() {
        val html = """
            <html><body>
              <span class="tytulnapis">JK (Jan Kowalski)</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="l" rowspan="2"><a class="o" href="o1.html">1A</a> <a class="o" href="o2.html">1B</a> <span class="p">WF</span> <a class="s" href="s1.html">SG1</a></td>
                  <td class="l"><a class="o" href="o3.html">2TI</a> <span class="p">BHP</span> <a class="s" href="s4.html">204</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
                <tr>
                  <td class="nr">2</td>
                  <td class="l">&nbsp;</td>
                  <td class="l"><a class="o" href="o4.html">3TM</a> <span class="p">WF</span> <a class="s" href="s1.html">SG1</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val tt = ZsmeHtmlParser.parseTimetable(
            html = html,
            className = "JK",
            classUrl = "https://plan.zsme.pl/plany/n3.html",
            category = TimetableCategory.TEACHER
        )
        assertTrue(ZsmeHtmlParser.hasTimetableStructure(html))
        assertEquals(2, tt.days[0].lessons.size)
        assertEquals("1A, 1B", tt.days[0].lessons[0].classGroupLabel)
        assertEquals("1A, 1B", tt.days[0].lessons[1].classGroupLabel)
        assertEquals(1, tt.days[1].lessons.size)
        assertEquals("2TI", tt.days[1].lessons[0].classGroupLabel)
        assertEquals(1, tt.days[2].lessons.size)
        assertEquals("3TM", tt.days[2].lessons[0].classGroupLabel)
    }

    @Test
    fun `parseTimetableCatalog extracts bottom-left Sale under Oddzialy in table layout`() {
        val html = """
            <html><body>
              <table>
                <tr><td class="blk">Oddziały</td></tr>
                <tr><td>
                  <ul>
                    <li><a href="plany/o1.html" target="plan">1A</a></li>
                    <li><a href="plany/o2.html" target="plan">2TI</a></li>
                  </ul>
                </td></tr>
                <tr><td class="blk">Sale</td></tr>
                <tr><td>
                  <ul>
                    <li><a href="plany/s1.html" target="plan">101</a></li>
                    <li><a href="plany/room204.html" target="plan">204</a></li>
                    <li><a href="plany/sg.html" target="plan">SG1</a></li>
                  </ul>
                </td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val catalog = ZsmeHtmlParser.parseTimetableCatalog(html, "https://plan.zsme.pl/lista.html")
        assertEquals(2, catalog.classes.size)
        assertEquals(3, catalog.rooms.size)
        assertEquals(listOf("101", "204", "SG1"), catalog.rooms.map { it.name })
    }

    @Test
    fun `parseTimetable extracts classes and teachers in room schedule even when a href lacks css class o`() {
        val html = """
            <html><body>
              <span class="tytulnapis">Sala 204</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00-8:45</td>
                  <td class="l"><a href="o12.html">2TI</a> <a href="n3.html">JK</a> <span class="p">Matematyka</span></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val tt = ZsmeHtmlParser.parseTimetable(
            html = html,
            className = "204",
            classUrl = "https://plan.zsme.pl/plany/s5.html",
            category = TimetableCategory.ROOM
        )
        val lesson = tt.days[0].lessons[0]
        assertEquals("2TI", lesson.classGroupLabel)
        assertEquals("JK", lesson.teacher)
        assertEquals("Matematyka", lesson.subject)
    }

    @Test
    fun `teacher substitution matcher filters by abbreviation or surname without false positives`() {
        val conductingByName = SubstitutionItem(
            id = "s1",
            period = "2",
            className = "2TI",
            absentTeacher = "A. Nowak",
            subject = "Matematyka",
            substituteTeacher = "Jan Kowalski",
            room = "204"
        )
        val replacedByAbbr = SubstitutionItem(
            id = "s2",
            period = "4",
            className = "1A",
            absentTeacher = "JK",
            subject = "Fizyka",
            substituteTeacher = "AB",
            room = "108"
        )
        val dottedAbbr = SubstitutionItem(
            id = "s3",
            period = "5",
            className = "3TM",
            absentTeacher = "J. K.",
            subject = "Informatyka",
            substituteTeacher = "Odwołane",
            isCancellation = true
        )
        val unrelated = SubstitutionItem(
            id = "s4",
            period = "6",
            className = "2TE",
            absentTeacher = "M. Kaczmarek",
            subject = "Język polski",
            substituteTeacher = "AJK"
        )

        assertEquals(
            ScheduleFilterMatcher.TeacherSubstitutionMatch.CONDUCTING,
            ScheduleFilterMatcher.classifyTeacherSubstitution(conductingByName, "JK", "Kowalski")
        )
        assertEquals(
            ScheduleFilterMatcher.TeacherSubstitutionMatch.REPLACED,
            ScheduleFilterMatcher.classifyTeacherSubstitution(replacedByAbbr, "JK", "Kowalski")
        )
        assertEquals(
            ScheduleFilterMatcher.TeacherSubstitutionMatch.REPLACED,
            ScheduleFilterMatcher.classifyTeacherSubstitution(dottedAbbr, "JK", "Kowalski")
        )
        assertFalse(ScheduleFilterMatcher.matchesTeacherSubstitution(unrelated, "JK", "Kowalski"))
    }

    @Test
    fun `aggregateTeacherScheduleFromTimetables searches all class plans and merges teacher lessons`() {
        val html1A = """
            <html><body>
              <span class="tytulnapis">1A</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00-8:45</td>
                  <td class="l"><span class="p">WF</span> <a class="n">JK</a> <a class="s">SG1</a></td>
                  <td class="l"><span class="p">Matematyka</span> <a class="n">AB</a> <a class="s">201</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val html1B = """
            <html><body>
              <span class="tytulnapis">1B</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00-8:45</td>
                  <td class="l"><span class="p">WF</span> <a class="n">JK</a> <a class="s">SG1</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
                <tr>
                  <td class="nr">2</td>
                  <td class="g">8:50-9:35</td>
                  <td class="l">&nbsp;</td>
                  <td class="l"><span class="p">BHP</span> <a class="n">JK</a> <a class="s">105</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val plan1A = ZsmeHtmlParser.parseTimetable(html1A, "1A", "https://plan.zsme.pl/plany/o1.html")
        val plan1B = ZsmeHtmlParser.parseTimetable(html1B, "1B", "https://plan.zsme.pl/plany/o2.html")

        val teacherPlan = ZsmeHtmlParser.aggregateTeacherScheduleFromTimetables(
            teacherAbbr = "JK",
            sourceTimetables = listOf(plan1A, plan1B),
            sourceUrl = "https://plan.zsme.pl/"
        )

        assertEquals(TimetableCategory.TEACHER, teacherPlan.category)
        assertEquals("JK", teacherPlan.className)
        assertEquals(1, teacherPlan.days[0].lessons.size)
        assertEquals("WF", teacherPlan.days[0].lessons[0].subject)
        assertEquals("1A, 1B", teacherPlan.days[0].lessons[0].classGroupLabel)
        assertEquals("SG1", teacherPlan.days[0].lessons[0].room)

        assertEquals(1, teacherPlan.days[1].lessons.size)
        assertEquals("BHP", teacherPlan.days[1].lessons[0].subject)
        assertEquals("1B", teacherPlan.days[1].lessons[0].classGroupLabel)
        assertEquals("105", teacherPlan.days[1].lessons[0].room)
    }

    @Test
    fun `extractTeacherAbbreviationsFromTimetables collects unique sorted teacher abbreviations across all plans`() {
        val html1A = """
            <html><body>
              <span class="tytulnapis">1A</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00-8:45</td>
                  <td class="l"><span class="p">WF</span> <span class="n">JK</span> <span class="s">SG1</span></td>
                  <td class="l"><span class="p">Matematyka</span> <span class="n">AB</span> <span class="s">201</span></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val html2TI = """
            <html><body>
              <span class="tytulnapis">2TI</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00-8:45</td>
                  <td class="l"><span class="p">J. angielski</span> <span class="n">MK, jk</span> <span class="s">102</span></td>
                  <td class="l"><span class="p">Fizyka</span> <span class="n">KZ</span> <span class="s">210</span></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val plan1A = ZsmeHtmlParser.parseTimetable(html1A, "1A", "https://plan.zsme.pl/plany/o1.html")
        val plan2TI = ZsmeHtmlParser.parseTimetable(html2TI, "2TI", "https://plan.zsme.pl/plany/o2.html")

        val abbrs = ZsmeHtmlParser.extractTeacherAbbreviationsFromTimetables(listOf(plan1A, plan2TI))
        assertEquals(listOf("AB", "JK", "KZ", "MK"), abbrs)
    }

    @Test
    fun `parseAppRepositoryApks extracts APK files with versions and modification dates`() {
        assertEquals("https://sekretariat.zsme.pl/app", UserPreferences.DEFAULT_APP_UPDATE_URL)
        val html = """
            <html><body>
              <h1>Index of /app</h1>
              <table>
                <tr><td><a href="../">Parent Directory</a></td><td>-</td></tr>
                <tr><td><a href="zsme-plan-1.0.apk">zsme-plan-1.0.apk</a></td><td align="right">2026-10-01 09:15</td></tr>
                <tr><td><a href="zsme-plan-v1.2.4.apk">zsme-plan-v1.2.4.apk</a></td><td align="right">2026-11-12 14:30</td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val candidates = ZsmeHtmlParser.parseAppRepositoryApks(html, "https://sekretariat.zsme.pl/app/")
        assertEquals(2, candidates.size)
        assertEquals("zsme-plan-v1.2.4.apk", candidates[0].fileName)
        assertEquals("1.2.4", candidates[0].versionName)
        assertEquals("https://sekretariat.zsme.pl/app/zsme-plan-v1.2.4.apk", candidates[0].downloadUrl)
        assertTrue(candidates[0].directoryDateText.contains("2026-11-12"))

        assertTrue(com.example.data.model.AppVersionHelper.compareVersions("1.2.4", "1.0") > 0)
        assertEquals(0, com.example.data.model.AppVersionHelper.compareVersions("v1.0.0", "1.0"))
    }

    @Test
    fun `allTeacherSchools and mergeMultiSchoolTeacherTimetables combine schedules with distinct school colors and chronological sorting`() {
        val prefs = UserPreferences(
            teacherAbbreviation = "jk",
            planUrl = "https://plan.zsme.pl/",
            additionalSchools = listOf(
                com.example.data.model.TeacherSchoolConfig(
                    id = "school_zst",
                    schoolName = "ZST",
                    teacherAbbreviation = "kow",
                    planUrl = "https://plan.zst.pl/",
                    colorIndex = 1
                )
            )
        )

        val schools = prefs.allTeacherSchools()
        assertEquals(2, schools.size)
        assertEquals("ZSME", schools[0].schoolName)
        assertEquals("JK", schools[0].teacherAbbreviation)
        assertEquals(0, schools[0].colorIndex)
        assertEquals("ZST", schools[1].schoolName)
        assertEquals("KOW", schools[1].teacherAbbreviation)
        assertEquals(1, schools[1].colorIndex)

        val zsmeHtml = """
            <html><body>
              <span class="tytulnapis">2TI</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00-8:45</td>
                  <td class="l"><span class="p">Fizyka</span> <a class="n">JK</a> <a class="s">204</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val zstHtml = """
            <html><body>
              <span class="tytulnapis">3AT</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">3</td>
                  <td class="g">9:50-10:35</td>
                  <td class="l"><span class="p">Elektrotechnika</span> <a class="n">KOW</a> <a class="s">15</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
                <tr>
                  <td class="nr">0</td>
                  <td class="g">7:10-7:55</td>
                  <td class="l"><span class="p">BHP</span> <a class="n">KOW</a> <a class="s">12</a></td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val zsmePlan = ZsmeHtmlParser.aggregateTeacherScheduleFromTimetables(
            teacherAbbr = schools[0].teacherAbbreviation,
            sourceTimetables = listOf(ZsmeHtmlParser.parseTimetable(zsmeHtml, "2TI", "https://plan.zsme.pl/plany/o1.html")),
            sourceUrl = schools[0].planUrl
        )
        val zstPlan = ZsmeHtmlParser.aggregateTeacherScheduleFromTimetables(
            teacherAbbr = schools[1].teacherAbbreviation,
            sourceTimetables = listOf(ZsmeHtmlParser.parseTimetable(zstHtml, "3AT", "https://plan.zst.pl/plany/o2.html")),
            sourceUrl = schools[1].planUrl
        )

        val merged = ZsmeHtmlParser.mergeMultiSchoolTeacherTimetables(
            listOf(schools[0] to zsmePlan, schools[1] to zstPlan)
        )

        assertEquals(TimetableCategory.TEACHER, merged.category)
        val monday = merged.days[0].lessons
        assertEquals(3, monday.size)
        // Sorted chronologically: 7:10 (ZST), 8:00 (ZSME), 9:50 (ZST)
        assertEquals("BHP", monday[0].subject)
        assertEquals("ZST", monday[0].schoolName)
        assertEquals(1, monday[0].schoolColorIndex)

        assertEquals("Fizyka", monday[1].subject)
        assertEquals("ZSME", monday[1].schoolName)
        assertEquals(0, monday[1].schoolColorIndex)

        assertEquals("Elektrotechnika", monday[2].subject)
        assertEquals("ZST", monday[2].schoolName)
        assertEquals(1, monday[2].schoolColorIndex)
    }

    @Test
    fun `parseTimetableCatalog and aggregateTeacherSchedule support JS redirects, numeric selects, and flexible teacher cells`() {
        val redirectHtml = """
            <html><head><script>window.location.href = 'plan/lista.html';</script></head><body></body></html>
        """.trimIndent()
        val frames = ZsmeHtmlParser.extractFrameUrls(redirectHtml, "https://szkola2.pl/")
        assertTrue(frames.contains("https://szkola2.pl/plan/lista.html"))

        val listHtml = """
            <html><body>
              <select name="oddzialy" onchange="location.href='plany/o'+this.value+'.html'">
                <option value="">-- Wybierz --</option>
                <option value="12">2TB</option>
              </select>
              <a href="plany/1a.html">1A</a>
            </body></html>
        """.trimIndent()
        val catalog = ZsmeHtmlParser.parseTimetableCatalog(listHtml, "https://szkola2.pl/plan/lista.html")
        assertEquals(2, catalog.classes.size)
        assertTrue(catalog.classes.any { it.name == "1A" && it.url == "https://szkola2.pl/plan/plany/1a.html" })
        assertTrue(catalog.classes.any { it.name == "2TB" && it.url == "https://szkola2.pl/plan/plany/o12.html" })

        val classHtml = """
            <html><body>
              <span class="tytulnapis">2TB</span>
              <table class="tabela">
                <tr><th>Nr</th><th>Godz</th><th>Poniedziałek</th><th>Wtorek</th><th>Środa</th><th>Czwartek</th><th>Piątek</th></tr>
                <tr>
                  <td class="nr">1</td>
                  <td class="g">8:00-8:45</td>
                  <td class="l"><span class="p">Elektronika</span> <span class="n">Jan Kowalski (JK)</span> <span class="s">301</span></td>
                  <td class="l">Matematyka JK s.104</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                  <td class="l">&nbsp;</td>
                </tr>
              </table>
            </body></html>
        """.trimIndent()

        val plan2TB = ZsmeHtmlParser.parseTimetable(classHtml, "2TB", "https://szkola2.pl/plan/plany/o12.html")
        val teacherPlan = ZsmeHtmlParser.aggregateTeacherScheduleFromTimetables(
            teacherAbbr = "JK",
            sourceTimetables = listOf(plan2TB),
            sourceUrl = "https://szkola2.pl/plan/"
        )
        assertEquals(1, teacherPlan.days[0].lessons.size)
        assertEquals("Elektronika", teacherPlan.days[0].lessons[0].subject)
        assertEquals(1, teacherPlan.days[1].lessons.size)
        assertEquals("2TB", teacherPlan.days[1].lessons[0].classGroupLabel)
    }

    @Test
    fun `subfolder timetable URLs stay inside subfolder even with root base tag and CMS links`() {
        val subfolderUrl = "http://zs5rybnik.pl/_plan/"
        val framesetHtml = """
            <html>
              <head><base href="/" /></head>
              <frameset cols="200,*">
                <frame src="lista.html" name="list" />
                <frame src="plany/o1.html" name="plan" />
              </frameset>
            </html>
        """.trimIndent()

        val frames = ZsmeHtmlParser.extractFrameUrls(framesetHtml, subfolderUrl)
        assertTrue(frames.contains("http://zs5rybnik.pl/_plan/lista.html"))
        assertTrue(frames.contains("http://zs5rybnik.pl/_plan/plany/o1.html"))
        assertFalse(frames.contains("http://zs5rybnik.pl/lista.html"))

        val listHtml = """
            <html>
              <head><base href="http://zs5rybnik.pl/" /></head>
              <body>
                <a href="plany/">plany/</a>
                <a href="plany/o1.html">1A</a>
                <a href="plany/n2.html">JK</a>
                <a href="/nauczyciele">Kadra</a>
              </body>
            </html>
        """.trimIndent()

        val catalog = ZsmeHtmlParser.parseTimetableCatalog(listHtml, "http://zs5rybnik.pl/_plan/lista.html")
        assertEquals(listOf("http://zs5rybnik.pl/_plan/plany/o1.html"), catalog.classes.map { it.url })
        assertEquals(listOf("http://zs5rybnik.pl/_plan/plany/n2.html"), catalog.teachers.map { it.url })

        val subpages = ZsmeHtmlParser.extractCatalogSubpageUrls(listHtml, "http://zs5rybnik.pl/_plan/lista.html")
        assertTrue(subpages.contains("http://zs5rybnik.pl/_plan/plany/"))

        val client = com.example.data.network.ZsmePortalClient()
        val prefix = client.extractSubfolderPrefix("http://zs5rybnik.pl/_plan")
        assertEquals("/_plan/", prefix)
        assertTrue(client.isWithinSubfolderScope("http://zs5rybnik.pl/_plan/lista.html", "zs5rybnik.pl", prefix))
        assertTrue(client.isWithinSubfolderScope("http://www.zs5rybnik.pl/_plan", "zs5rybnik.pl", prefix))
        assertTrue(client.isWithinSubfolderScope("https://zs5rybnik.pl/_plan/plany/o1.html", "zs5rybnik.pl", prefix))
        assertFalse(client.isWithinSubfolderScope("http://zs5rybnik.pl/nauczyciele", "zs5rybnik.pl", prefix))
        assertFalse(client.isWithinSubfolderScope("http://zs5rybnik.pl/", "zs5rybnik.pl", prefix))
    }

    @Test
    fun `filterLessonsByVisibleSchools hides and restores lessons per selected school`() {
        val lessons = listOf(
            com.example.data.model.LessonEntry(
                period = 1,
                timeRange = "8:00-8:45",
                subject = "Fizyka",
                teacher = "JK",
                room = "204",
                schoolId = UserPreferences.PRIMARY_SCHOOL_ID,
                schoolName = "ZSME",
                schoolColorIndex = 0
            ),
            com.example.data.model.LessonEntry(
                period = 2,
                timeRange = "8:50-9:35",
                subject = "Elektrotechnika",
                teacher = "JK",
                room = "15",
                schoolId = "school_zs5",
                schoolName = "ZS5",
                schoolColorIndex = 1
            ),
            com.example.data.model.LessonEntry(
                period = 3,
                timeRange = "9:50-10:35",
                subject = "BHP",
                teacher = "JK",
                room = "102",
                schoolId = "",
                schoolName = "ZSME",
                schoolColorIndex = 0
            )
        )

        assertEquals(3, ScheduleFilterMatcher.filterLessonsByVisibleSchools(lessons, emptySet()).size)

        val onlyZs5 = ScheduleFilterMatcher.filterLessonsByVisibleSchools(
            lessons,
            setOf(UserPreferences.PRIMARY_SCHOOL_ID)
        )
        assertEquals(listOf("Elektrotechnika"), onlyZs5.map { it.subject })

        val onlyZsme = ScheduleFilterMatcher.filterLessonsByVisibleSchools(
            lessons,
            setOf("school_zs5")
        )
        assertEquals(listOf("Fizyka", "BHP"), onlyZsme.map { it.subject })

        val noneVisible = ScheduleFilterMatcher.filterLessonsByVisibleSchools(
            lessons,
            setOf(UserPreferences.PRIMARY_SCHOOL_ID, "school_zs5")
        )
        assertTrue(noneVisible.isEmpty())
    }

    @Test
    fun `isValidTeacherAccessPassword validates exact teacher password`() {
        assertTrue(UserPreferences.isValidTeacherAccessPassword("zsmeedukacja"))
        assertTrue(UserPreferences.isValidTeacherAccessPassword("  zsmeedukacja  "))
        assertFalse(UserPreferences.isValidTeacherAccessPassword(""))
        assertFalse(UserPreferences.isValidTeacherAccessPassword("zsme"))
        assertFalse(UserPreferences.isValidTeacherAccessPassword("ZSMEEDUKACJA"))
    }

    @Test
    fun `parseSubstitutionPeriods and resolveWeekdayIndex handle ranges, groups, and Polish dates`() {
        assertEquals(setOf(1, 2), ScheduleFilterMatcher.parseSubstitutionPeriods("1-2"))
        assertEquals(setOf(6, 7, 8), ScheduleFilterMatcher.parseSubstitutionPeriods("6–8"))
        assertEquals(setOf(1, 2), ScheduleFilterMatcher.parseSubstitutionPeriods("1, 2 (1/2)"))
        assertEquals(setOf(3), ScheduleFilterMatcher.parseSubstitutionPeriods("3 (gr. 2)"))
        assertEquals(emptySet<Int>(), ScheduleFilterMatcher.parseSubstitutionPeriods(""))

        // 13.11.2026 is Friday (dayIndex = 4), 16.11.2026 is Monday (dayIndex = 0)
        assertEquals(4, ScheduleFilterMatcher.resolveWeekdayIndex("Zastępstwa 13.11.2026"))
        assertEquals(0, ScheduleFilterMatcher.resolveWeekdayIndex("Plan zastępstw 16.11.2026"))
        assertEquals(1, ScheduleFilterMatcher.resolveWeekdayIndex("Zastępstwa na wtorek"))
    }

    @Test
    fun `resolveClassDaySubstitutions crosses out late arrival and early finish lessons while keeping teacher replacements active`() {
        val lessons = listOf(
            com.example.data.model.LessonEntry(1, "8:00-8:45", "Fizyka", "JK", "204"),
            com.example.data.model.LessonEntry(2, "8:50-9:35", "Matematyka", "AB", "105"),
            com.example.data.model.LessonEntry(3, "9:50-10:35", "Informatyka", "MK", "301"),
            com.example.data.model.LessonEntry(4, "10:45-11:30", "J. polski", "EW", "112"),
            com.example.data.model.LessonEntry(5, "11:45-12:30", "Historia", "KZ", "108")
        )

        val html = """
            <html><body>
              <h2>Zastępstwa na piątek 13.11.2026</h2>
              <table>
                <tr><th>Lekcja</th><th>Klasa</th><th>Nieobecny</th><th>Przedmiot</th><th>Zastępstwo</th><th>Sala</th></tr>
                <tr><td>1</td><td>2TI</td><td>Jan Kowalski</td><td>Fizyka</td><td>Klasa przychodzi później - uczniowie zwolnieni</td><td>-</td></tr>
                <tr><td>3</td><td>2TI</td><td>M. Kaczmarek</td><td>Informatyka</td><td>Anna Nowak</td><td>302</td></tr>
                <tr><td>5</td><td>2TI</td><td>K. Zieliński</td><td>Historia</td><td>Klasa kończy szybciej</td><td>-</td></tr>
              </table>
            </body></html>
        """.trimIndent()

        val snapshot = ZsmeHtmlParser.parseSubstitutions(html, "https://sekretariat.zsme.pl/tv.php")
        assertEquals(3, snapshot.items.size)
        assertTrue(snapshot.items[0].isCancellation)
        assertFalse(snapshot.items[1].isCancellation)
        assertTrue(snapshot.items[2].isCancellation)

        val resolution = ScheduleFilterMatcher.resolveClassDaySubstitutions(
            dayLessons = lessons,
            dayIndex = 4, // Friday
            todayWeekdayIndex = 0, // Even if today is Monday, snapshot matches Friday
            snapshot = snapshot,
            className = "2TI",
            selectedGroup = StudentGroup.ALL,
            customGroupFilter = ""
        )

        assertEquals(2, resolution.cancelledCount)
        assertEquals(1, resolution.activeSubCount)
        assertTrue(resolution.matchByLessonIndex[0]?.isCancellation == true)
        assertEquals(null, resolution.matchByLessonIndex[1])
        assertTrue(resolution.matchByLessonIndex[2]?.isCancellation == false)
        assertEquals("Anna Nowak", resolution.matchByLessonIndex[2]?.substituteTeacher)
        assertEquals(null, resolution.matchByLessonIndex[3])
        assertTrue(resolution.matchByLessonIndex[4]?.isCancellation == true)

        val banner = resolution.summaryBanner.orEmpty()
        assertTrue(banner.contains("przychodzi później", ignoreCase = true))
        assertTrue(banner.contains("kończy szybciej", ignoreCase = true))
    }

    @Test
    fun `resolveClassDaySubstitutions handles arrival and finish boundaries from periods, ranges, and announcements`() {
        val lessons = listOf(
            com.example.data.model.LessonEntry(1, "8:00-8:45", "WF", "JK", "SG1"),
            com.example.data.model.LessonEntry(2, "8:50-9:35", "Matematyka", "AB", "105"),
            com.example.data.model.LessonEntry(3, "9:50-10:35", "Informatyka", "MK", "301"),
            com.example.data.model.LessonEntry(4, "10:45-11:30", "J. polski", "EW", "112"),
            com.example.data.model.LessonEntry(5, "11:45-12:30", "Angielski", "KZ", "108"),
            com.example.data.model.LessonEntry(6, "12:40-13:25", "Chemia", "MN", "210")
        )

        val snapshot = com.example.data.model.SubstitutionsSnapshot(
            dateHeading = "Zastępstwa na wtorek",
            items = listOf(
                SubstitutionItem(
                    id = "s1",
                    className = "2TI",
                    period = "3",
                    absentTeacher = "JK",
                    note = "Klasa przychodzi na 3 lekcję",
                    isCancellation = true
                )
            ),
            announcements = listOf(
                "Klasa 2TI kończy po 4 lekcji z powodu nieobecności nauczycieli."
            )
        )

        val resolution = ScheduleFilterMatcher.resolveClassDaySubstitutions(
            dayLessons = lessons,
            dayIndex = 1, // Tuesday
            todayWeekdayIndex = 1,
            snapshot = snapshot,
            className = "2TI",
            selectedGroup = StudentGroup.ALL,
            customGroupFilter = ""
        )

        // Lessons 1 and 2 cancelled (< 3); lesson 3 stays active; lessons 5 and 6 cancelled (> 4)
        assertTrue(resolution.matchByLessonIndex[0]?.isCancellation == true)
        assertTrue(resolution.matchByLessonIndex[1]?.isCancellation == true)
        assertEquals(null, resolution.matchByLessonIndex[2])
        assertEquals(null, resolution.matchByLessonIndex[3])
        assertTrue(resolution.matchByLessonIndex[4]?.isCancellation == true)
        assertTrue(resolution.matchByLessonIndex[5]?.isCancellation == true)
        assertEquals(4, resolution.cancelledCount)
    }

    @Test
    fun `resolveClassDaySubstitutions disambiguates multiple groups in same period using groupTag and absentTeacher`() {
        val lessons = listOf(
            com.example.data.model.LessonEntry(1, "8:00-8:45", "J. angielski", "AB", "102", groupTag = "1/2"),
            com.example.data.model.LessonEntry(1, "8:00-8:45", "J. angielski", "CD", "105", groupTag = "2/2"),
            com.example.data.model.LessonEntry(2, "8:50-9:35", "Matematyka", "JK", "204")
        )

        val snapshot = com.example.data.model.SubstitutionsSnapshot(
            dateHeading = "Zastępstwa",
            items = listOf(
                SubstitutionItem(
                    id = "g1",
                    className = "2TI (1/2)",
                    period = "1",
                    absentTeacher = "AB",
                    substituteTeacher = "Odwołane",
                    groupTag = "1/2",
                    isCancellation = true
                )
            )
        )

        val resolution = ScheduleFilterMatcher.resolveClassDaySubstitutions(
            dayLessons = lessons,
            dayIndex = 0,
            todayWeekdayIndex = 0,
            snapshot = snapshot,
            className = "2TI",
            selectedGroup = StudentGroup.ALL,
            customGroupFilter = ""
        )

        assertTrue(resolution.matchByLessonIndex[0]?.isCancellation == true)
        assertEquals(null, resolution.matchByLessonIndex[1])
        assertEquals(null, resolution.matchByLessonIndex[2])
    }

    @Test
    fun `GitHub APK URL and Releases API parsing extract version and download candidate`() {
        val legacyV106Url = "https://github.com/dniesobski/ZSME-Assistant/releases/download/v1.0.6/ZSMEAssist.apk"
        val latestReleasesUrl = "https://github.com/dniesobski/ZSME-Assistant/releases/latest"
        assertEquals(legacyV106Url, UserPreferences.LEGACY_GITHUB_V106_URL)
        assertEquals(latestReleasesUrl, UserPreferences.DEFAULT_GITHUB_RELEASES_URL)
        assertEquals(latestReleasesUrl, UserPreferences().appUpdateUrl)
        assertEquals(
            "1.0.6",
            com.example.data.model.AppVersionHelper.extractVersionFromText(legacyV106Url)
        )
        assertTrue(com.example.data.model.AppVersionHelper.compareVersions("1.0.6", "1.0") > 0)

        val client = com.example.data.network.ZsmePortalClient()
        assertEquals(
            "https://api.github.com/repos/dniesobski/ZSME-Assistant/releases/latest",
            client.resolveGitHubReleaseApiUrl(latestReleasesUrl)
        )
        assertEquals(
            "https://api.github.com/repos/dniesobski/ZSME-Assistant/releases/tags/v1.0.6",
            client.resolveGitHubReleaseApiUrl("https://github.com/dniesobski/ZSME-Assistant/releases/tag/v1.0.6")
        )
        assertEquals(null, client.resolveGitHubReleaseApiUrl("https://sekretariat.zsme.pl/app"))

        val latestCiJson = """
            {
              "tag_name": "v1.0.12",
              "name": "Aktualizacja #12",
              "published_at": "2026-11-16T10:00:00Z",
              "assets": [
                {
                  "name": "ZSMEAssist.apk",
                  "size": 12456789,
                  "browser_download_url": "https://github.com/dniesobski/ZSME-Assistant/releases/download/v1.0.12/ZSMEAssist.apk"
                }
              ]
            }
        """.trimIndent()
        val parsedCi = client.parseGitHubReleaseJson(latestCiJson)
        assertNotNull(parsedCi)
        assertEquals("ZSMEAssist.apk", parsedCi!!.fileName)
        assertEquals("1.0.12", parsedCi.versionName)
        assertTrue(com.example.data.model.AppVersionHelper.compareVersions(parsedCi.versionName, "1.0") > 0)
        assertTrue(com.example.data.model.AppVersionHelper.compareVersions(parsedCi.versionName, "1.0.6") > 0)

        val releaseJson = """
            {
              "tag_name": "v1.0.6",
              "name": "ZSME Assistant v1.0.6",
              "published_at": "2026-11-16T08:00:00Z",
              "assets": [
                {
                  "name": "ZSMEAssist.apk",
                  "size": 12345678,
                  "browser_download_url": "$legacyV106Url"
                }
              ]
            }
        """.trimIndent()
        val parsed = client.parseGitHubReleaseJson(releaseJson)
        assertNotNull(parsed)
        assertEquals("ZSMEAssist.apk", parsed!!.fileName)
        assertEquals("1.0.6", parsed.versionName)
        assertEquals(legacyV106Url, parsed.downloadUrl)
        assertEquals(12345678L, parsed.contentLength)
        assertTrue(parsed.lastModifiedMillis > 0L)

        val fragmentHtml = """
            <ul>
              <li><a href="/dniesobski/ZSME-Assistant/releases/download/v1.0.6/ZSMEAssist.apk">ZSMEAssist.apk</a></li>
            </ul>
        """.trimIndent()
        val htmlCandidates = ZsmeHtmlParser.parseAppRepositoryApks(fragmentHtml, latestReleasesUrl)
        assertEquals(1, htmlCandidates.size)
        assertEquals("1.0.6", htmlCandidates.first().versionName)
        assertEquals(legacyV106Url, htmlCandidates.first().downloadUrl)
    }
}
