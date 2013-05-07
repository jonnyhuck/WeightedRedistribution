package org.geotools.passivelygeolocated;

import java.io.File;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.map.DefaultMapContext;
import org.geotools.map.GridCoverageLayer;
import org.geotools.map.MapContext;
import org.geotools.swing.JMapFrame;


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
        SimpleFeatureSource csvSource = FileHandler.transformFeature(FileHandler.importCSV(csv));

        // display a data store file chooser dialog for shapefiles
        File shp = JFileDataStoreChooser.showOpenFile("shp", null);
        if (shp == null) {
            return;
        }
        SimpleFeatureSource shpSource = FileHandler.openShapefile(shp);

        // display a data store file chooser dialog for ESRI tifii grid
        File tif = JFileDataStoreChooser.showOpenFile("tif", null);
        if (tif == null) {
            return;
        }
        GridCoverageLayer rasterLayer = FileHandler.openGeoTiffFile(tif);
        GridCoverage2D weightingSurface = rasterLayer.getCoverage();

        //offset the csv data for testing
        SimpleFeatureCollection csvCollection = csvSource.getFeatures();
        
        WeightedFuzzy wf = new WeightedFuzzy();
        SimpleFeatureCollection offsetCollection = wf.getFuzzyRelocatedSurface(csvCollection, weightingSurface, 10, 10000);
        
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