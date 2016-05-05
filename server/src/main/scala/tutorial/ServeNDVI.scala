package tutorial

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.resample._

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.file._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.AttributeStore.Fields

import geotrellis.vector._
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.Polygon._
import geotrellis.vector.reproject._
import geotrellis.proj4._

import akka.actor._
import akka.io.IO
import spray.can.Http
import spray.routing.{HttpService, RequestContext}
import spray.routing.directives.CachingDirectives
import spray.http.MediaTypes
import spray.http._
import spray.http.HttpHeaders._
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._

import tutorial.ParsePolygon._

import org.apache.spark.{SparkConf, SparkContext}

import scala.concurrent._
import com.typesafe.config.ConfigFactory

object ServeNDVI {

  def main(args: Array[String]): Unit = {

    implicit val system = akka.actor.ActorSystem("tutorial")
    //implicit val system = ActorSystem()

    val config = ConfigFactory.load()
    //val staticPath = config.getString("tutorial.server.static-path")
    val staticPath = "../web"
    val port = 8777
    // create and start our service actor
    val service = system.actorOf(Props(classOf[NDVIServiceActor]), "tutorial")

    // start a new HTTP server on port 8080 with our service actor as the handler
    IO(Http) ! Http.Bind(service, "localhost", port = port)
  }
}

class NDVIServiceActor extends Actor with MyService {
  import scala.concurrent.ExecutionContext.Implicits.global

  val sparkConf = new SparkConf()
    .setIfMissing("spark.master", "local[*]")
    .setAppName("Landsat Ingest")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")
    .set("spark.kryoserializer.buffer.max","1gb")
  
  implicit val sparkContext = new SparkContext(sparkConf) 

  def actorRefFactory = context
  def receive = runRoute(serviceRoute)
}


trait MyService extends HttpService {

  // Create a reader that will read in the indexed tiles we produced in IngestImage.
  //lazy val  reader = FileLayerReader[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](catalogPath)
  //lazy val tilereader = FileTileReader[SpatialKey, MultibandTile](catalogPath)
  //lazy val attributeStore = FileAttributeStore(catalogPath)
  //lazy val tileReader = FileTileReader[SpatialKey, Tile](catalogPath)

  lazy val catalogPath = new java.io.File("data/catalog_oneW").getAbsolutePath
  lazy val reader = FileLayerReader(catalogPath)
  lazy val tileReader = FileValueReader(catalogPath)

  implicit val sparkContext: SparkContext
  val layerName = "bel2"
  val baseZoomLevel = 9

  val colorMap = ColorMap(
    Map(
      0.0 -> 0xffffe5ff.int,
      0.1 -> 0xf7fcb9ff.int,
      0.2 -> 0xd9f0a3ff.int,
      0.3 -> 0xaddd8eff.int,
      0.4 -> 0x78c679ff.int,
      0.5 -> 0x41ab5dff.int,
      0.6 -> 0x238443ff.int,
      0.7 -> 0x006837ff.int,
      1.0 -> 0x004529ff.int
    )
  )

  val colorMapNDVI = ColorMap(
    Map(
      0.0 -> 0x010015ff.int,
      0.1 -> 0x010060ff.int,
      0.2 -> 0x2f01faff.int,
      0.3 -> 0xfa00d3ff.int,
      0.4 -> 0xfb0071ff.int,
      0.5 -> 0xfb4321ff.int,
      0.6 -> 0xfae301ff.int,
      0.7 -> 0xa2c800ff.int,
      0.8 -> 0x56c800ff.int,
      0.9 -> 0x04c900ff.int,
      1.0 -> 0x4c4c4cff.int
    )
  )

  val staticPath = "../web"

  // def serviceRoute = get {
  //   pathPrefix("")(root)    
  // }

