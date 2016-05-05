var map = L.map('world-map-markers');
var server = 'http://localhost:8777/';

map.setView([58.22,35.51], 7, L.CRS.EPSG26915);

new L.tileLayer("http://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}.png").addTo(map);
// new L.tileLayer(server + "gt/tms/{z}/{x}/{y}", {layers: 'default', maxZoom: 13}).addTo(map);
// var layer = new L.tileLayer(server + "gt/tms/{z}/{x}/{y}", {layers: 'default', format:'image/png', maxZoom:13}).addTo(map)
// layer.setOpacity(0.3)
    
var drawing = (function() {
    var drawnItems = new L.FeatureGroup();
    map.addLayer(drawnItems);

    var drawOptions = {
        draw: {
      position: 'topleft',
            marker: false,
            polyline: false,
            rectangle: false,
            circle: false,
      polygon: {
          title: 'Draw a polygon for summary information.',
          allowIntersection: false,
          drawError: {
        color: '#b00b00',
        timeout: 1000
          },
          shapeOptions: {
        color: '#338FF2'
          }
      },
        },
        edit: false
    };

    var drawControl = new L.Control.Draw(drawOptions);
    map.addControl(drawControl);

    map.on('draw:created', function (e) {
        if (e.layerType === 'polygon') {
            summary.setPolygon(e.layer);
        }
    });

    map.on('draw:edited', function(e) {
        var polygon = summary.getPolygon();
        if(polygon != null) { 
            summary.update();
            weightedOverlay.update();
        }
    });

    map.on('draw:drawstart', function(e) {
        var polygon = summary.getPolygon();
        if(polygon != null) { drawnItems.removeLayer(polygon); }
    });

    map.on('draw:drawstop', function(e) {
        drawnItems.addLayer(summary.getPolygon());
    });

    return {
        clear: function(polygon) {
            drawnItems.removeLayer(polygon);
        }
    }
})();

var summary = (function() {
    var polygon = null;
    var update = function(switchTab) {
        if(polygon != null) {
            var geoJson = GJ.fromPolygon(polygon);
            $.ajax({        
                url: server + 'gt/sum',
                data: { polygon : geoJson},
                dataType: "json",
                success : function(jsondata) {
                    var sdata = $(".ndvi_polygon");
                    sdata.empty();
                    sdata.append(jsondata.total);
                    setHistogram(jsondata.histogram)
                }
            });
        }
    };
    return {
        getPolygon: function() { return polygon; },
        setPolygon: function(p) { 
            polygon = p; 
            weightedOverlay.update();
            update(true);
        },
        update: update,
        clear: function() {
            if(polygon) {
                drawing.clear(polygon);
                polygon = null;
                weightedOverlay.update();
            }
        }
    };
})();

var weightedOverlay = (function() {
    var layer = null;
    update = function() {
        var geoJson = "";
        var polygon = summary.getPolygon();
        if(polygon != null) {
            geoJson = GJ.fromPolygon(polygon);
            summary.update();
        }

        WOLayer = new L.tileLayer(server + "gt/tms/{z}/{x}/{y}/"+layer, {
            layers: 'default',
            format: 'image/png',
            mask: encodeURIComponent(geoJson),
            maxZoom:13
        });
    
        WOLayer.setOpacity(1);
        WOLayer.addTo(map);
    };

    return {
        update: update,
        setMapLayer: function(l) { 
            //map.clearLayers();
            layer = l;
        },
        getMapLayer: function() { return WOLayer; }
    };})();



$(document).ready(function(){
    
    weightedOverlay.setMapLayer("bel2");
    weightedOverlay.update();   

    $("#calendar").datepicker("setDate", new Date('04/01/2016'));

    $('#clear').click( function() {
        summary.clear();
        return false;
    });

    $('#calendar').on("changeDate", function() {
       var date = $("#calendar").data('datepicker').getFormattedDate('dd-mm-yyyy');
       var layer = "bel"+date;
       summary.clear();
       weightedOverlay.setMapLayer(layer);
       weightedOverlay.update();
    });   

    $.ajax({ 
    type: 'GET', 
    url: 'http://api.openweathermap.org/data/2.5/weather', 
    data: {
        lat : '59.460317',
        lon : '33.327316',
        appid: '8a24a1ede0c1a7d4ab7979b88d1c7f21'
    },
    dataType: 'json',
    success: function (jsondata) { 
        // Set tempurature
        var temp = $(".temp");
        temp.empty();
        var temp_value = (jsondata.main.temp - 273.0).toFixed(1);
        temp.append(temp_value);
        
        //Set humidity
        var humidity = $(".humidity");
        humidity.empty();
        humidity.append(jsondata.main.humidity);
     
        //Set Clouds
        var clouds = $(".clouds");
        clouds.empty();
        clouds.append(jsondata.clouds.all);
    }
});

});

// Add fields polygon

var field1 = L.polygon([
    [58.62821409743693,35.474853515625],
    [58.46339501539113,35.6011962890625],
    [58.464831567073276,35.8099365234375],
    [58.56524441881396,35.97747802734375],
    [58.70234405922001,35.77148437499999]
]).addTo(map);

var field2 = L.polygon([
    [58.679507683917066,36.3702392578125],
    [58.50488769336882,36.441650390625],
    [58.59087529884809,37.07336425781249],
    [58.79354000675436,37.0843505859375],
    [58.85609856470015,36.7327880859375]    
]).addTo(map);

var field3 = L.polygon([
    [58.2254144155825,35.5133056640625],
    [58.06887085748643,35.6341552734375],
    [58.10371782154322,35.9033203125],
    [58.236982863732415,35.9417724609375]
]).addTo(map);

