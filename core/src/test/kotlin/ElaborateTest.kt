package koto.core

import koto.core.util.Severity
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.readText
import kotlin.test.assertTrue

class ElaborateTest {
    private fun test(path: Path) {
        val text = path.readText()
        val parseResult = parse(text)
        assertTrue(parseResult.diagnostics.none { it.severity == Severity.ERROR })
        parseResult.diagnostics.forEach { println(it) }
        val elaborateResult = elaborate(parseResult)
        assertTrue(elaborateResult.diagnostics.none { it.severity == Severity.ERROR })
        elaborateResult.diagnostics.forEach { println(it) }
    }

    @TestFactory
    fun dynamicTestsFromCollection(): List<DynamicTest> {
        val files = mutableListOf<Path>()
        Path("").absolute().resolveSibling("examples").forEachDirectoryEntry("*.ヿ") {
            files.add(it)
        }
        return files.map { path ->
            dynamicTest("Elaborate ${path.fileName}") {
                test(path)
            }
        }
    }
}
