package com.fileservice.model

import java.nio.file.Path
import java.time.Instant

// ─────────────────────────────────────────────
// Enumerations
// ─────────────────────────────────────────────

sealed trait DestinationType
object DestinationType {
  case object S3   extends DestinationType
  case object HDFS extends DestinationType
}

sealed trait ChecksumAlgorithm
object ChecksumAlgorithm {
  case object MD5    extends ChecksumAlgorithm
  case object SHA256 extends ChecksumAlgorithm
  case object SHA512 extends ChecksumAlgorithm

  def fromString(s: String): ChecksumAlgorithm = s.toUpperCase match {
    case "MD5"     => MD5
    case "SHA-256" => SHA256
    case "SHA-512" => SHA512
    case other     => throw new IllegalArgumentException(s"Unknown checksum algorithm: $other")
  }
}

sealed trait ChecksumSource
object ChecksumSource {
  case object DoneFile extends ChecksumSource
  case object Sidecar  extends ChecksumSource
  case object Inline   extends ChecksumSource

  def fromString(s: String): ChecksumSource = s.toUpperCase match {
    case "DONE_FILE" => DoneFile
    case "SIDECAR"   => Sidecar
    case "INLINE"    => Inline
    case other       => throw new IllegalArgumentException(s"Unknown checksum source: $other")
  }
}

sealed trait RowCountSource
object RowCountSource {
  case object DoneFile extends RowCountSource
  case object Sidecar  extends RowCountSource
  case object Trailer  extends RowCountSource

  def fromString(s: String): RowCountSource = s.toUpperCase match {
    case "DONE_FILE" => DoneFile
    case "SIDECAR"   => Sidecar
    case "TRAILER"   => Trailer
    case other       => throw new IllegalArgumentException(s"Unknown row count source: $other")
  }
}

sealed trait ProcessingStatus
object ProcessingStatus {
  case object Pending    extends ProcessingStatus
  case object Validating extends ProcessingStatus
  case object Copying    extends ProcessingStatus
  case object Succeeded  extends ProcessingStatus
  case object Failed     extends ProcessingStatus
  case object Retrying   extends ProcessingStatus
}

// ─────────────────────────────────────────────
// Validation Config
// ─────────────────────────────────────────────

case class ChecksumValidationConfig(
    enabled: Boolean,
    algorithm: ChecksumAlgorithm,
    source: ChecksumSource,
    sidecarExtension: String = ".md5"
)

case class RowCountValidationConfig(
    enabled: Boolean,
    source: RowCountSource,
    trailerColumnIndex: Int = 0
)

case class FilenameValidationConfig(
    enabled: Boolean,
    pattern: String,
    dateGroupIndex: Int = 0,
    dateFormat: String = "yyyyMMdd"
)

case class FileSizeValidationConfig(
    enabled: Boolean,
    minBytes: Long,
    maxBytes: Long
)

case class ValidationConfig(
    checksum: ChecksumValidationConfig,
    rowCount: RowCountValidationConfig,
    filename: FilenameValidationConfig,
    fileSize: FileSizeValidationConfig
)

// ─────────────────────────────────────────────
// Destination Configs
// ─────────────────────────────────────────────

case class S3DestinationConfig(
    enabled: Boolean,
    bucket: String,
    keyPrefix: String,
    keyPattern: String,
    storageClass: String,
    serverSideEncryption: String,
    kmsKeyId: Option[String],
    multipartThresholdMb: Int
)

case class HdfsDestinationConfig(
    enabled: Boolean,
    namenode: String,
    path: String,
    replicationFactor: Short,
    blockSizeMb: Long,
    overwrite: Boolean
)

// ─────────────────────────────────────────────
// Lifecycle Hooks
// ─────────────────────────────────────────────

case class OnSuccessConfig(
    archiveSource: Boolean,
    deleteDoneFile: Boolean,
    writeAuditLog: Boolean
)

case class OnFailureConfig(
    moveToErrorDir: Boolean,
    alertEmail: Option[String],
    maxRetries: Int,
    retryDelaySeconds: Int
)

// ─────────────────────────────────────────────
// Feed Metadata
// ─────────────────────────────────────────────

case class FeedConfig(
    feedId: String,
    description: String,
    enabled: Boolean,
    sourceDir: String,
    doneFilePattern: String,
    filePattern: String,
    encoding: String,
    delimiter: String,
    headerLines: Int,
    compressed: Boolean,
    compressionType: Option[String],
    validation: ValidationConfig,
    s3Config: Option[S3DestinationConfig],
    hdfsConfig: Option[HdfsDestinationConfig],
    onSuccess: OnSuccessConfig,
    onFailure: OnFailureConfig
)

// ─────────────────────────────────────────────
// Runtime / Processing Models
// ─────────────────────────────────────────────

/** Represents a .done file parsed from disk */
case class DoneFile(
    path: Path,
    targetFilename: Option[String],    // if encoded in done file name
    expectedChecksum: Option[String],
    expectedRowCount: Option[Long],
    receivedAt: Instant
)

/** Represents an inbound data file ready for processing */
case class InboundFile(
    path: Path,
    feedId: String,
    filename: String,
    sizeBytes: Long,
    detectedAt: Instant,
    doneFile: DoneFile
)

/** Validation outcome */
sealed trait ValidationResult
object ValidationResult {
  case object Valid                              extends ValidationResult
  case class Invalid(violations: Seq[String])   extends ValidationResult

  def combine(results: Seq[ValidationResult]): ValidationResult = {
    val violations = results.collect { case Invalid(vs) => vs }.flatten
    if (violations.isEmpty) Valid else Invalid(violations)
  }
}

/** Per-destination copy result */
case class CopyResult(
    destination: DestinationType,
    targetPath: String,
    bytesWritten: Long,
    durationMs: Long,
    succeeded: Boolean,
    error: Option[String]
)

/** Top-level processing event record */
case class ProcessingEvent(
    eventId: String,
    feedId: String,
    filename: String,
    fileSizeBytes: Long,
    status: ProcessingStatus,
    validationResult: Option[ValidationResult],
    copyResults: Seq[CopyResult],
    startedAt: Instant,
    completedAt: Option[Instant],
    retryCount: Int,
    errorMessage: Option[String]
)

/** Service-level configuration envelope */
case class ServiceConfig(
    pollIntervalSeconds: Int,
    threadPoolSize: Int,
    workDir: String,
    archiveDir: String,
    errorDir: String,
    feeds: Seq[FeedConfig]
)
