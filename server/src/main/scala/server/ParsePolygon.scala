package server

import spray.json._

import geotrellis.vector.Polygon._
import geotrellis.vector._
import geotrellis.vector.io.json.Implicits._

case class PolyJson(`type`: String, properties: String, geometry: Geometry)
case class Geometry(`type`: String, coordinates: Seq[Seq[Coordinate]])
case class Coordinate(x: Double, y: Double)

/** GeoJson output result representation
 *
*/
object GeoJsonProtocol extends DefaultJsonProtocol {

	implicit object CoordinateFormat extends JsonFormat[Coordinate] {
	    def write(obj: Coordinate): JsValue = JsArray(
	      JsNumber(obj.x),
	      JsNumber(obj.y)
	    )

	    def read(json: JsValue): Coordinate = json match {
	      case JsArray(is) if is.length == 2 =>
	        Coordinate(is(0).convertTo[Double], is(1).convertTo[Double])
	      case _ => deserializationError(s"'$json' is not a valid Coordinate")
	    }
  	}

	implicit object GeometryFormat extends RootJsonFormat[Geometry] {
		def write(geometry:Geometry) = 
			JsObject("type" -> JsString(geometry.`type`),
					 "geometry" -> geometry.coordinates.toJson
			)
		def read(value:JsValue) = value match {
			case JsObject(fields) =>
				Geometry(
					fields.get("type").get.toString(),
					fields.get("coordinates").get.convertTo[Seq[Seq[Coordinate]]]
				)
			case _ => deserializationError("Geometry expected")
		}
	}

	implicit object PolygonFormat extends RootJsonFormat[PolyJson] {
		def write(poly:PolyJson) = 
			JsObject("type" -> JsString(poly.`type`),
					 "properties" -> Option(poly.properties).map(JsString(_)).getOrElse(JsNull),
					 "geometry" -> poly.geometry.toJson)
		def read(value:JsValue) = value match {
			case JsObject(fields) => 
				val propertiesOpt:Option[String] = fields.get("properties").map(_.toString())
				val properties:String = propertiesOpt.orNull[String]
				PolyJson(
					fields.get("type").get.toString(),
					fields.get("properties").get.toString(), 
					fields.get("geometry").get.convertTo[Geometry]
				)
			case _ => deserializationError("PolyJson expected")
		}
	} 
}

/** Getting the polygon class from GeoJSON HTTP response
*
*/
object ParsePolygon {
	import GeoJsonProtocol._

	def formPolygon(polygonJson:String):Polygon = {
		val polygon = polygonJson.parseJson.convertTo[PolyJson]
		val coords = polygon.geometry.coordinates.flatMap(x => x)
		val points = coords.map(coord => Point(coord.y.toDouble, coord.x.toDouble))
		val points2 = points :+ points(0)
		Polygon(points2)
	}
}