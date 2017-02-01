package geotrellis.ingestion

import geotrellis.raster.Tile
import geotrellis.spark.SpatialKey
import geotrellis.spark.etl.Etl
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.accumulo._
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.util.SparkUtils
import geotrellis.spark.ingest._
import geotrellis.vector.ProjectedExtent
import org.apache.spark.SparkConf
import geotrellis.spark.pyramid._

/** Testing the Ingestion on the running cluster 
**/
object TestIngest extends App {
  implicit val sc = SparkUtils.createSparkContext("GeoTrellis ETL SinglebandIngest", new SparkConf(true))
  Etl.ingest[ProjectedExtent, SpatialKey, Tile](args, ZCurveKeyIndexMethod)
  sc.stop()
}