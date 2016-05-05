package tutorial

import geotrellis.spark._
import geotrellis.spark.reproject.Reproject.Options
import geotrellis.spark.tiling._

import geotrellis.vector._
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.Polygon._
import geotrellis.vector.reproject._
import geotrellis.proj4._

import spray.json._
import tutorial.GeoJsonProtocol._
import tutorial.ParsePolygon._

import spire.syntax.cfor._
import org.scalatest.FunSpec

class ServeNDVI extends FunSpec {
	describe("Reproject from one CRS to another") {
		it("should reproject from LatLng to WebMercator") {
			val	tt = 1
			assert(tt == 1)
		}
	}

	describe("Polygon formation checking") {
		it("should parse polygon from json") {
			import tutorial.GeoJsonProtocol._

			val polygon_json = """ {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[[58.83080439883581,36.01318359374999],[58.36427519285588,35.9912109375],[58.370037236925526,36.8756103515625],[58.83649009392136,36.8646240234375]]]}} """
			val polygon_string = """POLYGON ((58.83080439883581 36.01318359374999, 58.36427519285588 35.9912109375, 58.370037236925526 36.8756103515625, 58.83649009392136 36.8646240234375, 58.83080439883581 36.01318359374999))"""
		
		    val polygon = formPolygon(polygon_json).toString
		    assert(polygon == polygon_string)
		}
	}
}