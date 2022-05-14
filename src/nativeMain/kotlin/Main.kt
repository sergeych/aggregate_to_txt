import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.datetime.Instant
import net.sergeych.mptools.toDumpLines
import net.sergeych.sprintf.sprintf
import okio.ByteString
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

val textExtensions = setOf(
    "c", "cc", "c++", "cpp", "cxx", "h", "hpp", "h++", "hxx",
    "txt", "md",
    "sh",
    "bat", "cmd",
    "java", "properties",
    "kt", "kts",
    "js", "ts", "json", "css", "html", "npmrc",
    "sql",
    "yml", "yaml", "conf", "xml",
    "dockerignore", "gitattribute",
    "gitattributes", "gitignore",
    "plist",
)

val textNames = setOf(
    "readme",
    "Vagrantfile",
    "Makefile"
)


class DirAggregator : CliktCommand(
    help = """
        | Create a text file containing all files from the root directory providing their names, dates
        | amd hashes. Text files are included as is, binary as base64 or dump. Thr resulting text files
        | is both human-readable (especially without base64) and allow to resotre source files tree from
        | it.
        | 
        | Resulting file is copied to stdout to comply with best unix practices.
        | 
        | For issues/ideas visit project's github: https://github.com/sergeych/aggregate_to_txt
    """.trimMargin()
) {
    val dry by option(help = "dry run")
        .flag(default = false)

    val base64 by option(help = "Use base64 for binary files, not the dump")
        .flag(default = false)

    val root by argument()

    val binaryExtsFound = mutableSetOf<String>()

    fun isBinary(x: Path): Boolean {
        val name = x.name
        if (name in textNames) return false
        val parts = name.split('.')
        if (parts.size == 1) {
            // TODO: check files without extensions more thoroughly
            return false
        }
        if (parts.size > 1) {
            val ext = parts.last()
            if (ext in textExtensions)
                return false
            binaryExtsFound.add(ext)
        }
        return true
    }


    override fun run() {
        try {
            scan(root)
            if (dry) {
                if (binaryExtsFound.isNotEmpty())
                    println("Following unknown extensions are treated as binary:\n" +
                            binaryExtsFound.joinToString(", ") { ".$it" })
                else
                    println("Np binary files found")
            }
        } catch (x: FileNotFoundException) {
            println("Error: (root?) file not found")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scan(root: String) {
        for (x: Path in FileSystem.SYSTEM.listRecursively(root.toPath(), false)) {
            if (FileSystem.SYSTEM.metadata(x).isDirectory) {
                if (dry) {
                    println("Dir    $x")
                }
            } else {
                if (dry) {
                    val b = if (isBinary(x)) "B" else "T"
                    println("File $b $x")
                } else {
                    if (isBinary(x))
                        processBinary(x)
                    else {
                        try {
                            processText(x)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            processBinary(x)
                        }
                    }
                }
            }
        }

    }

    private fun printHeder(path: Path, isBinary: Boolean, data: ByteString) {
        if (isBinary)
            println("--- Двоичный файл: $path")
        else
            println("--- Текстовый файл: $path")
        FileSystem.SYSTEM.metadata(path).lastAccessedAtMillis?.let { t ->
            println("--- Дата модификации: %tO".sprintf(Instant.fromEpochMilliseconds(t)))
        }
        println("--- SHA256: ${data.sha256().hex()}")
    }

    private fun processText(x: Path) {
        val data = FileSystem.SYSTEM.read(x) { readByteString() }
        val str = data.utf8()
        printHeder(x, false, data)
        println("--- начало файла, всего строк ${str.lines().size} ---\n$str\n--- конец файла ---\n ")
    }

    private fun processBinary(x: Path) {
        val data: ByteString = FileSystem.SYSTEM.read(x) { readByteString() }
        printHeder(x, true, data)
        val dump: List<String>
        val name: String
        if (base64) {
            dump = data.base64().chunked(80)
            name = "base64 закодированного base64 файла"
        } else {
            dump = data.toByteArray().toDumpLines()
            name = "начало дампа"
        }
        println("--- $name, всего строк ${dump.size} ---\n${dump.joinToString("\n")}\n--- конец дампа ---\n")
    }


}

fun main(args: Array<String>) = DirAggregator().main(args)