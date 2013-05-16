package org.geotools.passivelygeolocated;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import java.awt.image.WritableRaster;
import java.io.IOException;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.InvalidGridGeometryException;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.opengis.coverage.PointOutsideCoverageException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

/**
 *
 * @author jonnyhuck
 */
public class WeightedFuzzy {

    //radius of sphere for offset (wgs84 def.)
    private final int earthRadius = 6378137;
    private int cockups = 0;

    /**
     * Get the output surface
     * @param csvCollection
     * @param weightingSurface
     * @param relocationIterations
     * @param maxRelocationDistance
     * @param splatRadius
     * @param outputPath
     * @return surface of features that have been weighted fuzzy relocated
     * @throws NoSuchAuthorityCodeException
     * @throws FactoryException
     * @throws SchemaException
     * @throws InvalidGridGeometryException
     * @throws TransformException
     * @throws IOException 
     */
    public GridCoverage2D getFuzzyRelocatedSurface(SimpleFeatureCollection csvCollection, GridCoverage2D weightingSurface,
            int relocationIterations, int maxRelocationDistance, int splatRadius, String outputPath)
            throws NoSuchAuthorityCodeException, FactoryException, SchemaException, InvalidGridGeometryException, TransformException, IOException {

        //get an output surface
        WritableRaster outputSurface = FileHandler.getWritableRaster(weightingSurface, 0);

        //get pixel size
        final int pxWidth = (int) weightingSurface.getGridGeometry().getGridRange2D().getSpan(0);
        final int mWidth = (int) weightingSurface.getEnvelope2D().getSpan(0);
        final int pxSize = mWidth / pxWidth;

        //build the 2D matrix to represent each 'splat', then 'flatten' to 1D array
        double[][] splat2D = this.getFuzzyMatrix(splatRadius, pxSize);
        final int nCells = (int) Math.pow(splat2D[0].length, 2);
        double[] splat1D = new double[nCells];
        int j, k;
        for (int i = 0; i < nCells; i++) {
            j = (int) i / splat2D[0].length;
            k = i % splat2D[0].length;
            splat1D[i] = splat2D[j][k];
        }

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

                    //offset the point, then get the position of the top left of the patch
                    Point offsetPoint = this.relocate(p, relocationIterations, maxRelocationDistance, weightingSurface);
                    Point patchTopLeft = this.cartesianOffset(offsetPoint,
                            Math.sqrt(Math.pow(splat2D[0].length, 2) + Math.pow(splat2D[0].length, 2)), 315);

                    //The coordinates at which the patch will be applied
                    CoordinateReferenceSystem crs = CRS.decode("EPSG:27700");
                    DirectPosition position = new DirectPosition2D(crs, patchTopLeft.getX(), patchTopLeft.getY());
                    GridCoordinates2D topLeft = weightingSurface.getGridGeometry().worldToGrid(position);

                    //get the existing surface data to apply the patch to
                    double[] existingData = new double[nCells];
                    try {
                        outputSurface.getPixels(topLeft.x, topLeft.y, splat2D[0].length, splat2D[0].length, existingData);

                        //add the patch to the existing data
                        double[] patch = new double[nCells];
                        for (int i = 0; i < nCells; i++) {
                            patch[i] = existingData[i] + splat1D[i];
                        }

                        //add splat to raster at the desired location
                        outputSurface.setPixels(topLeft.x, topLeft.y, splat2D[0].length, splat2D[0].length, patch);
                        
                    } catch (java.lang.ArrayIndexOutOfBoundsException e) {
                        cockups++;
                    }
                }
            }
        } finally {
            //close the iterator
            csvCollection.close(iterator);
            System.out.println("cockup count: " + cockups);
        }

        //build a grid coverage from the writable raster
        GridCoverageFactory factory = new GridCoverageFactory();
        GridCoverage2D output = factory.create("output", outputSurface, weightingSurface.getEnvelope());

        //write the file and return
        FileHandler.writeGeoTiffFile(output, outputPath);
        return output;
    }

    /**
     * Relocates a point using the weighting surface.
     * More iterations = more weighting. Fewer iterations = more random
     * @param point
     * @param iterations
     * @param maxDistance
     * @return a point that has been fuzzy relocated
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
     * @param pixelSize
     * @return a matrix of values 
     */
    private double[][] getFuzzyMatrix(int radius, double pixelSize) {

        //get the size of the radius in pixels
        final int radPx = (int) (radius / pixelSize);

        //get the required array size and build it
        final int span = (radPx * 2) + 1;
        double[][] matrix = new double[span][span];

        //popuate the array with values
        for (int i = 0; i < span; i++) {
            for (int j = 0; j < span; j++) {
                //populate value ased upon distance to centre (java array is ordered y,x not x,y)
                matrix[i][j] = getMatrixValue(j, i, radPx);
            }
        }
        return matrix;
    }

    /**
     * Assesses whether or not a pixel is within span of the centre, if so it 
     *   returns the pixel distance
     * @param x
     * @param y
     * @param radPx
     * @return the distance from the centre of the splat
     */
    private double getMatrixValue(int x, int y, int radPx) {

        //use pythgoras to work out the pixel distance and test agains radius
        double pxDistance = Math.sqrt(Math.pow(radPx - x, 2) + Math.pow(radPx - y, 2));

        //return 0 if outside radius
        if (pxDistance > radPx) {
            return 0;
        }

        //scale 0 - 1 and invert value
        return 1 - (pxDistance) / radPx;
    }

    /**
     * Returns a value from a raster at a given coordinate
     * @param point
     * @param weightingSurface
     * @return value taken from the raster at the given location
     * @throws NoSuchAuthorityCodeException
     * @throws FactoryException 
     */
    private double getValueFromRaster(Point point, GridCoverage2D weightingSurface)
            throws NoSuchAuthorityCodeException, FactoryException {

        //get the coverage data
        CoordinateReferenceSystem crs = CRS.decode("EPSG:27700");
        DirectPosition position = new DirectPosition2D(crs, point.getX(), point.getY());
        double[] bands = new double[1];
        try {
            weightingSurface.evaluate(position, bands);
            return bands[0];
        } catch (PointOutsideCoverageException e) {
            //if the point is not withi the weighting data
            return 0;
        }
    }

    /**
     * Offset a point along a spherical surface
     * @param point
     * @param distance
     * @param azimuth
     * @return a point offset across a sphere by the specified distance and direction
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
     * @return a point offset across a cartesian surface  by the givern distance and bearing
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
     * @return the equivalent value in radians
     */
    private double deg2rad(double degrees) {
        return degrees * (Math.PI / 180);
    }

    /**
     * Convert radians to degrees
     * @param radians
     * @return the equivalent value in degrees
     */
    private double rad2deg(double radians) {
        return radians * (180 / Math.PI);
    }
}