var field4 = L.polygon([
    [58.24536761265225,36.353759765625],
    [58.01915509422909,36.58447265625],
    [58.106329954416054,37.254638671875],
    [58.222232413411284,37.364501953125],
    [58.389621116066614,36.6943359375]
]).addTo(map);

var field5 = L.polygon([
    [58.52497034170799,37.2491455078125],
    [58.38702976921042,37.232666015625],
    [58.349577704578856,37.44140625],
    [58.40430193555239,37.5897216796875],
    [58.596600306481214,37.4249267578125]
]).addTo(map);

field1.bindPopup("Field #1");
field2.bindPopup("Field #2");
field3.bindPopup("Field #3");
field4.bindPopup("Field #4");
field5.bindPopup("Field #5");

// On map fields click handlers
field1.on('click',function(e) {
    summary.clear();
    this.openPopup();
    summary.setPolygon(this);
    return false;
});

field2.on('click',function(e) {
    summary.clear();
    this.openPopup();
    summary.setPolygon(this);
    return false;
});

field3.on('click',function(e) {
    summary.clear();
    this.openPopup();
    summary.setPolygon(this);
    return false;
});

field4.on('click',function(e) {
    summary.clear();
    this.openPopup();
    summary.setPolygon(this);
    return false;
});

field5.on('click',function(e) {
    summary.clear();
    this.openPopup();
    summary.setPolygon(this);
    return false;
});
// Add coordinate show pop-up
// var popup = L.popup();
// function onMapClick(e) {
//     popup
//         .setLatLng(e.latlng)
//         .setContent("Coordinate " + e.latlng)
//         .openOn(map);
// }
// map.on('click', onMapClick);

// Fields table functions
$(document).ready(function(){
    $('#field1').click(function(){
        summary.clear();
        field1.openPopup();
        summary.setPolygon(field1);
        return false;
    });
    $('#field2').click(function(){
        summary.clear();
        field2.openPopup();
        summary.setPolygon(field2);
        return false;
    });
    $('#field3').click(function(){
        summary.clear();
        field3.openPopup();
        summary.setPolygon(field3);
        return false;
    });
    $('#field4').click(function(){
        summary.clear();
        field4.openPopup();
        summary.setPolygon(field4);
        return false;
    });
    $('#field5').click(function(){
        summary.clear();
        field5.openPopup();
        summary.setPolygon(field5);
        return false;
    });
});

//-------------
  //- PIE CHART -
  //-------------
  // Get context with jQuery - using jQuery's .get() method.
  var pieChartCanvas = $("#pieChart").get(0).getContext("2d");
  var pieChart = new Chart(pieChartCanvas);
  var PieData = [
    {
      value: histogram[0],
      color: "#f56954",
      highlight: "#f56954",
      label: "Soil"
    },
    {
      value: histogram[1],
      color: "#00a65a",
      highlight: "#00a65a",
      label: "Normal"
    },
    {
      value: histogram[2],
      color: "#f39c12",
      highlight: "#f39c12",
      label: "Deficient"
    }
  ];
  var pieOptions = {
    inGraphDataShow: true,  
    //Boolean - Whether we should show a stroke on each segment
    segmentShowStroke: true,
    //String - The colour of each segment stroke
    segmentStrokeColor: "#fff",
    //Number - The width of each segment stroke
    segmentStrokeWidth: 1,
    //Number - The percentage of the chart that we cut out of the middle
    percentageInnerCutout: 50, // This is 0 for Pie charts
    //Number - Amount of animation steps
    //animationSteps: 100,
    //String - Animation easing effect
    //animationEasing: "easeOutBounce",
    //Boolean - Whether we animate the rotation of the Doughnut
    //animateRotate: false,
    //Boolean - Whether we animate scaling the Doughnut from the centre
    //animateScale: false,
    //Boolean - whether to make the chart responsive to window resizing
    responsive: true,
    // Boolean - whether to maintain the starting aspect ratio or not when responsive, if set to false, will take up entire container
    maintainAspectRatio: false,
    //String - A legend template
    legendTemplate: "<ul class=\"<%=name.toLowerCase()%>-legend\"><% for (var i=0; i<segments.length; i++){%><li><span style=\"background-color:<%=segments[i].fillColor%>\"></span><%if(segments[i].label){%><%=segments[i].label%><%}%></li><%}%></ul>",
    //String - A tooltip template
    tooltipTemplate: "<%=value %> <%=label%> users"
  };
  //Create pie or douhnut chart
  // You can switch between pie and douhnut using the method below.
  pieChart.Doughnut(PieData, pieOptions);
  //-----------------
  //- END PIE CHART -
  //-----------------

function setHistogram(histogram) {
    var soilData = (histogram[0]*100.0).toFixed(2);
    var normalData = (histogram[1]*100.0).toFixed(2);
    var deficientData = (histogram[2]*100.0).toFixed(2);

    var PieData = [
        {
           value: soilData,
           color: "#f56954",
           highlight: "#f56954",
           label: "Soil"
        },
        {
            value: normalData,
            color: "#00a65a",
            highlight: "#00a65a",
            label: "Normal"
        },
        {
            value: deficientData,
            color: "#f39c12",
            highlight: "#f39c12",
            label: "Deficient"
        }
    ];
    pieChart.Doughnut(PieData, pieOptions);

    //Soil data values
    var soilDiv = $(".soil_data");
    soilDiv.empty();
    soilDiv.append(soilData);
    //Normal data values
    var normalDiv = $(".normal_data");
    normalDiv.empty();
    normalDiv.append(normalData);
    //Deficient data values
    var deficientDiv = $(".deficient_data");
    deficientDiv.empty();
    deficientDiv.append(deficientData);
    return false;
};
