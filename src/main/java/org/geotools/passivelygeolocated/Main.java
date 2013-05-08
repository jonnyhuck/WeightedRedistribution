package org.geotools.passivelygeolocated;

import java.io.File;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.map.DefaultMapContext;
import org.geotools.map.GridCoverageLayer;
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
        //SimpleFeatureCollection offsetCollection = wf.getFuzzyRelocatedSurface(csvCollection, weightingSurface, 10, 10000, 10000, "/Users/jonnyhuck/Documents/wfr.tif");
        GridCoverage2D gcOut = wf.getFuzzyRelocatedSurface(csvCollection, weightingSurface, 10, 10000, 10000, "/Users/jonnyhuck/Documents/wfr.tif");
        
        //create greyscale style
        StyleFactory sf = CommonFactoryFinder.getStyleFactory(null);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);
        ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
        SelectedChannelType sct = sf.createSelectedChannelType(String.valueOf(1), ce);
        RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
        ChannelSelection sel = sf.channelSelection(sct);
        sym.setChannelSelection(sel);
        Style rasterStyle = SLD.wrapSymbolizers(sym);
        
        // Create a map context and add our shapefile to it
        MapContext map = new DefaultMapContext();
        map.setTitle("Passively Geolocated Data");
        map.addLayer(gcOut, rasterStyle);
        
        // Now display the map
        JMapFrame.showMap(map);
    }
}   //class