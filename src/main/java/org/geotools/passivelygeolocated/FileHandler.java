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
     * Open a GeoTiff file into a greyscaled Layer
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
     * Writes a GridCoverage2D to a GeoTiff file
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
    /*    
    public static SimpleFeatureSource transformFeature(SimpleFeatureSource sfs)
    throws NoSuchAuthorityCodeException, FactoryException, IOException,
    SchemaException, MismatchedDimensionException, TransformException {
    
    //get coordinate reference systems and transformation
    CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326");
    CoordinateReferenceSystem osgb36 = CRS.decode("EPSG:27700");
    MathTransform transform = CRS.findMathTransform(wgs84, osgb36, true);
    
    //get the features and an iterator from the input data
    SimpleFeatureCollection featureCollection = sfs.getFeatures();
    SimpleFeatureIterator iterator = featureCollection.features();
    
    //create an empty feature collection
    SimpleFeatureCollection collection = FeatureCollections.newCollection();
    final SimpleFeatureType TYPE = DataUtilities.createType("Location", "location:Point:srid=27700,");
    SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
    
    try {
    while (iterator.hasNext()) {
    
    //copy the contents of each feature and transform the geometry
    SimpleFeature feature = iterator.next();
    Geometry geometry = (Geometry) feature.getDefaultGeometry();
    Geometry transformedGeometry = JTS.transform(geometry, transform);
    
    //add to the new feature collection
    featureBuilder.add(transformedGeometry);
    SimpleFeature transformedFeature = featureBuilder.buildFeature(null);
    
    //add the feature to a collection
    collection.add(transformedFeature);
    
    }
    } finally {
    iterator.close();
    }
    return DataUtilities.source(collection);
    }
     */
}