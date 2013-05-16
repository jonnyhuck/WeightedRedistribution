package org.geotools.passivelygeolocated;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.DataUtilities;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.geotools.feature.FeatureCollections;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.operation.TransformException;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

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
    
    /* NOT USED AFTER THIS POINT */

    /**
     * Imports a CSV file to a Feature Source
     * NB: Presumes that the CSV has no headers (as it's from MySQL)
     * @param file
     * @return SimpleFeatureSource
     * @throws IOException 
     */
    public static SimpleFeatureSource importCSV(File file) throws IOException, SchemaException {

        //create a collection to hold the resulting features
        SimpleFeatureCollection collection = FeatureCollections.newCollection();

        //create the geometry attribute of the feature
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);

        //create a feature type
        final SimpleFeatureType TYPE = DataUtilities.createType("Location", "location:Point:srid=4326,");

        //this is used to create the feature
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

        //read in the csv file
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String line;
            for (line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.trim().length() > 0) { // skip blank lines

                    //split the line by commas
                    String tokens[] = line.split("\\,");

                    if (!tokens[0].equals("") && !tokens[1].equals("")) {

                        //parse the data into variables
                        double latitude = Double.parseDouble(tokens[0]);
                        double longitude = Double.parseDouble(tokens[1]);

                        //create a point 
                        Point point = geometryFactory.createPoint(new Coordinate(latitude, longitude));

                        //add all of the attributes to a feature
                        featureBuilder.add(point);
                        SimpleFeature feature = featureBuilder.buildFeature(null);

                        //add the feature to a collection
                        collection.add(feature);
                    }
                }
            }
        } finally {
            reader.close();
        }
        return DataUtilities.source(collection);
    }

    
    /**
     * Transform a feature from wgs84 to osgb36
     * @param sfs
     * @return
     * @throws IOException
     * @throws FactoryException
     * @throws SchemaException 
     */
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
}