package common.ui.widgets

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Every `Material3Scaffold` call site must consume the inset padding the scaffold hands it.
 *
 * Dropping that parameter lets the content start *behind* the pinned app bar, so its first row is
 * clipped under the toolbar. The same miss shipped three times: the live wallpaper config screen
 * (fixed 3.4.1), the about page (fixed 3.5.12), and finally the alert and allergen pages (fixed
 * 3.6.10) — each time on a page nobody could see without the right data. There is no Compose UI
 * test infrastructure here, and a pixel assertion would not survive a layout tweak anyway, so this
 * pins the *shape of the call*: the padding must be named and used.
 *
 * The call's closing `)` is found by indentation, not by the first `) {` that comes along — a
 * `topBar` full of nested `IconButton(onClick = { … }) {` produces plenty of those.
 */
class ScaffoldInsetsTest {

    @Test
    fun everyScaffoldConsumesItsInsetPadding() {
        val sources = sourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains(CALL) }
            .toList()

        val offenders = mutableListOf<String>()
        var sites = 0

        for (file in sources) {
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (!line.contains(CALL) || line.contains("fun $CALL")) {
                    return@forEachIndexed
                }
                sites++
                val indent = line.takeWhile { it == ' ' }
                val closer = Regex("""^$indent\)\s*\{\s*(?:(\w+)\s*->)?\s*$""")
                val opener = (index until minOf(index + LOOKAHEAD, lines.size))
                    .firstOrNull { closer.matches(lines[it]) }
                if (opener == null) {
                    offenders += "${file.name}:${index + 1} — no lambda opener within $LOOKAHEAD lines"
                    return@forEachIndexed
                }
                val name = closer.find(lines[opener])!!.groupValues[1]
                if (name.isEmpty()) {
                    offenders += "${file.name}:${opener + 1} — `) {` drops the inset padding"
                    return@forEachIndexed
                }
                val applied = (opener + 1 until minOf(opener + LOOKAHEAD, lines.size))
                    .any { lines[it].contains(name) }
                if (!applied) {
                    offenders += "${file.name}:${opener + 1} — names `$name` but never applies it"
                }
            }
        }

        // Anti-vacuum: a rename that makes the scan find nothing must fail, not pass quietly.
        assertTrue("only $sites $CALL call sites found — the scan lost its target", sites >= 4)

        if (offenders.isNotEmpty()) {
            fail("Material3Scaffold sites ignoring their inset padding:\n" + offenders.joinToString("\n"))
        }
    }

    private fun sourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (candidate in listOf("src/main/java", "app/src/main/java")) {
                val root = File(dir, candidate)
                if (root.isDirectory) {
                    return root
                }
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate src/main/java from ${File(".").absolutePath}")
    }

    companion object {
        private const val CALL = "Material3Scaffold("
        private const val LOOKAHEAD = 60
    }
}
