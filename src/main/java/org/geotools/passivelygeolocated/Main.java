package org.geotools.passivelygeolocated;

import java.io.File;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.map.DefaultMapContext;
import org.geotools.map.MapContext;
import org.geotools.styling.ChannelSelection;
import org.geotools.styling.ContrastEnhancement;
import org.geotools.styling.RasterSymbolizer;
import org.geotools.styling.SLD;
import org.geotools.styling.SelectedChannelType;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.swing.JMapFrame;
import org.opengis.filter.FilterFactory2;
import org.opengis.style.ContrastMethod;


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
        
        //make sure there are 3 arguments
        if (args.length == 0){
            //print help if there are no args
            System.out.println("java WFR n f [output path].tif");            
            System.out.println("e.g.:");            
            System.out.println("     java WFR 10 0.1 /Users/wfr/filename.tif");
            return;
        } else if (args.length != 3){
            System.out.println("you need 3 arguments!");
            System.out.println("please try again.");
            return;
        }
        
        //make sure f and n are numbers!
        int n = 0;
        double f = 0;
        try {
            n = Integer.parseInt(args[0]);
            f = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("number of iterations (n) and fuzziness (f) need to be numbers!");
            System.out.println("n should be a positive whole number");
            System.out.println("f should be a value between 0 and 1");
            System.out.println("please try again.");
            return;
        }
        
        // display a data store file chooser dialog for shapefiles
        System.out.println("select point data...");
        File points = JFileDataStoreChooser.showOpenFile("shp", null);
        if (points == null) {
            return;
        }
        SimpleFeatureSource pointSource = FileHandler.openShapefile(points);
        

        // display a data store file chooser dialog for shapefiles
        System.out.println("select polygon data...");
        File polygons = JFileDataStoreChooser.showOpenFile("shp", null);
        if (polygons == null) {
            return;
        }
        SimpleFeatureSource polygonSource = FileHandler.openShapefile(polygons);

        // display a data store file chooser dialog for geotiff files
        System.out.println("select weighting surface...");
        File tif = JFileDataStoreChooser.showOpenFile("tif", null);
        if (tif == null) {
            return;
        }
        GridCoverage2D weightingSurface = FileHandler.openGeoTiffFile(tif);
        
        //get the output surface
        System.out.println("calculating WFR surface...");
        WeightedFuzzy wf = new WeightedFuzzy();
        GridCoverage2D gcOut = wf.getFuzzyRelocatedSurface(pointSource, polygonSource,
                weightingSurface, n, f);
        
        //write the file
        System.out.println("writing output...");
        //FileHandler.writeGeoTiffFile(gcOut, "/Users/jonnyhuck/Documents/_level3.tif");
        FileHandler.writeGeoTiffFile(gcOut, args[2]);
        
        //create greyscale style
        StyleFactory sf = CommonFactoryFinder.getStyleFactory(null);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);
        ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.HISTOGRAM);
        SelectedChannelType sct = sf.createSelectedChannelType(String.valueOf(1), ce);
        RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
        ChannelSelection sel = sf.channelSelection(sct);
        sym.setChannelSelection(sel);
        Style rasterStyle = SLD.wrapSymbolizers(sym);
        
        // Create a map context and add our shapefile to it
        MapContext map = new DefaultMapContext();
        map.setTitle("Fuzzy Relocated Data");
        map.addLayer(gcOut, rasterStyle);
        
        // Now display the map
        JMapFrame.showMap(map);
        System.out.println("done.");
    }
}   //class