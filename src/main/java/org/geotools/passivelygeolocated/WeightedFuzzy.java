package org.geotools.passivelygeolocated;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.FeatureCollections;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 * @author jonnyhuck
 */
public class WeightedFuzzy {

    //radius of sphere for offset (wgs84 def.)
    private final int earthRadius = 6378137;

    /**
     * 
     * @param csvCollection
     * @param weightingSurface
     * @param relocationIterations
     * @param maxRelocationDistance
     * @throws NoSuchAuthorityCodeException
     * @throws FactoryException
     * @throws SchemaException 
     */
    public SimpleFeatureCollection getFuzzyRelocatedSurface(SimpleFeatureCollection csvCollection, GridCoverage2D weightingSurface, 
            int relocationIterations, int maxRelocationDistance) 
            throws NoSuchAuthorityCodeException, FactoryException, SchemaException {
        
        //build a new feature collection for offset points
        SimpleFeatureCollection offsetCollection = FeatureCollections.newCollection();
        final SimpleFeatureType TYPE = DataUtilities.createType("Location", "location:Point:srid=27700,");  //srid=4326,");
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
        
        //get iterator to move through the input features
        SimpleFeatureIterator iterator = csvCollection.features();

        try {
            //populate the new feature collection with offset points
            while (iterator.hasNext()) {

                //retrieve the feature then geom (as a JTS point)
                SimpleFeature feature = iterator.next();
                Point p = (Point) feature.getDefaultGeometry();

                //test that a default geom was set
                if (p != null) {

                    //offset and add to a feature
                    Point o = this.relocate(p, relocationIterations, maxRelocationDistance, weightingSurface);
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
        return offsetCollection;
    }
    
    /**
     * Relocates a point using the weighting surface.
     * More iterations = more weighting. Fewer iterations = more random
     * @param point
     * @param iterations
     * @param maxDistance
     * @return 
     */
    private Point relocate(Point point, int iterations, double maxDistance, GridCoverage2D weightingSurface)
            throws NoSuchAuthorityCodeException, FactoryException {

        //holds max value
        double maxVal = 0;
        Point out = point;

        //create the required number of offset points
        for (int i = 0; i < iterations; i++) {

            //offset the point and push into an array list
            Point relocated = this.cartesianOffset(point, Math.rint(Math.random() * maxDistance),
                    Math.rint(Math.random() * 359));

            //test the weighting surface value
            double val = this.getValueFromRaster(relocated, weightingSurface);
            if (val > maxVal) {
                maxVal = val;
                out = relocated;
            }
        }

        //return the new point
        return out;
    }

    /**
     * Creates a matrix of values to be applied to the output surface
     * @param radius 
     */
    private void getFuzzyMatrix(double radius) {
    }

    /**
     * Returns a value from a raster at a given coordinate
     * @param point
     * @param weightingSurface
     * @return
     * @throws NoSuchAuthorityCodeException
     * @throws FactoryException 
     */
    private double getValueFromRaster(Point point, GridCoverage2D weightingSurface)
            throws NoSuchAuthorityCodeException, FactoryException {

        //get the coverage data
        CoordinateReferenceSystem crs = CRS.decode("EPSG:27700");
        DirectPosition position = new DirectPosition2D(crs, point.getX(), point.getY());
        double[] bands = new double[1];
        weightingSurface.evaluate(position, bands);
        return bands[0];
    }

    /**
     * Offset a point along a spherical surface
     * @param point
     * @param distance
     * @param azimuth
     * @return 
     */
    public Point sphericalOffset(Point point, double distance, double azimuth) {

        //convert to angular distance in radians
        double distR = distance / earthRadius;

        //convert to radians
        double azimuthR = deg2rad(azimuth);
        double lngR = deg2rad(point.getX());
        double latR = deg2rad(point.getY());

        //offset across a sphere
        double lat = rad2deg(Math.asin(Math.sin(latR) * Math.cos(distR)
                + Math.cos(latR) * Math.sin(distR) * Math.cos(azimuthR)));

        //(includes normalisation for -180 - 180)
        double lng = (rad2deg(lngR + Math.atan2(Math.sin(azimuthR) * Math.sin(distR)
                * Math.cos(latR), Math.cos(distR) - Math.sin(latR)
                * Math.sin(deg2rad(lat)))) + 540 % 360) - 180;

        //build a point object from the results
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);
        return geometryFactory.createPoint(new Coordinate(lng, lat));
    }

    /**
     * Offset a point along a cartesian surface
     * @param point
     * @param distance
     * @param azimuth
     * @return 
     */
    public Point cartesianOffset(Point point, double distance, double azimuth) {

        //offset the points
        double easting = point.getX() + Math.sin(deg2rad(azimuth)) * distance;
        double northing = point.getY() + Math.cos(deg2rad(azimuth)) * distance;

        //build a point object from the results
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);
        return geometryFactory.createPoint(new Coordinate(easting, northing));
    }

    /**
     * Convert degrees to radians
     * @param degrees
     * @return 
     */
    private double deg2rad(double degrees) {
        return degrees * (Math.PI / 180);
    }

    /**
     * Convert radians to degrees
     * @param radians
     * @return 
     */
    private double rad2deg(double radians) {
        return radians * (180 / Math.PI);
    }
}