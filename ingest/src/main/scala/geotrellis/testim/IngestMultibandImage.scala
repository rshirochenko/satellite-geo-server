package geotrellis.testim

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.raster.reproject._
import geotrellis.proj4._

import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.json._
import geotrellis.spark.io._
import geotrellis.spark.ingest._
import geotrellis.spark.reproject._
import geotrellis.spark.tiling._
import geotrellis.spark.render._
import geotrellis.spark.pyramid._

import geotrellis.vector._

import org.apache.spark._
import org.apache.spark.rdd._

import java.io.File

object IngestMultibandImage {
  val inputPath = new File("data/bel-r-nir/r-nir1.tif").getAbsolutePath
  val catalogPath = "data/catalog_bel1"

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf = new SparkConf()
        .setIfMissing("spark.master", "local[*]")
        .setAppName("Landsat Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")
        .set("spark.kryoserializer.buffer.max","1gb")

    implicit val sc = new SparkContext(conf)
    
    try {
      run(sc)
      // Pause to wait to close the spark context,
      // so that you can check out the UI at http://localhost:4040
      println("Hit enter to exit.")
      readLine()
    } finally {
      sc.stop()
    }
  }

  def fullPath(path: String) = new java.io.File(path).getAbsolutePath

  def run(implicit sc: SparkContext):Unit = {
      
    val geoTiff = MultibandGeoTiff(inputPath)   
    val ingestElement = (ProjectedExtent(geoTiff.extent, geoTiff.crs), geoTiff.tile)
    val sourceTile = sc.parallelize(Seq(ingestElement))

    val (_, rasterMetaData: TileLayerMetadata[SpatialKey]) =
      TileLayerMetadata.fromRdd[ProjectedExtent, MultibandTile, SpatialKey](sourceTile, FloatingLayoutScheme(512))

    val tiled: RDD[(SpatialKey, MultibandTile)] = sourceTile.tileToLayout(rasterMetaData, Bilinear)

    // We'll be tiling the images using a zoomed layout scheme
    // in the web mercator format (which fits the slippy map tile specification).
    // We'll be creating 256 x 256 tiles.
    val layoutScheme = ZoomedLayoutScheme(WebMercator, tileSize = 256)

    // We need to reproject the tiles to WebMercator
    val (zoom, reprojected) = MultibandTileLayerRDD(tiled, rasterMetaData).reproject(WebMercator, layoutScheme, Bilinear)

    // Create the writer that we will use to store the tiles in the local catalog.
    val attributeStore = FileAttributeStore(catalogPath)
    val writer = FileLayerWriter(attributeStore)
    val keyIndex = ZCurveKeyIndexMethod

    // Pyramiding up the zoom levels, write our tiles out to the local file system.
    val writeOp =
      Pyramid.upLevels(reprojected, layoutScheme, zoom, Bilinear) { (rdd, z) =>
        writer.write[SpatialKey,MultibandTile,TileLayerMetadata[SpatialKey]](LayerId("belor", z), rdd, keyIndex)
      }

    println("Done!")
  }
}
