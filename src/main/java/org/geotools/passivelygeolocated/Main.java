package org.geotools.passivelygeolocated;

import java.io.File;
import com.vividsolutions.jts.geom.Point;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollections;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.map.DefaultMapContext;
import org.geotools.map.GridCoverageLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContext;
import org.geotools.swing.JMapFrame;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;


/**
 * 
 * @author jonnyhuck
 */
public class Main {

    /**
     * Main
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {

        // display a data store file chooser dialog for csv files
        File csv = JFileDataStoreChooser.showOpenFile("csv", null);
        if (csv == null) {
            return;
        }
        SimpleFeatureSource csvSource = FileReaders.transformFeature(FileReaders.importCSV(csv));

        // display a data store file chooser dialog for shapefiles
        File shp = JFileDataStoreChooser.showOpenFile("shp", null);
        if (shp == null) {
            return;
        }
        SimpleFeatureSource shpSource = FileReaders.openShapefile(shp);

        // display a data store file chooser dialog for ESRI tifii grid
        File tif = JFileDataStoreChooser.showOpenFile("tif", null);
        if (tif == null) {
            return;
        }
        GridCoverageLayer rasterLayer = FileReaders.openGeoTiffFile(tif);
        GridCoverage2D weightingSurface = rasterLayer.getCoverage();

        //offset the csv data for testing
        SimpleFeatureCollection csvCollection = csvSource.getFeatures();
        SimpleFeatureIterator iterator = csvCollection.features();

        //create a feature 
        SimpleFeatureCollection offsetCollection = FeatureCollections.newCollection();
        final SimpleFeatureType TYPE = DataUtilities.createType("Location", "location:Point:srid=27700,");  //srid=4326,");
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

        try {
            while (iterator.hasNext()) {

                //retrieve the feature then geom (as a JTS point)
                SimpleFeature feature = iterator.next();
                Point p = (Point) feature.getDefaultGeometry();

                //test that a default geom was set
                if (p != null) {

                    //offset and draw to map as well
                    WeightedFuzzy wf = new WeightedFuzzy();
                    Point o = wf.relocate(p, 10, 10000, weightingSurface);

                    //add to a feature
                    featureBuilder.add(o);
                    SimpleFeature offsetFeature = featureBuilder.buildFeature(null);

                    //add the feature to a collection
                    offsetCollection.add(offsetFeature);

                }
            }
        } finally {
            //close the iterator
            csvCollection.close(iterator);
        }

        // Create a map context and add our shapefile to it
        MapContext map = new DefaultMapContext();
        map.setTitle("Pasively Geolocated Data");
        map.addLayer(rasterLayer);
        map.addLayer(shpSource, null);
        map.addLayer(csvSource, null);
        map.addLayer(DataUtilities.source(offsetCollection), null);

        // Now display the map
        JMapFrame.showMap(map);
    }
}   //class