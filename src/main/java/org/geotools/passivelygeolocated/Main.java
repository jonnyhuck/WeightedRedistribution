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
        
        // display a data store file chooser dialog for shapefiles
        File points = JFileDataStoreChooser.showOpenFile("shp", null);
        if (points == null) {
            return;
        }
        SimpleFeatureSource pointSource = FileHandler.openShapefile(points);

        // display a data store file chooser dialog for shapefiles
        File polygons = JFileDataStoreChooser.showOpenFile("shp", null);
        if (polygons == null) {
            return;
        }
        SimpleFeatureSource polygonSource = FileHandler.openShapefile(polygons);

        // display a data store file chooser dialog for geotiff files
        File tif = JFileDataStoreChooser.showOpenFile("tif", null);
        if (tif == null) {
            return;
        }
        GridCoverage2D weightingSurface = FileHandler.openGeoTiffFile(tif);
        
        //get the output surface
        WeightedFuzzy wf = new WeightedFuzzy();
        GridCoverage2D gcOut = wf.getFuzzyRelocatedSurface(pointSource, polygonSource,
                weightingSurface, 10, 10000);
        
        //write the file
        FileHandler.writeGeoTiffFile(gcOut, "/Users/jonnyhuck/Documents/wfr.tif");
        
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
    }
}   //class