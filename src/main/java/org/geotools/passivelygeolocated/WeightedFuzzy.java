package org.geotools.passivelygeolocated;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import java.util.ArrayList;
import org.geotools.data.ows.Layer;
import org.geotools.geometry.jts.JTSFactoryFinder;

/**
 *
 * @author jonnyhuck
 */
public class WeightedFuzzy {

    //radius of sphere for offset (wgs84 def.)
    private final int earthRadius = 6378137;

    /**
     * 
     * @param point
     * @param iterations
     * @param maxDistance
     * @return 
     */
    public Point relocate(Point point, int iterations, double maxDistance, boolean spherical) {

        //create the required number of offset points
        for (int i = 0; i < iterations; i++) {

            //which offset method to use?
            if (spherical) {

                //offset the point and push into an array list
                Point relocated = sphericalOffset(point, Math.rint(Math.random() * maxDistance),
                        Math.rint(Math.random() * 359));

                //test the weighting surface value

            } else {    //cartesian

                //offset the point and push into an array list
                Point relocated = cartesianOffset(point, Math.rint(Math.random() * maxDistance),
                        Math.rint(Math.random() * 359));

                //test the weighting surface value
            }
        }

        //return the new point
        return point;
    }
    
    public double getValueFromRaster(Point point, Layer rasterLayer) {
        
        
        return 0;
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
                * Math.cos(latR), Math.cos(distR) - Math.sin(latR) * 
                Math.sin(deg2rad(lat)))) + 540 % 360) - 180;

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