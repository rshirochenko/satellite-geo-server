#!/usr/bin/env bash

realpath ()
{
    f=$@;
    if [ -d "$f" ]; then
        base="";
        dir="$f";
    else
        base="/$(basename "$f")";
        dir=$(dirname "$f");
    fi;
    dir=$(cd "$dir" && /bin/pwd);
    echo "$dir$base"
}

# Ingest tiled GeoTiff into Accumulo

# Geotrellis (gt-admin) ingest jar
export JAR="target/scala-2.10/TestProject-assembly-0.1-SNAPSHOT.jar"

# Directory with the input tiled GeoTiff's
LAYERS="/home/rshir/landsat/arg_wm"
OUTPUT="/home/rshir/development/project_v.2.0/ingest/data/catalog_arg"

# Table to store tiles
TABLE="test_table3"

# Destination spatial reference system
CRS="EPSG:3857"

LAYOUT_SCHEME="tms"

# Accumulo conf
INSTANCE="test"
USER="root"
PASSWORD="zxczxc"
ZOOKEEPER="localhost"

# Remove some bad signatures from the assembled JAR
zip -d $JAR META-INF/ECLIPSEF.RSA > /dev/null
zip -d $JAR META-INF/ECLIPSEF.SF > /dev/null

# Go through all layers and run the spark submit job
for LAYER in $(ls $LAYERS)
do

  LAYERNAME=${LAYER%.*}
  INPUT=file:$(realpath $LAYERS/$LAYER)

  echo "spark-submit \
  --class geotrellis.testim.TestimIngest --driver-memory=2G $JAR \
  --input hadoop --format geotiff --cache NONE -I path=$INPUT \
  --output accumulo -O instance=$INSTANCE table=$TABLE user=$USER password=$PASSWORD zookeeper=$ZOOKEEPER \
  --layer $LAYERNAME --pyramid --crs $CRS --layoutScheme $LAYOUT_SCHEME" >> test.txt

  /home/rshir/Installs/spark-1.6.0/bin/spark-submit \
  --class geotrellis.testim.TestIngest --driver-memory=3G $JAR \
  --input hadoop --format geotiff --cache NONE -I path=$INPUT \
  --output hadoop -O path=$OUTPUT \
  --layer $LAYERNAME --crs $CRS --pyramid --layoutScheme $LAYOUT_SCHEME
 
done
