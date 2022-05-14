import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.cinterop.refTo
import kotlinx.datetime.Instant
import net.sergeych.mptools.toDumpLines
import net.sergeych.sprintf.sprintf
import okio.ByteString
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.*

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

interface Strings {
    val textFile: String get() = "Text file"
    val binaryFile: String get() = "Binary file"
    val fileTime: String get() = "Last modified"
    val textFileStarts: String get() = "file contents, total lines:"
    val dumpStarts: String get() = "binary file dump, total lines:"
    val base64Starts: String get() = "base64 encoded contents, total lines:"
    val endFile: String get() = "end of file"
}

object RuStrings : Strings {
    override val textFile = "Текстовый файл"
    override val binaryFile = "Двоичный файл"
    override val fileTime = "Время модификации"
    override val textFileStarts = "начало файла, всего строк:"
    override val dumpStarts = "Начало файла, всего строк:"
    override val base64Starts = "Начало base64-кодированного файла, всего строк:"
    override val endFile = "конец файла"
}


class DirAggregator : CliktCommand(
    help = """
        | aggregate_to_text v${version}
        | 
        | Create a text file containing all files from the root directory providing their names, dates
        | amd hashes. Text files are included as is, binary as base64 or dump. Thr resulting text files
        | is both human-readable (especially without base64) and allow to resotre source files tree from
        | it.
        | 
        | Resulting file is copied to stdout to comply with best unix practices.
        | 
        | For issues/ideas visit project's github: https://github.com/sergeych/aggregate_to_txt
    """.trimMargin(),
    name = "aggregate_to_txt",
    printHelpOnEmptyArgs = true,
) {
    val dry by option("-d", "--dry", help = "dry run")
        .flag(default = false)

    val base64 by option("-b", "--base64", help = "Use base64 for binary files, not the dump")
        .flag(default = false)

    val root by argument()

    val binaryExtsFound = mutableSetOf<String>()

    val ru by option(help = "use Russian locale").flag(default = false)

    val strings: Strings by lazy {
        if (ru)
            RuStrings
        else
            object : Strings {}
    }

    fun isBinary(x: Path): Boolean {
        val name = x.name
        if (name in textNames) return false
        val parts = name.split('.')
        if (parts.size == 1) {
            // We suppose that only such files also starting with shebang are text
            return checkNonAscii(x)
        }
        val ext = parts.last()
        if( ext in textExtensions ) return false
        if( checkNonAscii(x) ) {
            binaryExtsFound.add(ext)
            return true
        }
        return false
    }

    private fun checkNonAscii(fileName: Path): Boolean {
        fopen(fileName.toString(), "rb")?.let { f ->
            try {
                val buffer = ByteArray(2) // well I'm a rpogrammer mean dont' beleive in non-words ;)
                val cnt = fread(buffer.refTo(0), 1, 2, f)
                if (cnt == 2UL) {
                    if (buffer[0].toInt() == '#'.code &&
                        buffer[1].toInt() == '!'.code
                    ) {
                        return false
                    }
                    rewind(f)
                    val buffer = ByteArray(0x8000)
                    val chunk = buffer.size
                    var pos = 0
                    while( feof(f) != EOF ) {
                        val count = fread(buffer.refTo(0), 1, chunk.toULong(), f)
                        if( count == 0UL ) break
                        for( i in 0 until count.toInt()) {
                            val code = buffer[i].toUInt()
                            pos++
                            when(code) {
                                9U, 10U, 13U, 32U -> {}// ok
                                else -> {
                                    if( code < 32U ) {
                                        if( dry )
                                            println("Non-ascii character found @$pos: $code, considering file is binary")
                                        return true
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                fclose(f)
            }
        }
        return false
    }


    override fun run() {
        try {
            scan(root)
            if (dry) {
                if (binaryExtsFound.isNotEmpty())
                    println("Following unknown extensions are treated as binary:\n" +
                            binaryExtsFound.joinToString(", ") { ".$it" })
                else
                    println("No unknown/binary files found")
            }
        } catch (x: FileNotFoundException) {
            println("Error: (root?) file not found")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scan(root: String) {
        for (x: Path in FileSystem.SYSTEM.listRecursively(root.toPath(), false)) {
            val binary = isBinary(x)
            if (FileSystem.SYSTEM.metadata(x).isDirectory) {
                if (dry) {
                    println("Dir    $x")
                }
            } else {
                if (dry) {
                    val b = if (binary) "B" else "T"
                    println("File $b $x")
                } else {
                    if (binary)
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
            println("--- ${strings.binaryFile}: $path")
        else
            println("--- ${strings.textFile}: $path")
        FileSystem.SYSTEM.metadata(path).lastAccessedAtMillis?.let { t ->
            println("--- ${strings.fileTime}: %tO".sprintf(Instant.fromEpochMilliseconds(t)))
        }
        println("--- SHA256: ${data.sha256().hex()}")
    }

    private fun processText(x: Path) {
        val data = FileSystem.SYSTEM.read(x) { readByteString() }
        val str = data.utf8()
        printHeder(x, false, data)
        println("--- ${strings.textFileStarts} ${str.lines().size} ---\n$str\n--- ${strings.endFile} ---\n ")
    }

    private fun processBinary(x: Path) {
        val data: ByteString = FileSystem.SYSTEM.read(x) { readByteString() }
        printHeder(x, true, data)
        val dump: List<String>
        val intro: String
        if (base64) {
            dump = data.base64().chunked(80)
            intro = strings.base64Starts
        } else {
            dump = data.toByteArray().toDumpLines()
            intro = strings.dumpStarts
        }
        println("--- $intro ${dump.size} ---\n${dump.joinToString("\n")}\n--- ${strings.endFile} ---\n")
    }
}

fun main(args: Array<String>) = DirAggregator().main(args)