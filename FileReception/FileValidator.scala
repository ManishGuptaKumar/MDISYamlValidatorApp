package com.fileservice.validation

import com.fileservice.model._
import org.slf4j.LoggerFactory

import java.io.{BufferedInputStream, FileInputStream}
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}

/**
 * FileValidator runs all enabled validations against an InboundFile
 * and combines results into a single ValidationResult.
 */
class FileValidator(feedConfig: FeedConfig) {
  private val log = LoggerFactory.getLogger(getClass)

  def validate(inbound: InboundFile): ValidationResult = {
    val cfg = feedConfig.validation

    val results = Seq(
      if (cfg.filename.enabled)  validateFilename(inbound, cfg.filename) else ValidationResult.Valid,
      if (cfg.fileSize.enabled)  validateFileSize(inbound, cfg.fileSize) else ValidationResult.Valid,
      if (cfg.checksum.enabled)  validateChecksum(inbound, cfg.checksum) else ValidationResult.Valid,
      if (cfg.rowCount.enabled)  validateRowCount(inbound, cfg.rowCount) else ValidationResult.Valid
    )

    val combined = ValidationResult.combine(results)
    combined match {
      case ValidationResult.Valid =>
        log.info(s"[${inbound.feedId}] ✓ Validation passed: ${inbound.filename}")
      case ValidationResult.Invalid(vs) =>
        log.warn(s"[${inbound.feedId}] ✗ Validation failed: ${inbound.filename} → ${vs.mkString("; ")}")
    }
    combined
  }

  // ── Filename validation ────────────────────────────────────────────────────
  private def validateFilename(inbound: InboundFile, cfg: FilenameValidationConfig): ValidationResult = {
    val pattern = cfg.pattern.r
    if (!pattern.matches(inbound.filename)) {
      ValidationResult.Invalid(Seq(
        s"Filename '${inbound.filename}' does not match expected pattern '${cfg.pattern}'"
      ))
    } else {
      // Optional: validate embedded date is a real calendar date
      val dateViolation = Try {
        val m = pattern.findFirstMatchIn(inbound.filename)
        m.map { mtch =>
          if (cfg.dateGroupIndex > 0 && cfg.dateGroupIndex <= mtch.groupCount) {
            val datePart = mtch.group(cfg.dateGroupIndex)
            val fmt = DateTimeFormatter.ofPattern(cfg.dateFormat)
            LocalDate.parse(datePart, fmt)  // throws if invalid
          }
        }
      } match {
        case Failure(ex) =>
          Some(s"Date embedded in filename '${inbound.filename}' is invalid: ${ex.getMessage}")
        case _ => None
      }

      dateViolation match {
        case Some(msg) => ValidationResult.Invalid(Seq(msg))
        case None      => ValidationResult.Valid
      }
    }
  }

  // ── File size validation ───────────────────────────────────────────────────
  private def validateFileSize(inbound: InboundFile, cfg: FileSizeValidationConfig): ValidationResult = {
    val violations = scala.collection.mutable.ArrayBuffer.empty[String]
    if (inbound.sizeBytes < cfg.minBytes)
      violations += s"File size ${inbound.sizeBytes} bytes is below minimum ${cfg.minBytes} bytes"
    if (inbound.sizeBytes > cfg.maxBytes)
      violations += s"File size ${inbound.sizeBytes} bytes exceeds maximum ${cfg.maxBytes} bytes"
    if (violations.isEmpty) ValidationResult.Valid else ValidationResult.Invalid(violations.toSeq)
  }

  // ── Checksum validation ────────────────────────────────────────────────────
  private def validateChecksum(inbound: InboundFile, cfg: ChecksumValidationConfig): ValidationResult = {
    val expected = resolveExpectedChecksum(inbound, cfg)
    expected match {
      case None =>
        log.warn(s"[${inbound.feedId}] Checksum validation enabled but no expected value found — skipping")
        ValidationResult.Valid
      case Some(expectedHex) =>
        val actualHex = computeChecksum(inbound.path, cfg.algorithm)
        actualHex match {
          case Failure(ex) =>
            ValidationResult.Invalid(Seq(s"Failed to compute checksum: ${ex.getMessage}"))
          case Success(computed) =>
            if (computed.equalsIgnoreCase(expectedHex.trim)) {
              log.debug(s"[${inbound.feedId}] Checksum OK: $computed")
              ValidationResult.Valid
            } else {
              ValidationResult.Invalid(Seq(
                s"Checksum mismatch — expected: $expectedHex, actual: $computed"
              ))
            }
        }
    }
  }

