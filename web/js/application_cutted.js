var server = ''

var getLayer = function(url,attrib) {
    return L.tileLayer(url, { maxZoom: 18, attribution: attrib });
};

var Layers = {
    stamen: { 
        toner:  'http://{s}.tile.stamen.com/toner/{z}/{x}/{y}.png',   
        terrain: 'http://{s}.tile.stamen.com/terrain/{z}/{x}/{y}.png',
        watercolor: 'http://{s}.tile.stamen.com/watercolor/{z}/{x}/{y}.png',
        attrib: 'Map data &copy;2013 OpenStreetMap contributors, Tiles &copy;2013 Stamen Design'
    },
    mapBox: {
        azavea:     'http://{s}.tiles.mapbox.com/v3/azavea.map-zbompf85/{z}/{x}/{y}.png',
        worldLight: 'http://c.tiles.mapbox.com/v3/mapbox.world-light/{z}/{x}/{y}.png',
        attrib: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery &copy; <a href="http://mapbox.com">MapBox</a>'
    }
};

var map = (function() {
    var selected = getLayer(Layers.mapBox.azavea,Layers.mapBox.attrib);
    var baseLayers = {
	"Default" : selected,
        "World Light" : getLayer(Layers.mapBox.worldLight,Layers.mapBox.attrib),
        "Terrain" : getLayer(Layers.stamen.terrain,Layers.stamen.attrib),
        "Watercolor" : getLayer(Layers.stamen.watercolor,Layers.stamen.attrib),
        "Toner" : getLayer(Layers.stamen.toner,Layers.stamen.attrib),
    };

    var m = L.map('map');

    m.setView([34.76192255039478,-85.35140991210938], 9);

    selected.addTo(m);

    m.lc = L.control.layers(baseLayers).addTo(m);
    return m;
})()

var weightedOverlay = (function() {
    var layers = "DevelopedLand";

    var layersToWeights = {}

    var breaks = null;
    var WOLayer = null;
    var opacity = 1.0;
    var colorRamp = "blue-to-red";
    var numBreaks = "10";

    update = function() {
        $.ajax({
            url: server + 'gt/breaks',
            data: { 'layers' : "DevelopedLand", 
                    'weights' : "1",
                    'numBreaks': numBreaks},
            dataType: "json",
            success: function(r) {
                //breaks = r.classBreaks;

                if (WOLayer) {
                    map.lc.removeLayer(WOLayer);
                    map.removeLayer(WOLayer);
                }

                // var layerNames = getLayers();
                // if(layerNames == "") return;

                var geoJson = "";
                var polygon = summary.getPolygon();
                if(polygon != null) {
                    geoJson = GJ.fromPolygon(polygon);
                }
                window.alert("Hello")
                WOLayer = new L.TileLayer.WMS(server + "gt/wo", {
                    layers: 'default',
                    format: 'image/png',
                    breaks: "-6,-5,-2,0,1,2,4,5,6,10",
                    transparent: false,
                    layers: "DevelopedLand",
                    weights: "1",
                    colorRamp: "green-to-orange",
                    mask: encodeURIComponent(geoJson),
                    attribution: 'Azavea'
                })

                WOLayer.setOpacity(opacity);
                WOLayer.addTo(map);
                map.lc.addOverlay(WOLayer, "Weighted Overlay");
            }
        });
    };

    return {
        setOpacity: function(o) { update() },
        update: update,
        getColorRamp: function() {return colorRamp; },
        getMapLayer: function() { return WOLayer; }
    };

})();

var summary = (function() {
    var polygon = null;
    var layers = "DevelopedLand";
    var weights = "1";
    var update = function(switchTab) {
        if(polygon != null) {
            // if(weightedOverlay.activeLayers().length == 0) {
            //     $("#summary-data").empty();                
            //     return;
            // };

            var geoJson = GJ.fromPolygon(polygon);
            // window.alert(weightedOverlay.activeLayers())
            $.ajax({        
                url: server + 'gt/sum',
                data: { polygon : geoJson, 
                        layers  : "DevelopedLand", 
                        weights : "-6,-5,-2,0,1,2,4,5,6,10"
                      },
                dataType: "json",
                success : function(data) {
                     $('.results').html("OK");
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

// Set up from config
$.getJSON('config.json', function(data) {
    weightedOverlay.setOpacity(data.weightedOverlay.opacity);
});

var setupSize = function() {
    var bottomPadding = 10;

    var resize = function(){
        var pane = $('#main');
        var height = $(window).height() - pane.offset().top - bottomPadding;
        pane.css({'height': height +'px'});

        var sidebar = $('#tabBody');
        var height = $(window).height() - sidebar.offset().top - bottomPadding;
        sidebar.css({'height': height +'px'});

        var mapDiv = $('#map');
		var wrapDiv = $('#wrap');
        var height = $(window).height() - mapDiv.offset().top - bottomPadding - wrapDiv.height();
        mapDiv.css({'height': height +'px'});
        map.invalidateSize();
    };
    resize();
    $(window).resize(resize);
};

// On page load
$(document).ready(function() {
    // Set heights

    weightedOverlay.bindSliders();
    colorRamps.bindColorRamps();

    $('#clearButton').click( function() {
        window.alert("Pressed")
        summary.clear();
        return false;
    });
    setupSize();
});
