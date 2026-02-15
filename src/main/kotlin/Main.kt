package org.example

import kotlinx.coroutines.runBlocking
import org.example.downloader.FileDownloader
import org.example.downloader.ProgressListener
import org.example.downloader.exception.DownloadException
import org.example.downloader.http.KtorHttpClient
import org.example.downloader.model.DownloadConfig
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args) ?: exitProcess(1)

    val config = DownloadConfig(
        chunkSize = parsedArgs.chunkSize,
        maxParallelDownloads = parsedArgs.parallelism,
    )

    val progressListener = ProgressListener { bytesDownloaded, totalBytes ->
        val percent = (bytesDownloaded * 100) / totalBytes
        print("\rProgress: $bytesDownloaded / $totalBytes bytes ($percent%)")
        if (bytesDownloaded >= totalBytes) println()
    }

    KtorHttpClient().use { httpClient ->
        val downloader = FileDownloader(httpClient, config, progressListener)

        runBlocking {
            try {
                println("Downloading: ${parsedArgs.url}")
                println("Output: ${parsedArgs.outputPath}")
                println("Chunk size: ${parsedArgs.chunkSize / 1024} KB, Parallel downloads: ${parsedArgs.parallelism}")
                println()

                downloader.download(parsedArgs.url, Path.of(parsedArgs.outputPath))

                println("Download completed successfully.")
            } catch (e: DownloadException.RangesNotSupported) {
                System.err.println("Error: ${e.message}")
                exitProcess(1)
            } catch (e: DownloadException.NetworkError) {
                System.err.println("Network error: ${e.message}")
                exitProcess(1)
            } catch (e: DownloadException.FileWriteError) {
                System.err.println("File write error: ${e.message}")
                exitProcess(1)
            }
        }
    }
}

private data class ParsedArgs(
    val url: String,
    val outputPath: String,
    val chunkSize: Long = DownloadConfig.DEFAULT_CHUNK_SIZE,
    val parallelism: Int = DownloadConfig.DEFAULT_MAX_PARALLEL_DOWNLOADS,
)

private fun parseArgs(args: Array<String>): ParsedArgs? {
    if (args.size < 2) {
        printUsage()
        return null
    }

    val url = args[0]
    val outputPath = args[1]

    if (!isValidUrl(url)) {
        System.err.println("Invalid URL: $url")
        System.err.println("URL must start with http:// or https://")
        return null
    }

    if (!isValidOutputPath(outputPath)) {
        System.err.println("Invalid output path: $outputPath")
        System.err.println("Parent directory does not exist.")
        return null
    }

    var chunkSize = DownloadConfig.DEFAULT_CHUNK_SIZE
    var parallelism = DownloadConfig.DEFAULT_MAX_PARALLEL_DOWNLOADS

    for (arg in args.drop(2)) {
        when {
            arg.startsWith("--chunk-size=") -> {
                chunkSize = arg.substringAfter("=").toLongOrNull()
                    ?: run {
                        System.err.println("Invalid chunk size: ${arg.substringAfter("=")}")
                        return null
                    }
            }
            arg.startsWith("--parallel=") -> {
                parallelism = arg.substringAfter("=").toIntOrNull()
                    ?: run {
                        System.err.println("Invalid parallelism value: ${arg.substringAfter("=")}")
                        return null
                    }
            }
            else -> {
                System.err.println("Unknown argument: $arg")
                printUsage()
                return null
            }
        }
    }

    return ParsedArgs(url, outputPath, chunkSize, parallelism)
}

private fun isValidUrl(url: String): Boolean {
    return url.startsWith("http://") || url.startsWith("https://")
}

private fun isValidOutputPath(outputPath: String): Boolean {
    val parent = Path.of(outputPath).parent ?: return true // file in current directory is valid
    return parent.toFile().isDirectory
}

private fun printUsage() {
    println("""
        Usage: filesdownloader <url> <output-path> [options]
        
        Options:
          --chunk-size=<bytes>    Size of each download chunk in bytes (default: 1048576 = 1 MB)
          --parallel=<count>     Maximum parallel downloads (default: 4)
        
        Example:
          filesdownloader http://localhost:8080/file.txt output.txt
          filesdownloader http://localhost:8080/file.txt output.txt --chunk-size=524288 --parallel=8
    """.trimIndent())
}
