package org.geotools.passivelygeolocated;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import java.util.Random;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import javax.media.jai.RasterFactory;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.InvalidGridGeometryException;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.opengis.coverage.PointOutsideCoverageException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
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

    /**
     * 
     * @param points
     * @param polygons
     * @param weightingSurface
     * @param relocationIterations
     * @param fuzziness
     * @return
     * @throws IOException
     * @throws NoSuchAuthorityCodeException
     * @throws FactoryException
     * @throws InvalidGridGeometryException
     * @throws TransformException 
     */
    public GridCoverage2D getFuzzyRelocatedSurface(SimpleFeatureSource points, SimpleFeatureSource polygons,
            GridCoverage2D weightingSurface, int relocationIterations, double fuzziness) throws IOException,
            NoSuchAuthorityCodeException, FactoryException, InvalidGridGeometryException, TransformException {

        //get an output surface
        WritableRaster outputSurface = this.getWritableRaster(weightingSurface, 0);

        //get pixel size
        final int pxWidth = (int) weightingSurface.getGridGeometry().getGridRange2D().getSpan(0);
        final int mWidth = (int) weightingSurface.getEnvelope2D().getSpan(0);
        final int pxSize = mWidth / pxWidth;

        //loop through the polygons
        SimpleFeatureIterator polygonIterator = polygons.getFeatures().features();
        try {
            //populate the new feature collection with offset points
            while (polygonIterator.hasNext()) {

                int cockups = 0;
                int relocates = 0;
                int nodata = 0;
                int test = 0;

                //get next polygon
                SimpleFeature polygonFeature = polygonIterator.next();
                Geometry polygon = (Geometry) polygonFeature.getDefaultGeometry();

                //get the splat radius for this polygon
                double splatRadius = Math.sqrt((polygon.getArea() * fuzziness) / Math.PI);

                //build the 2D matrix to represent each 'splat', then 'flatten' to 1D array
                double[][] splat2D = this.getFuzzyMatrix(splatRadius, pxSize);
                int nCells = (int) Math.pow(splat2D[0].length, 2);
                double[] splat1D = new double[nCells];
                int j, k;
                for (int i = 0; i < nCells; i++) {
                    j = (int) i / splat2D[0].length;
                    k = i % splat2D[0].length;
                    splat1D[i] = splat2D[j][k];
                }

                //get the max offset distance
                double maxOffsetDistance = this.getBoundingRadius(polygon); //Math.sqrt(polygon.getArea() / Math.PI);

                //get all points within it
                SimpleFeatureCollection pointsWithin = this.getPointsWithin(polygonFeature, points);

                //loop through the points
                SimpleFeatureIterator pointsIterator = pointsWithin.features();
                try {
                    //populate the new feature collection with offset points
                    while (pointsIterator.hasNext()) {

                        test++;

                        //retrieve the feature then geom (as a JTS point)
                        SimpleFeature pointFeature = pointsIterator.next();
                        Point point = (Point) pointFeature.getDefaultGeometry();

                        //test that a default geom was set
                        if (point != null) {

                            //offset the point, then get the position of the top left of the patch
                            Point offsetPoint = this.relocate(point, polygon, relocationIterations, maxOffsetDistance, weightingSurface);
                            Point patchTopLeft = this.cartesianOffset(offsetPoint,
                                    Math.sqrt(Math.pow(splat2D[0].length, 2) + Math.pow(splat2D[0].length, 2)), 315);

                            //The coordinates at which the patch will be applied
                            CoordinateReferenceSystem crs = CRS.decode("EPSG:27700");
                            DirectPosition position = new DirectPosition2D(crs, patchTopLeft.getX(), patchTopLeft.getY());
                            GridCoordinates2D topLeft = weightingSurface.getGridGeometry().worldToGrid(position);

                            try {
                                //get the existing surface data to apply the patch to
                                double[] existingData = new double[nCells];
                                outputSurface.getPixels(topLeft.x, topLeft.y, splat2D[0].length, splat2D[0].length, existingData);

                                //add the patch to the existing data
                                double[] patch = new double[nCells];
                                for (int i = 0; i < nCells; i++) {
                                    patch[i] = existingData[i] + splat1D[i];
                                }

                                //add splat to raster at the desired location
                                outputSurface.setPixels(topLeft.x, topLeft.y, splat2D[0].length, splat2D[0].length, patch);

                                relocates++;
                            } catch (ArrayIndexOutOfBoundsException e) {
                                cockups++;
                            }
                        } else {
                            nodata++;
                        }
                    }
                } finally {
                    //close the iterator
                    pointsIterator.close();
                    /*System.out.println("points: " + pointsWithin.size());
                    System.out.println("relocates: " + relocates);
                    System.out.println("no data: " + nodata);
                    System.out.println("cockups: " + cockups);
                    System.out.println("test: " + test);
                    System.out.println("");*/
                }
            }
        } finally {
            polygonIterator.close();
        }

        //build a grid coverage from the writable raster
        GridCoverageFactory factory = new GridCoverageFactory();
        GridCoverage2D output = factory.create("output", outputSurface, weightingSurface.getEnvelope());
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
    private Point relocate(Point point, Geometry polygon, int iterations, double maxDistance, GridCoverage2D weightingSurface)
            throws NoSuchAuthorityCodeException, FactoryException {

        //random number generator
        Random rng = new Random();
        
        //holds max value
        double maxVal = 0;
        Point out = point;

        //create the required number of offset points
        for (int i = 0; i < iterations; i++) {

            //offset the point and push into an array list
            Point relocated;
            do {
                //force the new point to be within the polygon
                relocated = this.cartesianOffset(point, rng.nextDouble() * maxDistance,
                        rng.nextDouble() * 359.9);
            } while (!relocated.within(polygon));

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
    private double[][] getFuzzyMatrix(double radius, double pixelSize) {

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
     * Returns a blank writable raster based upon the gc given
     * @param template
     * @return 
     */
    private WritableRaster getWritableRaster(GridCoverage2D template, double initialValue) {

        //get raster dimensions
        final GridEnvelope2D envelope = template.getGridGeometry().getGridRange2D();
        final int width = (int) envelope.getSpan(0);
        final int height = (int) envelope.getSpan(1);

        //build writable raster
        WritableRaster raster = RasterFactory.createBandedRaster(DataBuffer.TYPE_DOUBLE, width, height, 1, null);

        //populate with initial value
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.setSample(x, y, 0, initialValue);
            }
        }

        return raster;
    }

    /**
     * Return the distance between the centroid and furthest node in a polygon
     * @param geom
     * @return 
     */
    public double getBoundingRadius(Geometry geom) {

        //get geometric centroid
        Point centroid = geom.getCentroid();

        //get the distance to the furthest node from the centroid
        double maxDistance = 0;
        for (Coordinate node : geom.getCoordinates()) {
            double d = Math.sqrt(Math.pow(centroid.getX() - node.x, 2) + Math.pow(centroid.getY() - node.y, 2));
            if (d > maxDistance) {
                maxDistance = d;
            }
        }
        return maxDistance;
    }

    /**
     * Gets all points within a given polygon
     * @param points
     * @param polygon
     * @return feature collection of points that were within the polygon
     * @throws IOException 
     */
    private SimpleFeatureCollection getPointsWithin(SimpleFeature polygon, SimpleFeatureSource points) throws IOException {

        //create a filter to handle the 'within' query
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);

        //get the geom column from the datasource
        FeatureType schema = points.getSchema();
        String geometryPropertyName = schema.getGeometryDescriptor().getLocalName();

        //build the filter for a "within" query
        Filter filter = ff.within(ff.property(geometryPropertyName), ff.literal(polygon.getDefaultGeometry()));

        //apply the filter to the feature source
        return points.getFeatures(filter);
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
}