  private def resolveExpectedChecksum(inbound: InboundFile, cfg: ChecksumValidationConfig): Option[String] =
    cfg.source match {
      case ChecksumSource.DoneFile =>
        inbound.doneFile.expectedChecksum
      case ChecksumSource.Sidecar =>
        val sidecarPath = inbound.path.resolveSibling(inbound.filename + cfg.sidecarExtension)
        readFirstLine(sidecarPath)
      case ChecksumSource.Inline =>
        inbound.doneFile.expectedChecksum
    }

  private def computeChecksum(path: Path, algorithm: ChecksumAlgorithm): Try[String] = Try {
    val algoName = algorithm match {
      case ChecksumAlgorithm.MD5    => "MD5"
      case ChecksumAlgorithm.SHA256 => "SHA-256"
      case ChecksumAlgorithm.SHA512 => "SHA-512"
    }
    val digest = MessageDigest.getInstance(algoName)
    val buf    = new Array[Byte](65536)
    Using(new BufferedInputStream(new FileInputStream(path.toFile))) { is =>
      var read = is.read(buf)
      while (read != -1) {
        digest.update(buf, 0, read)
        read = is.read(buf)
      }
    }
    HexFormat.of().formatHex(digest.digest())
  }

  // ── Row count validation ───────────────────────────────────────────────────
  private def validateRowCount(inbound: InboundFile, cfg: RowCountValidationConfig): ValidationResult = {
    val expected = resolveExpectedRowCount(inbound, cfg)
    expected match {
      case None =>
        log.warn(s"[${inbound.feedId}] Row count validation enabled but no expected value found — skipping")
        ValidationResult.Valid
      case Some(expectedCount) =>
        val actualCount = countRows(inbound)
        actualCount match {
          case Failure(ex) =>
            ValidationResult.Invalid(Seq(s"Failed to count rows: ${ex.getMessage}"))
          case Success(actual) =>
            if (actual == expectedCount) {
              log.debug(s"[${inbound.feedId}] Row count OK: $actual")
              ValidationResult.Valid
            } else {
              ValidationResult.Invalid(Seq(
                s"Row count mismatch — expected: $expectedCount, actual: $actual"
              ))
            }
        }
    }
  }

  private def resolveExpectedRowCount(inbound: InboundFile, cfg: RowCountValidationConfig): Option[Long] =
    cfg.source match {
      case RowCountSource.DoneFile =>
        inbound.doneFile.expectedRowCount
      case RowCountSource.Sidecar =>
        val sidecarPath = inbound.path.resolveSibling(inbound.filename + ".count")
        readFirstLine(sidecarPath).flatMap(s => Try(s.toLong).toOption)
      case RowCountSource.Trailer =>
        readTrailerRowCount(inbound, cfg.trailerColumnIndex)
    }

  private def countRows(inbound: InboundFile): Try[Long] = Try {
    val headerSkip = feedConfig.headerLines
    Using(Source.fromFile(inbound.path.toFile, feedConfig.encoding)) { src =>
      src.getLines().drop(headerSkip).count(_ => true).toLong
    }.get
  }

  private def readTrailerRowCount(inbound: InboundFile, colIndex: Int): Option[Long] = Try {
    Using(Source.fromFile(inbound.path.toFile, feedConfig.encoding)) { src =>
      val lines = src.getLines().toVector
      lines.lastOption.flatMap { trailer =>
        val parts = trailer.split(feedConfig.delimiter)
        if (colIndex < parts.length) Try(parts(colIndex).trim.toLong).toOption
        else None
      }
    }.getOrElse(None)
  }.toOption.flatten

  // ── Utilities ──────────────────────────────────────────────────────────────
  private def readFirstLine(path: Path): Option[String] =
    Try {
      Using(Source.fromFile(path.toFile)) { src =>
        src.getLines().find(_.nonEmpty)
      }.getOrElse(None)
    }.toOption.flatten
}
