jarname="cochlea-0.1.0-SNAPSHOT-standalone.jar"
rm -rf target
lein uberjar
cd target/uberjar
lein compile cochlea.core
cp -r ../base+system+user+dev/classes/cochlea ./cochlea
jar uf $jarname cochlea
mv $jarname ../../cochlea.jar
