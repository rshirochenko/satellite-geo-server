package geotrellis.ingestion

import geotrellis.raster._
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader

import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.tiling._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.json._
//import geotrellis.spark.reproject._
import geotrellis.spark.tiling._
import geotrellis.spark.render._
import geotrellis.spark.io._
import geotrellis.spark.pyramid._

import geotrellis.proj4._
import geotrellis.vector.ProjectedExtent

import org.apache.commons.io.filefilter._
import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io._
import java.io.File

/** Ingestion of Landsat image to RDD and writing by tiles on HDFS
 *
 *  The Landsat imagery should be pre-processed in order to operate
 *  on Spark cluster
*/
object IngestLandsatImage {
  //val inputPath = new File("data/r-nir.tif").getAbsolutePath
  val catalogPath = "data/catalog_oneL"

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf = new SparkConf()
        .setIfMissing("spark.master", "local[*]")
        .setAppName("Landsat Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")
        .set("spark.kryoserializer.buffer.max","1gb")

    implicit val sc = new SparkContext(conf)

    // Manually set bands
    val bands =
      Array[String](
        // Red, Green, Blue
        "4","3", "2",
        // Near IR
        "5"
        //"7",
        //"QA"
      )

    val images =
      Array[String](
        "data/bel2/LC81800192016108LGN00"
        //"data/bel2/LC81780222014152LGN00"
        //"data/bel2/LC81810242014157LGN00"
        //"data/bel2/LE71800222014158SG100",
        //"data/bel2/LE71800232014158SG100",
        //"data/bel2/LE71800242014158SG100"
      )
    
    try {
      run("bel2", images, bands)
      // Pause to wait to close the spark context,
      println("Hit enter to exit.")
      readLine()
    } finally {
      sc.stop()
    }
  }

  /** Get file directory path */
  def fullPath(path: String) = new java.io.File(path).getAbsolutePath

  /** Get images band from multiband Tiff file. */
  def findBandTiff(imagePath: String, band: String): String =
    new File(imagePath).listFiles(new WildcardFileFilter(s"*_B${band}.TIF"): FileFilter).toList match {
      case Nil => sys.error(s"Band ${band} not found for image at ${imagePath}")
      case List(f) => {
        println(f.getAbsolutePath)
        f.getAbsolutePath
     }
     case _ => sys.error(s"Multiple files matching band ${band} found for image at ${imagePath}")
  }

  /** Read data from Tiff Landsat File */
  def readBands(imagePath: String, bands: Array[String]): MultibandGeoTiff = {
    val bandTiffs = bands.map { band => SinglebandGeoTiff(findBandTiff(imagePath, band)) }
    MultibandGeoTiff(ArrayMultibandTile(bandTiffs.map(_.tile)), bandTiffs.head.extent, bandTiffs.head.crs)
  }

  /** Reporject to tiles and save on HDFS disk*/ 
  def run(layerName: String, images: Array[String], bands: Array[String])(implicit sc: SparkContext): Unit = {
    val targetLayoutScheme = ZoomedLayoutScheme(LatLng, 256)
    // Create tiled RDDs for each
    val tileSets =
      images.foldLeft(Seq[(Int, MultibandTileLayerRDD[SpatialKey])]()) { (acc, image) =>
        val geoTiff = readBands(image, bands)
        val ingestElement = (ProjectedExtent(geoTiff.extent, geoTiff.crs), geoTiff.tile)
        val sourceTile = sc.parallelize(Seq(ingestElement))
        val (_, tileLayerMetadata: TileLayerMetadata[SpatialKey]) = TileLayerMetadata.fromRdd[ProjectedExtent, MultibandTile, SpatialKey](sourceTile, FloatingLayoutScheme(512))
        
        val tiled =
          sourceTile
            .tileToLayout[SpatialKey](tileLayerMetadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))

        val rdd = MultibandTileLayerRDD(tiled, tileLayerMetadata)

        // Reproject to WebMercator
        acc :+ rdd.reproject(targetLayoutScheme, bufferSize = 30, Reproject.Options(method = Bilinear, errorThreshold = 0))
      }

    val zoom = tileSets.head._1
    val rdd = ContextRDD(
      sc.union(tileSets.map(_._2)),
      tileSets.map(_._2.metadata).reduce(_ merge _)
    )

    // Write to the catalog
    val attributeStore = FileAttributeStore(catalogPath)
    val writer = FileLayerWriter(attributeStore)
    val keyIndex = ZCurveKeyIndexMethod

    // Pyramiding up the zoom levels, write our tiles out to the local file system.
    val lastRdd =
       Pyramid.upLevels(rdd, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
       writer.write(LayerId(layerName, zoom), rdd, keyIndex)
    }
    //attributeStore.write(LayerId(layerName, 0), "times", lastRdd.map(_._1.instant).collect.toArray)

    println("Done")
  }
}