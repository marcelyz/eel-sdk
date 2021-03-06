package io.eels.component.parquet

import io.eels.Row
import io.eels.schema.StructType
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.parquet.column.ParquetProperties
import org.apache.parquet.hadoop.api.WriteSupport
import org.apache.parquet.hadoop.{ParquetFileWriter, ParquetWriter}
import org.apache.parquet.schema.MessageType

import scala.math.BigDecimal.RoundingMode.RoundingMode

/**
  * Helper function to create a native ParquetWriter for Row objects using the apache parquet library.
  * Uses config keys to support compression codec, page size, and block size.
  *
  * @param path     the path to save the file at
  * @param schema   the schema to use for this file. This schema is the one that will be set, regardless
  *                 of the schema set in the data.
  * @param metadata extra metadata to add to the parquet file when it is closed
  */
object RowParquetWriterFn {

  class RowParquetWriterBuilder(path: Path,
                                schema: MessageType,
                                roundingMode: RoundingMode,
                                metadata: Map[String, String])
    extends ParquetWriter.Builder[Row, RowParquetWriterBuilder](path) {
    override def getWriteSupport(conf: Configuration): WriteSupport[Row] = new RowWriteSupport(schema, roundingMode, metadata)
    override def self(): RowParquetWriterBuilder = this
  }

  def apply(path: Path,
            schema: StructType,
            metadata: Map[String, String],
            dictionary: Boolean,
            roundingMode: RoundingMode,
            fsConfig: Configuration): ParquetWriter[Row] = {
    val config = ParquetWriterConfig()
    val messageType = ParquetSchemaFns.toParquetMessageType(schema)
    new RowParquetWriterBuilder(path, messageType, roundingMode, metadata)
      .withCompressionCodec(config.compressionCodec)
      .withDictionaryEncoding(dictionary)
      .withPageSize(config.pageSize)
      .withRowGroupSize(config.blockSize)
      .withValidation(config.validating)
      .withWriteMode(ParquetFileWriter.Mode.CREATE)
      .withWriterVersion(ParquetProperties.WriterVersion.PARQUET_1_0)
      .withConf(fsConfig)
      .build()
  }
}