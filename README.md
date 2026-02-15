# FilesDownloader

A parallel file downloader written in Kotlin that downloads files from an HTTP server by fetching byte-range chunks concurrently.

## Features

- **Parallel chunk downloads** — splits files into configurable byte ranges and downloads them concurrently using Kotlin coroutines
- **Retry with exponential backoff** — transient network failures are automatically retried with configurable attempts and delay
- **Progress reporting** — real-time download progress displayed in the terminal
- **Chunk integrity verification** — each downloaded chunk is verified against the expected byte range length
- **Streaming to disk** — chunks are written directly to the output file via `RandomAccessFile`, keeping memory usage constant regardless of file size
- **Fallback download** — if the server does not support byte-range requests, falls back to a single-stream full download
- **Configurable** — chunk size, parallelism, and retry settings are all configurable via CLI arguments

## Requirements

- JDK 24+
- Gradle 8.14+ (included via wrapper)
- Docker (for running the test HTTP server)

## Quick Start

### 1. Start a local HTTP server

```bash
docker run --rm -p 8080:80 -v /path/to/your/files:/usr/local/apache2/htdocs/ httpd:latest
```

### 2. Run the downloader

```bash
./gradlew run --args="http://localhost:8080/myfile.txt output.txt"
```

### 3. With custom options

```bash
./gradlew run --args="http://localhost:8080/largefile.bin output.bin --chunk-size=524288 --parallel=8"
```

## Usage

```
Usage: filesdownloader <url> <output-path> [options]

Options:
  --chunk-size=<bytes>    Size of each download chunk in bytes (default: 1048576 = 1 MB)
  --parallel=<count>      Maximum parallel downloads (default: 4)

Example:
  filesdownloader http://localhost:8080/file.txt output.txt
  filesdownloader http://localhost:8080/file.txt output.txt --chunk-size=524288 --parallel=8
```

## How It Works

```
1. HEAD request  -->  Get file size + check Accept-Ranges: bytes
2. Split         -->  Divide file into byte ranges (e.g. 5 MB file / 1 MB chunks = 5 ranges)
3. Download      -->  Fetch chunks in parallel (bounded by semaphore)
                      Each chunk: download -> verify size -> write to disk at correct offset
4. Done          -->  Output file is complete
```

If the server does not support range requests, the downloader falls back to a single GET request for the entire file.

## Architecture

```
src/main/kotlin/
  Main.kt                                  CLI entry point, argument parsing
  org/example/downloader/
    FileDownloader.kt                       Orchestrator: HEAD -> split -> parallel GET -> write
    ChunkSplitter.kt                        Pure function: file size + chunk size -> byte ranges
    FileMerger.kt                           Utility for sequential chunk merging
    ProgressListener.kt                     Callback interface for progress updates
    exception/
      DownloadException.kt                  Sealed exception hierarchy
    http/
      HttpClient.kt                         Interface: fetchMetadata(), downloadRange(), downloadFull()
      KtorHttpClient.kt                     Ktor CIO implementation
    model/
      ByteRange.kt                          Inclusive byte range (start, end)
      DownloadConfig.kt                     Configuration: chunk size, parallelism, retry settings
      FileMetadata.kt                       Content-Length + Accept-Ranges from HEAD response
    util/
      Retry.kt                              Generic retry with exponential backoff
```

### Key Design Decisions

- **Interface-based HTTP client** — `HttpClient` is an interface, making `FileDownloader` testable without any HTTP mocking frameworks. The Ktor implementation can be swapped for any other HTTP library.
- **Pure chunk splitting** — `ChunkSplitter` is a pure function with no side effects, trivially testable with many edge cases.
- **Sealed exception hierarchy** — `DownloadException` is a sealed class, enabling exhaustive `when` expressions for error handling.
- **Streaming to disk** — chunks are written directly to `RandomAccessFile` at the correct byte offset, avoiding holding the entire file in memory.
- **Coroutine-based parallelism** — uses `async`/`awaitAll` with `Semaphore` for bounded concurrency.

## Testing

Run all tests:

```bash
./gradlew test
```

The test suite includes 44 tests across 5 test classes:

| Test Class | Tests | What It Covers |
|---|---|---|
| `ChunkSplitterTest` | 10 | Range calculation, edge cases, invalid inputs |
| `FileDownloaderTest` | 12 | Full download flow, retry, progress, fallback, chunk verification |
| `FileMergerTest` | 7 | File writing, ordering, binary data, error handling |
| `KtorHttpClientTest` | 8 | HEAD/GET parsing, MockEngine, error responses |
| `RetryTest` | 7 | Exponential backoff, retry limits, exception filtering |

Tests use:
- **Ktor MockEngine** for HTTP tests (no real server needed)
- **Hand-written fakes** for `HttpClient` (no mocking frameworks)
- **JUnit `@TempDir`** for file I/O tests

## Known Limitations

- **No download resume** — if the process is interrupted, the download must restart from scratch. There is no checkpoint/progress file to track completed chunks.
- **Fallback holds full file in memory** — when the server does not support range requests, the entire file is downloaded into memory before writing to disk. Only the parallel path streams to disk.
- **No checksum verification** — the downloaded file's integrity is not verified against a hash (e.g. SHA-256). Only individual chunk sizes are verified.
- **No redirect handling configuration** — relies on Ktor CIO's default redirect behavior.
- **Single URL only** — no support for downloading from multiple mirrors.

## Future Improvements

- **Download resume** — persist a `.progress` metadata file tracking completed ranges; skip them on restart
- **Checksum verification** — accept an optional SHA-256 hash and verify the final file
- **Adaptive chunk sizing** — automatically adjust chunk size based on file size and network conditions
- **Connection pool tuning** — configure Ktor CIO's connection pool for high parallelism scenarios
- **Multiple URL sources** — support downloading from mirrors for redundancy and speed
- **Streaming fallback** — stream the full download to disk instead of holding in memory

## Tech Stack

- **Kotlin** 2.2.20
- **Ktor Client** (CIO engine) — HTTP client
- **Kotlin Coroutines** — parallel downloads
- **Logback** — logging
- **JUnit 5** + **kotlin-test** — testing
- **Ktor MockEngine** — HTTP test doubles
