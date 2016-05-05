package tutorial

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.ingest._
import geotrellis.vector._
import collection.mutable.Buffer

import org.apache.spark.rdd.RDD

case class LayerSummary(name: String, score: Double, hist: List[Double])
case class SummaryResult(layerSummaries: List[LayerSummary], score: Double)

object ModelSpark {
   
	def summary(layer: String, zoom: Int, polygon: Polygon)(reader: FileLayerReader): LayerSummary = {
		val layerId = LayerId(layer, zoom)
		val raster = reader.read[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
    //val extent = Extent(4030983.1236470565, 8061966.247294109, 4070118.8821290657, 8140237.7642581295)
    //val poly = extent.toPolygon()
    val masked = raster.mask(polygon)
    println(masked.metadata)

  	val ndvi_tiles = masked.map{ case(k, tile) =>
      val ndvi =
        tile.convert(DoubleCellType).combineDouble(0, 3) { (r, ir) =>
          if(isData(r) && isData(ir)) {
            ((ir - r)*100) / (ir + r)
          } else {
            Double.NaN
          }
        }
        ndvi
      }
    val (ndvi_coefficient, histogram) = calcHistogram(ndvi_tiles.collect)
    LayerSummary(layer,ndvi_coefficient, histogram)
  }

  // Summary for timed layer
  def summary(layer: String, zoom: Int, polygon: Polygon, time: Long)(reader: FileLayerReader): LayerSummary = {
    val layerId = LayerId(layer, zoom)
    val raster = reader.read[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
    val masked = raster.toSpatial(time).mask(polygon)
    println(masked.metadata)

    val ndvi_tiles = masked.map{ case(k, tile) =>
      val ndvi =
        tile.convert(DoubleCellType).combineDouble(0, 3) { (r, ir) =>
          if(isData(r) && isData(ir)) {
            ((ir - r)*100) / (ir + r)
          } else {
            Double.NaN
          }
        }
        ndvi
      }
    val (ndvi_coefficient, histogram) = calcHistogram(ndvi_tiles.collect)
    LayerSummary(layer,ndvi_coefficient, histogram)
  }

  def calcHistogram(ndvi_tiles:Array[Tile]): (Double, List[Double]) = {
    import collection.mutable.Buffer
    var hist = Buffer[Int](0,0,0)
    val ndvi_ratio = ndvi_tiles.map{ndvi_tile =>
      var sum = 0.0
      var count = 0
      ndvi_tile.foreachDouble{pixel =>
        if(isData(pixel)) {
          // Calc average per each tile
          sum += pixel
          count += 1
          // Calc histogram
          if (pixel < 20) hist(0) +=1
          else if(pixel >=20 && pixel<40) hist(1) +=1
          else hist(2) +=1
        }
      }
      (sum/count,count)
    }
    val ndvi_coefficient = ndvi_ratio.map(x => x._1).reduce(_+_)
    val ndvi_count = ndvi_ratio.map(x => x._2).reduce(_+_)
    val histogram = hist.toList.map(x => x.toDouble/ndvi_count)
    (ndvi_coefficient, histogram)
  }
}
 