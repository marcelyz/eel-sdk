package io.eels.component.hive

import io.eels.schema.PartitionConstraint
import io.eels.schema.PartitionEquals
import io.eels.Source
import io.eels.component.Part
import io.eels.component.Predicate
import io.eels.component.Using
import io.eels.component.parquet.ParquetLogMute
import io.eels.schema.Schema
import io.eels.util.Logging
import io.eels.util.Option
import io.eels.util.findOptional
import io.eels.util.getOrElse
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.hive.metastore.IMetaStoreClient

/**
 * @constraints optional constraits on the partition data to narrow which partitions are read
 * @projection sets which fields are required by the caller.
 * @predicate optional predicate which will filter rows at the read level
 *
 */
data class HiveSource(val dbName: String,
                      val tableName: String,
                      private val constraints: List<PartitionConstraint> = emptyList(),
                      private val projection: List<String> = emptyList(),
                      private val predicate: Option<Predicate> = Option.None,
                      private val fs: FileSystem,
                      private val client: IMetaStoreClient) : Source, Logging, Using {
  init {
    ParquetLogMute()
  }

  val ops = HiveOps(client)

  fun select(vararg columns: String): HiveSource = withProjection(columns.asList())
  fun withProjection(vararg columns: String): HiveSource = withProjection(columns.asList())
  fun withProjection(columns: List<String>): HiveSource {
    require(columns.isNotEmpty())
    return copy(projection = columns)
  }

  fun withPredicate(predicate: Predicate): HiveSource = copy(predicate = Option.Some(predicate))

  fun withPartitionConstraint(name: String, value: String): HiveSource = withPartitionConstraint(PartitionEquals(name, value))

  fun withPartitionConstraint(expr: PartitionConstraint): HiveSource {
    return copy(constraints = constraints.plus(expr))
  }

  /**
   * The returned schema should take into account:
   *
   * 1) Any projection. If a projection is set, then it should return the schema in the same order
   * as the projection. If no projection is set then the schema should be driven from the hive metastore.
   *
   * 2) Any partitions set. These should be included in the schema columns.
   */
  override fun schema(): Schema {

    // if no field names were specified, then we will return the schema as is from the hive database,
    // otherwise we will keep only the requested fields
    val schema = if (projection.isEmpty()) metastoreSchema
    else {
      // remember hive is always lower case, so when comparing requested field names with
      // hive fields we need to use lower case everything. And we need to return the schema
      // in the same order as the requested projection
      val columns = projection.map { fieldName ->
        val field = metastoreSchema.fields.findOptional { it.name.equals(fieldName, true) }
        field.getOrElse(error("Requested field $fieldName does not exist in the hive schema"))
      }
      Schema(columns)
    }

    return schema
  }

  // returns the full underlying schema from the metastore including partition partitionKeys
  val metastoreSchema: Schema = ops.schema(dbName, tableName)

  //fun spec(): HiveSpec = HiveSpecFn.toHiveSpec(dbName, tableName)

  fun isPartitionOnlyProjection(): Boolean {
    val partitionKeyNames = HiveTable(dbName, tableName, fs, client).partitionKeys().map { it.field.name }
    return projection.isNotEmpty() && projection.map { it.toLowerCase() }.all { partitionKeyNames.contains(it) }
  }

  override fun parts(): List<Part> {

    val table = client.getTable(dbName, tableName)
    val dialect = io.eels.component.hive.HiveDialect(table)
    val partitionKeys = HiveTable(dbName, tableName, fs, client).partitionKeys()

    // all field names from the underlying hive schema
    val fieldNames = metastoreSchema.fields.map { it.name }

    // if we requested only partition columns, then we can get this information by scanning the file system
    // to see which partitions have been created. Then the presence of files in a partition location means that
    // that partition must have data.
    return if (isPartitionOnlyProjection()) {
      logger.info("Requested projection contains only partitions; reading directly from metastore")
      // we pass in the schema so we can order the results to keep them aligned with the given projection
      listOf(HivePartitionPart(dbName, tableName, fieldNames, emptyList(), metastoreSchema, predicate, dialect, fs, client))
    } else {
      val files = HiveFilesFn.invoke(table, constraints, partitionKeys, fs, client)
      logger.debug("Found ${files.size} visible hive files from all locations for $dbName:$tableName")

      // for each seperate hive file part we must pass in the metastore schema
      files.map {
        HiveFilePart(dialect, it.first, metastoreSchema, schema(), predicate, it.second.parts, fs)
      }
    }
  }
}