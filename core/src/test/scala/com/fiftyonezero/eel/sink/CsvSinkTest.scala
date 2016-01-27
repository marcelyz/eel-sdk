package com.fiftyonezero.eel.sink

import java.nio.charset.Charset
import java.nio.file.Files

import com.fiftyonezero.eel.{Frame, Row, Column}
import org.scalatest.{Matchers, WordSpec}
import scala.collection.JavaConverters._

class CsvSinkTest extends WordSpec with Matchers {

  val columns = Seq(Column("a"), Column("b"), Column("c"), Column("d"))
  val frame = Frame(Row(columns, Seq("1", "2", "3", "4")), Row(columns, Seq("5", "6", "7", "8")))

  "CsvSink" should {
    "write csv data" in {
      val temp = Files.createTempFile("csvsink", ".test")
      frame.to(CsvSink(temp))
      val result = Files.readAllLines(temp, Charset.defaultCharset).asScala.toList
      result shouldBe List("1,2,3,4", "5,6,7,8")
    }
    "support setting delimiter" in {
      val temp = Files.createTempFile("csvsink", ".test")
      frame.to(CsvSink(temp, CsvSinkProps(delimiter = '>')))
      val result = Files.readAllLines(temp, Charset.defaultCharset).asScala.toList
      result shouldBe List("1>2>3>4", "5>6>7>8")
    }
  }
}
