package org.esa.snap.jp2.reader;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.snap.core.dataio.ProductSubsetDef;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.subset.PixelSubsetRegion;
import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * Created by jcoravu on 5/11/2019.
 */
public class JP2ProductReaderTest extends AbstractJP2Test {

    public JP2ProductReaderTest() {
    }

    @Test
    public void testReadProduct() throws Exception {
        URL resource = getClass().getResource("sample.jp2");
        assertNotNull(resource);

        File productFile = new File(resource.toURI());

        JP2ProductReaderPlugin readerPlugin = new JP2ProductReaderPlugin();
        JP2ProductReader productReader = new JP2ProductReader(readerPlugin);

        Product product = productReader.readProductNodes(productFile, null);
        assertNotNull(product);
        assertNotNull(product.getFileLocation());
        assertNull(product.getSceneGeoCoding());
        assertNotNull(product.getName());
        assertNotNull(product.getPreferredTileSize());
        assertNotNull(product.getProductReader());
        assertEquals(product.getProductReader(), productReader);
        assertEquals(400, product.getSceneRasterWidth());
        assertEquals(300, product.getSceneRasterHeight());
        assertEquals(3, product.getNumBands());

        assertEquals(0, product.getMaskGroup().getNodeCount());

        Band band = product.getBandAt(0);
        assertNotNull(band);
        assertEquals("band_1", band.getName());
        assertEquals(400, band.getRasterWidth());
        assertEquals(300, band.getRasterHeight());

        Rectangle part = new Rectangle(0, 0, 10, 10);
        MultiLevelImage multiLevelImage = band.getSourceImage();
        assertNotNull(multiLevelImage);
        assertNotNull(multiLevelImage.getColorModel());
        assertNotNull(multiLevelImage.getData());
        assertNotNull(multiLevelImage.getBounds());

        BufferedImage bufferedImage = multiLevelImage.getAsBufferedImage(part, multiLevelImage.getColorModel());
        assertNotNull(bufferedImage);
    }

    @Test
    public void testReadProductSubset() throws Exception {
        URL resource = getClass().getResource("sample.jp2");
        assertNotNull(resource);

        File productFile = new File(resource.toURI());

        JP2ProductReaderPlugin readerPlugin = new JP2ProductReaderPlugin();
        JP2ProductReader productReader = new JP2ProductReader(readerPlugin);

        Rectangle subsetRegion = new Rectangle(100, 100, 278, 189);
        ProductSubsetDef subsetDef = new ProductSubsetDef();
        subsetDef.setNodeNames(new String[] { "band_2", "band_3"} );
        subsetDef.setSubsetRegion(new PixelSubsetRegion(subsetRegion, 0));
        subsetDef.setSubSampling(1, 1);

        Product product = productReader.readProductNodes(productFile, subsetDef);
        assertNotNull(product);
        assertNotNull(product.getFileLocation());
        assertNull(product.getSceneGeoCoding());
        assertNotNull(product.getName());
        assertNotNull(product.getPreferredTileSize());
        assertNotNull(product.getProductReader());
        assertEquals(product.getProductReader(), productReader);
        assertEquals(278, product.getSceneRasterWidth());
        assertEquals(189, product.getSceneRasterHeight());
        assertEquals(2, product.getNumBands());

        assertEquals(0, product.getMaskGroup().getNodeCount());

        Band band = product.getBandAt(1);
        assertNotNull(band);
        assertEquals("band_3", band.getName());
        assertEquals(278, band.getRasterWidth());
        assertEquals(189, band.getRasterHeight());

        int[] pixels = new int[band.getRasterWidth() * band.getRasterHeight()];
        band.readPixels(0, 0, band.getRasterWidth(), band.getRasterHeight(), pixels, ProgressMonitor.NULL);
        assertEquals(107, pixels[10]);
        assertEquals(59, pixels[40]);
        assertEquals(84, pixels[13]);
        assertEquals(8, pixels[142]);
        assertEquals(35, pixels[475]);
        assertEquals(14, pixels[1012]);
        assertEquals(43, pixels[1313]);
        assertEquals(36, pixels[1231]);
        assertEquals(28, pixels[555]);
        assertEquals(22, pixels[365]);
        assertEquals(45, pixels[678]);
        assertEquals(30, pixels[931]);

        band = product.getBandAt(0);
        assertNotNull(band);
        assertEquals("band_2", band.getName());
        assertEquals(278, band.getRasterWidth());
        assertEquals(189, band.getRasterHeight());

        pixels = new int[band.getRasterWidth() * band.getRasterHeight()];
        band.readPixels(0, 0, band.getRasterWidth(), band.getRasterHeight(), pixels, ProgressMonitor.NULL);
        assertEquals(145, pixels[0]);
        assertEquals(68, pixels[20]);
        assertEquals(29, pixels[123]);
        assertEquals(37, pixels[342]);
        assertEquals(48, pixels[875]);
        assertEquals(58, pixels[1142]);
        assertEquals(35, pixels[1213]);
        assertEquals(50, pixels[1431]);
        assertEquals(48, pixels[555]);
        assertEquals(18, pixels[765]);
        assertEquals(8, pixels[999]);
        assertEquals(49, pixels[665]);
    }
}
