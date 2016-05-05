package geotrellis.testim

import geotrellis.raster._
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.raster.io.geotiff._
import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.tiling._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io._
import geotrellis.spark.pyramid._
import geotrellis.proj4._

import org.apache.commons.io.filefilter._

import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io._

object LandsatIngestTimed {
  val catalogPath = "data/bel2timed"

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setIfMissing("spark.master", "local[4]")
        .setAppName("Landsat Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")

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

    // Manually set the image directories
    val images =
      Array[String](
        "data/bel2/LC81800192016108LGN00",
        "data/bel2/LC81780222014152LGN00"
        //"data/bel2/LC81810242014157LGN00"
        //"data/bel2/LE71800222014158SG100",
        //"data/bel2/LE71800232014158SG100",
        //"data/bel2/LE71800242014158SG100"
    )

    try {
      run("bel2", images, bands)
      println("Hit enter to exit.")
      readLine()
    } finally {
      sc.stop()
    }
  }

  def fullPath(path: String) = new java.io.File(path).getAbsolutePath

  def findMTLFile(imagePath: String): String =
    new File(imagePath).listFiles(new WildcardFileFilter(s"*_MTL.txt"): FileFilter).toList match {
      case Nil => sys.error(s"MTL data not found for image at ${imagePath}")
      case List(f) => f.getAbsolutePath
      case _ => sys.error(s"Multiple files matching band MLT file found for image at ${imagePath}")
    }

  def findBandTiff(imagePath: String, band: String): String =
    new File(imagePath).listFiles(new WildcardFileFilter(s"*_B${band}.TIF"): FileFilter).toList match {
      case Nil => sys.error(s"Band ${band} not found for image at ${imagePath}")
      case List(f) => f.getAbsolutePath
      case _ => sys.error(s"Multiple files matching band ${band} found for image at ${imagePath}")
    }

  def readBands(imagePath: String, bands: Array[String]): (MTL, MultibandGeoTiff) = {
    // Read time out of the metadata MTL file.
    val mtl = MTL(findMTLFile(imagePath))

    val bandTiffs =
      bands
        .map { band =>
          SinglebandGeoTiff(findBandTiff(imagePath, band))
        }

    (mtl, MultibandGeoTiff(ArrayMultibandTile(bandTiffs.map(_.tile)), bandTiffs.head.extent, bandTiffs.head.crs))
  }

  def run(layerName: String, images: Array[String], bands: Array[String])(implicit sc: SparkContext): Unit = {
    val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256)
    // Create tiled RDDs for each
    val tileSets =
      images.foldLeft(Seq[(Int, MultibandTileLayerRDD[SpaceTimeKey])]()) { (acc, image) =>
        val (mtl, geoTiff) = readBands(image, bands)
        val ingestElement = (TemporalProjectedExtent(geoTiff.extent, geoTiff.crs, mtl.dateTime), geoTiff.tile)
        val sourceTile = sc.parallelize(Seq(ingestElement))
        val (_, metadata) =
          TileLayerMetadata.fromRdd[TemporalProjectedExtent, MultibandTile, SpaceTimeKey](sourceTile, FloatingLayoutScheme(512))
        val tiled =
          sourceTile
            .tileToLayout[SpaceTimeKey](metadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))
        val rdd = MultibandTileLayerRDD(tiled, metadata)

        // Reproject to WebMercator
        acc :+ rdd.reproject(targetLayoutScheme, bufferSize = 30, Reproject.Options(method = Bilinear, errorThreshold = 0))
      }

    val zoom = tileSets.head._1
    val rdd = ContextRDD(
      sc.union(tileSets.map(_._2)),
      tileSets.map({ case (_, rdd) => rdd.metadata}).reduce({ _ merge _ }) // These are all of the same zoom level
    )

    // Write to the catalog
    val attributeStore = FileAttributeStore(catalogPath)
    val writer = FileLayerWriter(attributeStore)
    val method = ZCurveKeyIndexMethod.byDay

    val lastRdd =
        Pyramid.upLevels(rdd, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
        writer.write(LayerId(layerName, zoom), rdd, method)
      }

    attributeStore.write(LayerId(layerName, 0), "times", lastRdd.map(_._1.instant).collect.toArray)
  }
}