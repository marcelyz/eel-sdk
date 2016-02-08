package io.eels.component.hive

import com.sksamuel.scalax.io.Using
import io.eels.{Field, Column, Row, Part, FrameSchema, Source}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.hive.ql.io.orc.{RecordReader, OrcFile}
import scala.collection.JavaConverters._

case class OrcSource(path: Path)(implicit fs: FileSystem) extends Source with Using {

  def createReader: RecordReader = OrcFile.createReader(fs, path).rows()

  override def schema: FrameSchema = {
    using(createReader) { reader =>
      val fields = reader.next(null) match {
        case al: java.util.List[_] => al.asScala.map(_.toString)
        case _ => toString.split(",").toList
      }
      FrameSchema(fields)
    }
  }

  override def parts: Seq[Part] = {

    val part = new Part {

      override def iterator: Iterator[Row] = new Iterator[Row] {

        val reader = OrcFile.createReader(fs, path).rows()
        def close(): Unit = reader.close()

        override def hasNext: Boolean = reader.hasNext
        override def next(): Row = {
          val fields = reader.next(null) match {
            case al: java.util.List[_] => al.asScala.map(_.toString)
            case _ => toString.split(",").toList
          }
          Row(fields.map(Column.apply).toList, fields.map(Field.apply).toList)
        }
      }
    }

    Seq(part)
  }
}
