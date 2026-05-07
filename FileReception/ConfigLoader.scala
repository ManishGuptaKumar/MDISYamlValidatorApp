package com.fileservice.config

import com.fileservice.model._
import com.typesafe.config.{Config, ConfigFactory}
import io.circe._
import io.circe.yaml.parser
import org.slf4j.LoggerFactory

import java.io.{File, FileReader}
import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

/**
 * Loads and parses the metadata-driven YAML configuration.
 * All feed definitions, validation rules, and destination configs
 * are resolved here into strongly typed Scala models.
 */
object ConfigLoader {
  private val log = LoggerFactory.getLogger(getClass)

  def load(configPath: String = "application.yaml"): ServiceConfig = {
    log.info(s"Loading configuration from: $configPath")
    val json = loadYaml(configPath)
    parseServiceConfig(json)
  }

  // ── YAML → Circe Json ──────────────────────────────────────────────────────
  private def loadYaml(path: String): Json = {
    val file = new File(path)
    val reader = if (file.exists()) {
      log.info(s"Loading external config: ${file.getAbsolutePath}")
      new FileReader(file)
    } else {
      log.info("Loading classpath config: application.yaml")
      val stream = getClass.getClassLoader.getResourceAsStream("application.yaml")
      new java.io.InputStreamReader(stream)
    }
    parser.parse(reader) match {
      case Right(json) => json
      case Left(err)   => throw new RuntimeException(s"Failed to parse YAML config: ${err.message}")
    }
  }

  // ── Top-level service config ───────────────────────────────────────────────
  private def parseServiceConfig(root: Json): ServiceConfig = {
    val c = root.hcursor
    ServiceConfig(
      pollIntervalSeconds = c.downField("service").downField("pollIntervalSeconds").as[Int].getOrElse(30),
      threadPoolSize      = c.downField("service").downField("threadPoolSize").as[Int].getOrElse(4),
      workDir             = c.downField("service").downField("workDir").as[String].getOrElse("/tmp/work"),
      archiveDir          = c.downField("service").downField("archiveDir").as[String].getOrElse("/tmp/archive"),
      errorDir            = c.downField("service").downField("errorDir").as[String].getOrElse("/tmp/error"),
      feeds               = parseFeeds(c.downField("feeds").focus.getOrElse(Json.arr()))
    )
  }

  // ── Feed list ──────────────────────────────────────────────────────────────
  private def parseFeeds(feedsJson: Json): Seq[FeedConfig] =
    feedsJson.asArray.getOrElse(Vector.empty).map(parseFeedConfig)

  private def parseFeedConfig(j: Json): FeedConfig = {
    val c = j.hcursor
    FeedConfig(
      feedId          = required(c, "feedId"),
      description     = c.downField("description").as[String].getOrElse(""),
      enabled         = c.downField("enabled").as[Boolean].getOrElse(true),
      sourceDir       = required(c, "sourceDir"),
      doneFilePattern = c.downField("doneFilePattern").as[String].getOrElse("{filename}.done"),
      filePattern     = required(c, "filePattern"),
      encoding        = c.downField("encoding").as[String].getOrElse("UTF-8"),
      delimiter       = c.downField("delimiter").as[String].getOrElse(","),
      headerLines     = c.downField("headerLines").as[Int].getOrElse(0),
      compressed      = c.downField("compressed").as[Boolean].getOrElse(false),
      compressionType = c.downField("compressionType").as[String].toOption,
      validation      = parseValidation(c.downField("validation")),
      s3Config        = parseS3Config(c.downField("destinations")),
      hdfsConfig      = parseHdfsConfig(c.downField("destinations")),
      onSuccess       = parseOnSuccess(c.downField("onSuccess")),
      onFailure       = parseOnFailure(c.downField("onFailure"))
    )
  }

