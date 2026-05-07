package com.fileservice.watcher

import com.fileservice.model._
import org.slf4j.LoggerFactory

import java.io.{File, FileNotFoundException}
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}

/**
 * DoneFileWatcher scans a feed's sourceDir for .done sentinel files.
 *
 * Two patterns are supported (configured via doneFilePattern):
 *
 *   1. PER-FILE  — "{filename}.done"
 *      Each data file has its own companion done file.
 *      e.g. sales_20240501.csv → sales_20240501.csv.done
 *
 *   2. SENTINEL  — ".done"
 *      A single .done file signals all matching files in the directory
 *      are ready to process.
 *
 * Done files may optionally contain lines of the form:
 *   CHECKSUM=<hex>
 *   ROWCOUNT=<n>
 *   FILENAME=<name>
 */
class DoneFileWatcher(feedConfig: FeedConfig) {
  private val log        = LoggerFactory.getLogger(getClass)
  private val sourceDir  = Paths.get(feedConfig.sourceDir)
  private val fileRegex  = feedConfig.filePattern.r
  private val isSentinel = feedConfig.doneFilePattern == ".done"

  /**
   * Scan sourceDir and return all (InboundFile, DoneFile) pairs that are
   * ready for processing.  Does not mutate any files.
   */
  def scan(): Seq[InboundFile] = {
    if (!Files.isDirectory(sourceDir)) {
      log.warn(s"[${feedConfig.feedId}] Source directory does not exist: $sourceDir")
      return Seq.empty
    }

    if (isSentinel) scanWithSentinel() else scanPerFile()
  }

  // ── Per-file mode ──────────────────────────────────────────────────────────
  private def scanPerFile(): Seq[InboundFile] = {
    val allFiles = listDir(sourceDir)

    val doneFiles = allFiles.filter(_.getFileName.toString.endsWith(".done"))
    log.debug(s"[${feedConfig.feedId}] Found ${doneFiles.size} .done files in $sourceDir")

    doneFiles.flatMap { donePath =>
      // Derive data file name by stripping the ".done" suffix
      val doneFilename = donePath.getFileName.toString
      val dataFilename = doneFilename.stripSuffix(".done")
      val dataPath     = sourceDir.resolve(dataFilename)

      if (!Files.exists(dataPath)) {
        log.warn(s"[${feedConfig.feedId}] Done file found but data file missing: $dataPath")
        None
      } else if (!fileRegex.matches(dataFilename)) {
        log.warn(s"[${feedConfig.feedId}] Data file '$dataFilename' does not match pattern '${feedConfig.filePattern}' — skipping")
        None
      } else {
        parseDoneFile(donePath).map { doneFile =>
          buildInboundFile(dataPath, doneFile)
        }
      }
    }
  }

  // ── Sentinel mode ──────────────────────────────────────────────────────────
  private def scanWithSentinel(): Seq[InboundFile] = {
    val sentinelPath = sourceDir.resolve(".done")

    if (!Files.exists(sentinelPath)) {
      log.debug(s"[${feedConfig.feedId}] No sentinel .done file in $sourceDir")
      return Seq.empty
    }

    log.info(s"[${feedConfig.feedId}] Sentinel .done found — scanning for matching data files")

    val doneFile = parseDoneFile(sentinelPath).getOrElse(
      DoneFile(sentinelPath, None, None, None, Instant.now())
    )

    listDir(sourceDir)
      .filterNot(_.getFileName.toString == ".done")
      .filter(p => fileRegex.matches(p.getFileName.toString))
      .map(dataPath => buildInboundFile(dataPath, doneFile))
  }

  // ── Done file parser ───────────────────────────────────────────────────────
  /**
   * Parse a .done file for optional metadata.
   *
   * Supported lines (case-insensitive, optional):
   *   CHECKSUM=<hex>
   *   ROWCOUNT=<n>
   *   FILENAME=<name>
   */
  private def parseDoneFile(donePath: Path): Option[DoneFile] = {
    Try {
      val kvMap: Map[String, String] = Using(Source.fromFile(donePath.toFile)) { src =>
        src.getLines()
          .map(_.trim)
          .filterNot(_.isEmpty)
          .flatMap { line =>
            val parts = line.split("=", 2)
            if (parts.length == 2) Some(parts(0).trim.toUpperCase -> parts(1).trim)
            else None
          }
          .toMap
      }.getOrElse(Map.empty)

      DoneFile(
        path             = donePath,
        targetFilename   = kvMap.get("FILENAME"),
        expectedChecksum = kvMap.get("CHECKSUM"),
        expectedRowCount = kvMap.get("ROWCOUNT").flatMap(s => Try(s.toLong).toOption),
        receivedAt       = Instant.ofEpochMilli(Files.getLastModifiedTime(donePath).toMillis)
      )
    } match {
      case Success(df) =>
        log.debug(s"[${feedConfig.feedId}] Parsed done file: $donePath → checksum=${df.expectedChecksum}, rows=${df.expectedRowCount}")
        Some(df)
      case Failure(ex) =>
        log.error(s"[${feedConfig.feedId}] Failed to parse done file $donePath: ${ex.getMessage}")
        None
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────
  private def buildInboundFile(dataPath: Path, doneFile: DoneFile): InboundFile =
    InboundFile(
      path        = dataPath,
      feedId      = feedConfig.feedId,
      filename    = dataPath.getFileName.toString,
      sizeBytes   = Try(Files.size(dataPath)).getOrElse(0L),
      detectedAt  = Instant.now(),
      doneFile    = doneFile
    )

  private def listDir(dir: Path): Seq[Path] =
    Try {
      val ds = Files.newDirectoryStream(dir)
      try {
        val buf = scala.collection.mutable.ArrayBuffer.empty[Path]
        ds.forEach(p => buf += p)
        buf.toSeq
      } finally ds.close()
    }.getOrElse(Seq.empty)
}
