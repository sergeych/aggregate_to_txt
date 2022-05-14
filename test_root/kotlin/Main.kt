import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.cinterop.pointed
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir


fun scan(root: String) {
    opendir(root)?.let { dir ->
        try {
            while(true) {
                val ep = readdir(dir) ?: break
                println(ep.pointed.d_name)
                println(ep.pointed.d_type)
            }
        }
        finally {
            closedir(dir)
        }
    }
}

class DirAggregator : CliktCommand() {
    val root by option(help = "root folder to collect from")
        .default("./test_root")

    override fun run() {
        println("Scanning $root...")

    }

}

fun main(args: Array<String>) = DirAggregator().main(args)