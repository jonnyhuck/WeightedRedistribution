package org.geotools.passivelygeolocated;

import java.io.File;
import java.io.IOException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriter;

/**
 *
 * @author jonnyhuck
 */
public class FileHandler {

    /**
     * Open a shapefile and return the feature source
     * @param file
     * @return SimpleFeatureSource
     * @throws Exception 
     */
    public static SimpleFeatureSource openShapefile(File file) throws Exception {

        //access the data store of the shapefile
        FileDataStore store = FileDataStoreFinder.getDataStore(file);
        SimpleFeatureSource featureSource = store.getFeatureSource();
        return featureSource;
    }

    /**
     * Open a GeoTiff file into a coverage
     * @param file
     * @return Layer
     */
    public static GridCoverage2D openGeoTiffFile(File file)
            throws IllegalArgumentException, IOException, NoSuchAuthorityCodeException, FactoryException {

        //get a reader for the input file
        GeoTiffReader reader = new GeoTiffReader(file);
        GridCoverage2D gc = (GridCoverage2D) reader.read(null);
        return gc;

    }

    /**
     * Writes a coverage to a GeoTiff file
     * @param gc
     * @param path
     * @throws IOException 
     */
    public static void writeGeoTiffFile(GridCoverage2D gc, String path) throws IOException {

        //create a geotiff writer
        File file = new File(path);
        GeoTiffWriter gw = new GeoTiffWriter(file);
        try {
            //write the file
            gw.write(gc, null);
        } finally {
            //destroy the writer
            gw.dispose();
        }
    }
}