  // ── Validation ─────────────────────────────────────────────────────────────
  private def parseValidation(c: ACursor): ValidationConfig = {
    val cs = c.downField("checksum")
    val rc = c.downField("rowCount")
    val fn = c.downField("filename")
    val fs = c.downField("fileSize")
    ValidationConfig(
      checksum = ChecksumValidationConfig(
        enabled          = cs.downField("enabled").as[Boolean].getOrElse(false),
        algorithm        = ChecksumAlgorithm.fromString(cs.downField("algorithm").as[String].getOrElse("MD5")),
        source           = ChecksumSource.fromString(cs.downField("source").as[String].getOrElse("DONE_FILE")),
        sidecarExtension = cs.downField("sidecarExtension").as[String].getOrElse(".md5")
      ),
      rowCount = RowCountValidationConfig(
        enabled            = rc.downField("enabled").as[Boolean].getOrElse(false),
        source             = RowCountSource.fromString(rc.downField("source").as[String].getOrElse("DONE_FILE")),
        trailerColumnIndex = rc.downField("trailerColumnIndex").as[Int].getOrElse(0)
      ),
      filename = FilenameValidationConfig(
        enabled        = fn.downField("enabled").as[Boolean].getOrElse(false),
        pattern        = fn.downField("pattern").as[String].getOrElse(".*"),
        dateGroupIndex = fn.downField("dateGroupIndex").as[Int].getOrElse(0),
        dateFormat     = fn.downField("dateFormat").as[String].getOrElse("yyyyMMdd")
      ),
      fileSize = FileSizeValidationConfig(
        enabled  = fs.downField("enabled").as[Boolean].getOrElse(false),
        minBytes = fs.downField("minBytes").as[Long].getOrElse(0L),
        maxBytes = fs.downField("maxBytes").as[Long].getOrElse(Long.MaxValue)
      )
    )
  }

  // ── Destinations ───────────────────────────────────────────────────────────
  private def parseS3Config(destinations: ACursor): Option[S3DestinationConfig] = {
    val s3 = destinations.values.getOrElse(Vector.empty)
      .find(_.hcursor.downField("type").as[String].contains("S3"))
    s3.map { j =>
      val c = j.hcursor
      S3DestinationConfig(
        enabled              = c.downField("enabled").as[Boolean].getOrElse(true),
        bucket               = required(c, "bucket"),
        keyPrefix            = c.downField("keyPrefix").as[String].getOrElse(""),
        keyPattern           = c.downField("keyPattern").as[String].getOrElse("{feedId}/{filename}"),
        storageClass         = c.downField("storageClass").as[String].getOrElse("STANDARD"),
        serverSideEncryption = c.downField("serverSideEncryption").as[String].getOrElse("AES256"),
        kmsKeyId             = c.downField("kmsKeyId").as[String].toOption,
        multipartThresholdMb = c.downField("multipartThresholdMb").as[Int].getOrElse(100)
      )
    }
  }

  private def parseHdfsConfig(destinations: ACursor): Option[HdfsDestinationConfig] = {
    val hdfs = destinations.values.getOrElse(Vector.empty)
      .find(_.hcursor.downField("type").as[String].contains("HDFS"))
    hdfs.map { j =>
      val c = j.hcursor
      HdfsDestinationConfig(
        enabled           = c.downField("enabled").as[Boolean].getOrElse(true),
        namenode          = required(c, "namenode"),
        path              = required(c, "path"),
        replicationFactor = c.downField("replicationFactor").as[Int].getOrElse(3).toShort,
        blockSizeMb       = c.downField("blockSizeMb").as[Long].getOrElse(128L),
        overwrite         = c.downField("overwrite").as[Boolean].getOrElse(true)
      )
    }
  }

  // ── Lifecycle hooks ────────────────────────────────────────────────────────
  private def parseOnSuccess(c: ACursor): OnSuccessConfig = OnSuccessConfig(
    archiveSource = c.downField("archiveSource").as[Boolean].getOrElse(true),
    deleteDoneFile = c.downField("deleteDoneFile").as[Boolean].getOrElse(true),
    writeAuditLog = c.downField("writeAuditLog").as[Boolean].getOrElse(true)
  )

  private def parseOnFailure(c: ACursor): OnFailureConfig = OnFailureConfig(
    moveToErrorDir    = c.downField("moveToErrorDir").as[Boolean].getOrElse(true),
    alertEmail        = c.downField("alertEmail").as[String].toOption,
    maxRetries        = c.downField("maxRetries").as[Int].getOrElse(3),
    retryDelaySeconds = c.downField("retryDelaySeconds").as[Int].getOrElse(60)
  )

  // ── Helpers ────────────────────────────────────────────────────────────────
  private def required(c: ACursor, field: String): String =
    c.downField(field).as[String].getOrElse(
      throw new RuntimeException(s"Missing required config field: $field")
    )
}
