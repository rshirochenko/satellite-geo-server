package tutorial

import spray.json._

case class WeatherJson(weather: Weather, clouds: Clouds)
case class Clouds(all:Double)
case class Weather(temp:Double, humidity: Double, pressure: Double)

object WeatherProtocol extends DefaultJsonProtocol {

	implicit object WeatherFormat extends RootJsonFormat[Weather] {
		def write(weatherJson:Weather) = 
			JsObject("temp" -> JsNumber(weatherJson.temp),
					 "humidity" -> JsNumber(weatherJson.humidity),
					 "pressure" -> JsNumber(weatherJson.pressure)
				)
		def read(value:JsValue) = value match {
			case JsObject(fields) => 
				Weather(
					fields.get("temp").get.convertTo[Double],
					fields.get("humidity").get.convertTo[Double],
					fields.get("pressure").get.convertTo[Double]
				)
			case _ => deserializationError("Weather expected")
		}
	} 

	implicit object CloudsFormat extends RootJsonFormat[Clouds] {
		def write(cloudsJson:Clouds) = 
			JsObject("all" -> JsNumber(cloudsJson.all))
		def read(value:JsValue) = value match {
			case JsObject(fields) => 
				Clouds(
					fields.get("all").get.convertTo[Double]
				)
			case _ => deserializationError("Clouds expected")
		}
	} 

	implicit object WeatherDataFormat extends RootJsonFormat[WeatherJson] {
		def write(weatherJson:WeatherJson) = 
			JsObject("weather" -> weatherJson.weather.toJson,
					 "clouds" -> weatherJson.clouds.toJson
					)
		def read(value:JsValue) = value match {
			case JsObject(fields) => 
				WeatherJson(
					fields.get("main").get.convertTo[Weather],
					fields.get("clouds").get.convertTo[Clouds]
				)
			case _ => deserializationError("WeatherJson expected")
		}
	} 
}

object ParseWeather {
	import WeatherProtocol._
	def getWeather(weatherJson: String):(Weather, Clouds) = {
		val weather = weatherJson.parseJson.convertTo[WeatherJson];
		(weather.weather, weather.clouds)
	}
}