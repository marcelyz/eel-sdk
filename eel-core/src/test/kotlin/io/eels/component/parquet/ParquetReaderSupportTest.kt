package io.eels.component.parquet

import java.util.UUID

import io.eels.schema.Column
import io.eels.schema.ColumnType
import io.eels.schema.Schema
import io.kotlintest.specs.WordSpec
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.parquet.avro.AvroParquetWriter

@Suppress("NAME_SHADOWING")
class ParquetReaderSupportTest : WordSpec() {

  val path = Path(UUID.randomUUID().toString())

  override fun afterAll(): Unit {
    val fs = FileSystem.get(Configuration())
    fs.delete(path, false)
  }

  init {

    val avroSchema = SchemaBuilder.record("com.chuckle").fields()
        .requiredString("str").requiredLong("looong").requiredDouble("dooble").endRecord()

    val writer = AvroParquetWriter.builder<GenericRecord>(path)
        .withSchema(avroSchema)
        .build()

    val record = GenericData.Record(avroSchema)
    record.put("str", "wibble")
    record.put("looong", 999L)
    record.put("dooble", 12.34)
    writer.write(record)
    writer.close()

    val schema = Schema(Column("str"), Column("looong", ColumnType.Long, true), Column("dooble", ColumnType.Double, true))

    "ParquetReaderSupport" should     {
      "support projections on doubles" with {

        val reader = ParquetReaderSupport.create(path, true, null, schema.removeColumn("looong"))
        val record = reader.read()
        reader.close()

        record.get("str").toString() shouldBe "wibble"
        record.get("dooble") shouldBe 12.34
      }
      "support projections on longs" with {

        val reader = ParquetReaderSupport.create(path, true, null, schema.removeColumn("str"))
        val record = reader.read()
        reader.close()

        record.get("looong") shouldBe 999L
      }
      "support full projections" with {

        val reader = ParquetReaderSupport.create(path, true, null, schema)
        val record = reader.read()
        reader.close()

        record.get("str").toString() shouldBe "wibble"
        record.get("looong") shouldBe 999L
        record.get("dooble") shouldBe 12.34

      }
      "support non projections" with {

        val reader = ParquetReaderSupport.create(path, false, null, null)
        val record = reader.read()
        reader.close()

        record.get("str").toString() shouldBe "wibble"
        record.get("looong") shouldBe 999L
        record.get("dooble") shouldBe 12.34

      }
    }
  }
}