  def serviceRoute = get {
    pathPrefix("gt") {
      pathPrefix("tms")(tms)
    } ~
    pathPrefix("gt") {
      pathPrefix("sum")(sum)
    } ~
    pathEndOrSingleSlash {
      getFromFile(staticPath + "/index.html")
    } ~
    pathPrefix("") {
      getFromDirectory(staticPath)
    }
  }

  def tms = pathPrefix(IntNumber / IntNumber / IntNumber / Segment) { (zoom, x, y, layer) =>
    respondWithMediaType(MediaTypes.`image/png`) {
      complete {
        // Read in the tile at the given z/x/y coordinates.
        val tile = tileReader.reader[SpatialKey, MultibandTile](LayerId(layer,zoom)).read(x, y)
        // Compute the NDVI
        val ndvi =
          tile.convert(DoubleCellType).combineDouble(0, 3) { (r, ir) =>
            if(isData(r) && isData(ir)) {
              (ir - r) / (ir + r)
            } else {
              Double.NaN
            }
          }
        val ndvi_array = ndvi.toArrayDouble
        val ndvi_number = ndvi_array.sum / ndvi_array.size
        // Render as a PNG
        //println(x,y)
        //println(zoom)
        //println("ndvi_number"+ndvi_number)
        ndvi.renderPng(colorMap).bytes
      }
    }
  }

  def sum =
    parameters('polygon) { (polygonJson) =>
      respondWithMediaType(MediaTypes.`application/json`) {
        respondWithHeader(RawHeader("Access-Control-Allow-Origin","*")){
          import tutorial.GeoJsonProtocol._

          println(polygonJson)
          val poly = {
            val parsed = formPolygon(polygonJson)
            println(parsed)
            Reproject(parsed, LatLng, WebMercator)
          }
          
          println(poly)
          val summary = ModelSpark.summary(layerName, baseZoomLevel, poly)(reader)
          
          complete(JsObject(
            "total" -> "%.2f".format(summary.score * 100).toJson,
            "histogram" -> summary.hist.toJson
          ))
        }
      }   
    }

  //Result of SpaceTimeKey layers types  
  def tms2 = pathPrefix(IntNumber / IntNumber / IntNumber /LongNumber/ Segment) { (zoom, x, y, time, layer) =>
    respondWithMediaType(MediaTypes.`image/png`) {
      complete {
        // Read in the tile at the given z/x/y coordinates and time.
        val key = SpaceTimeKey(x,y,time)
        val tile = tileReader.reader[SpaceTimeKey, MultibandTile](LayerId(layer,zoom)).read(key)
        // Compute the NDVI
        val ndvi =
          tile.convert(DoubleCellType).combineDouble(0, 3) { (r, ir) =>
            if(isData(r) && isData(ir)) {
              (ir - r) / (ir + r)
            } else {
              Double.NaN
            }
          }
        val ndvi_array = ndvi.toArrayDouble
        val ndvi_number = ndvi_array.sum / ndvi_array.size
        // Render as a PNG
        //println(x,y)
        //println(zoom)
        //println("ndvi_number"+ndvi_number)
        ndvi.renderPng(colorMap).bytes
      }
    }
  }

  //Summary result for timed layers
  def sum2 = pathPrefix(Segment / LongNumber) { (polygonJson, time) =>
    respondWithMediaType(MediaTypes.`application/json`) {
      respondWithHeader(RawHeader("Access-Control-Allow-Origin","*")){
        import tutorial.GeoJsonProtocol._

        println(polygonJson)
        val poly = {
          val parsed = formPolygon(polygonJson)
          println(parsed)
          Reproject(parsed, LatLng, WebMercator)
        }
        
        println(poly)
        val summary = ModelSpark.summary(layerName, baseZoomLevel, poly, time)(reader)
        
        complete(JsObject(
          "total" -> "%.2f".format(summary.score * 100).toJson,
          "histogram" -> summary.hist.toJson
        ))
      }
    }   
  }

  // def colors = complete("OK")
}