package org.esa.snap.dataio.bigtiff;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelImage;
import it.geosolutions.imageio.plugins.tiff.BaselineTIFFTagSet;
import it.geosolutions.imageio.plugins.tiff.TIFFField;
import it.geosolutions.imageio.plugins.tiff.TIFFImageWriteParam;
import it.geosolutions.imageio.plugins.tiff.TIFFTag;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFIFD;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageMetadata;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageWriter;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFLZWCompressor;
import org.esa.snap.core.dataio.AbstractProductWriter;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.dataio.ProductWriter;
import org.esa.snap.core.dataio.ProductWriterPlugIn;
import org.esa.snap.core.dataio.dimap.DimapHeaderWriter;
import org.esa.snap.core.dataio.dimap.DimapProductWriterPlugIn;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FilterBand;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.ProductNode;
import org.esa.snap.core.datamodel.VirtualBand;
import org.esa.snap.core.image.ImageManager;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.StringUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.geotiff.GeoTIFF;
import org.esa.snap.core.util.geotiff.GeoTIFFMetadata;
import org.esa.snap.core.util.io.FileUtils;
import org.esa.snap.dataio.bigtiff.internal.TiffIFD;
import org.esa.snap.runtime.Config;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.media.jai.JAI;
import javax.media.jai.operator.FormatDescriptor;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.renderable.ParameterBlock;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

class BigGeoTiffProductWriter extends AbstractProductWriter {

    private static final String PARAM_COMPRESSION_TYPE = "snap.dataio.bigtiff.compression.type";   // value must be "LZW" or "NONE" or empty or null
    private static final String COMPRESSION_TYPE_LZW = "LZW";
    private static final String COMPRESSION_TYPE_DEFAULT = COMPRESSION_TYPE_LZW;
    private static final String COMPRESSION_TYPE_NONE = "NONE";

    private static final String PARAM_COMPRESSION_QUALITY = "snap.dataio.bigtiff.compression.quality";   // value float 0 ... 1, default 0.75
    private static final float PARAM_COMPRESSION_QUALITY_DEFAULT = 0.75f;

    private static final String PARAM_TILING_WIDTH = "snap.dataio.bigtiff.tiling.width";   // integer value
    private static final String PARAM_TILING_HEIGHT = "snap.dataio.bigtiff.tiling.height";   // integer value

    private static final String PARAM_FORCE_BIGTIFF = "snap.dataio.bigtiff.force.bigtiff";   // boolean
    public static final String PARAM_PUSH_PROCESSING = "snap.dataio.bigtiff.support.pushprocessing";   // boolean
    private static final String PARAM_ARCGIS_AUX = "snap.dataio.bigtiff.write.arcgisaux";   // boolean

    private File outputFile;
    private TIFFImageWriter imageWriter;
    private boolean isWritten;
    private FileImageOutputStream outputStream;
    private TIFFImageWriteParam writeParam;
    private boolean withIntermediate;
    private File intermediateFile = null;
    private ProductWriter intermediateWriter = null;
    private List<String> bandNames = new ArrayList<>();
    private boolean writingDataHasStarted = false;

    public BigGeoTiffProductWriter(ProductWriterPlugIn writerPlugIn) {
        super(writerPlugIn);
        createWriterParams();
        final boolean writeIntermediateProduct = Config.instance().preferences().getBoolean(PARAM_PUSH_PROCESSING, false);
        setWriteIntermediateProduct(writeIntermediateProduct);
    }


    @Override
    public void flush() throws IOException {
        if (outputStream != null) {
            outputStream.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (withIntermediate) {
            if (intermediateWriter != null) {
                intermediateWriter.flush();
                intermediateWriter.close();
                _writeProductNodesImpl();
                Product product = ProductIO.readProduct(intermediateFile, "BEAM-DIMAP");
                product.setName(FileUtils.getFilenameWithoutExtension(outputFile));
                _writeBandRasterData(product);
                intermediateWriter.deleteOutput();
                intermediateWriter = null;
            }
        }

        if (outputStream != null && Config.instance().preferences().getBoolean(PARAM_ARCGIS_AUX, false)) {
            File auxFile = new File(outputFile.getParent(), outputFile.getName() + ".aux.xml");
            SystemUtils.LOG.info("writing band names to ArcGIS aux " + auxFile.getPath());
            try (BufferedWriter auxWriter = new BufferedWriter(new FileWriter(auxFile))) {
                auxWriter.write("<PAMDataset>\n  <Metadata>\n    <MDI key='Band_0'>dummy</MDI>\n");
                int i = 0;
                for (String bandName : bandNames) {
                    auxWriter.write("    <MDI key='Band_" + (++i) + "'>");
                    auxWriter.write(bandName);
                    auxWriter.write("</MDI>\n");
                }
                auxWriter.write("  </Metadata>\n</PAMDataset>\n");
            }
        }

        if (outputStream != null) {
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        }

        if (imageWriter != null) {
            imageWriter.dispose();
            imageWriter = null;
        }
    }

    @Override
    public boolean shouldWrite(ProductNode node) {
        return !(node instanceof VirtualBand) && !(node instanceof FilterBand);
    }

    @Override
    public void deleteOutput() throws IOException {
        if (outputFile != null && outputFile.isFile()) {
            if (!outputFile.delete()) {
                throw new IOException("Unable to delete file: " + outputFile.getAbsolutePath());
            }
        }
    }

    @Override
    public void writeBandRasterData(Band sourceBand, int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight, ProductData sourceBuffer, ProgressMonitor pm) throws IOException {
        if (withIntermediate) {
            intermediateWriter.writeBandRasterData(sourceBand, sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight, sourceBuffer, pm);
        } else {
            if (isWritten) {
                return;
            }
            final Product sourceProduct = sourceBand.getProduct();
            _writeBandRasterData(sourceProduct);
            isWritten = true;
        }
    }

    @Override
    protected void writeProductNodesImpl() throws IOException {
        writingDataHasStarted = true;
        updateTilingParameter();
        for (Band band : getSourceProduct().getBands()) {
            bandNames.add(band.getName());
        }
        if (withIntermediate) {
            if (getOutput() instanceof String) {
                intermediateFile = new File(System.getProperty("java.io.tmpdir"), getOutput() + ".tmp.dim");
            } else {
                intermediateFile = new File(System.getProperty("java.io.tmpdir"), ((File) getOutput()).getName() + ".tmp.dim");
            }
            SystemUtils.LOG.info("writing to intermediate file " + intermediateFile.getPath());
            intermediateWriter.writeProductNodes(getSourceProduct(), intermediateFile);
        } else {
            _writeProductNodesImpl();
        }
    }

    private void _writeBandRasterData(Product sourceProduct) throws IOException {
        final int targetDataType = getTargetDataType(sourceProduct);
        final ArrayList<Band> bandsToExport = getBandsToExport(sourceProduct);

        RenderedImage writeImage;
        if (bandsToExport.size() > 1) {
            final ParameterBlock parameterBlock = new ParameterBlock();
            for (int i = 0; i < bandsToExport.size(); i++) {
                final Band subsetBand = bandsToExport.get(i);
                final RenderedImage sourceImage = getImageWithTargetDataType(targetDataType, subsetBand);
                parameterBlock.setSource(sourceImage, i);
            }
            writeImage = JAI.create("bandmerge", parameterBlock, null);
        } else {
            writeImage = getImageWithTargetDataType(targetDataType, bandsToExport.get(0));
        }

        GeoTIFFMetadata geoTIFFMetadata = ProductUtils.createGeoTIFFMetadata(sourceProduct);
        if (geoTIFFMetadata == null) {
            geoTIFFMetadata = new GeoTIFFMetadata();
        }
        final ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromRenderedImage(writeImage);
        final TIFFImageMetadata iioMetadata = (TIFFImageMetadata) GeoTIFF.createIIOMetadata(imageWriter, imageTypeSpecifier, geoTIFFMetadata,
                                                                                            "it_geosolutions_imageioimpl_plugins_tiff_image_1.0",
                                                                                            "it.geosolutions.imageio.plugins.tiff.BaselineTIFFTagSet,it.geosolutions.imageio.plugins.tiff.BaselineTIFFTagSet");


        addDimapMetaField(sourceProduct, iioMetadata);

        final SampleModel sampleModel = writeImage.getSampleModel();
        writeParam.setDestinationType(new ImageTypeSpecifier(new BogusAndCheatingColorModel(sampleModel), sampleModel));

        final IIOImage iioImage = new IIOImage(writeImage, null, iioMetadata);
        imageWriter.write(null, iioImage, writeParam);
    }

    private void createWriterParams() {
        writeParam = new TIFFImageWriteParam(Locale.ENGLISH);

        final String compressionType = Config.instance().preferences().get(PARAM_COMPRESSION_TYPE, COMPRESSION_TYPE_DEFAULT);
        if (StringUtils.isNotNullAndNotEmpty(compressionType) || COMPRESSION_TYPE_NONE.equals(compressionType)) {
            if (COMPRESSION_TYPE_DEFAULT.equals(compressionType)) {
                writeParam.setCompressionMode(TIFFImageWriteParam.MODE_EXPLICIT);

                final TIFFLZWCompressor compressor = new TIFFLZWCompressor(BaselineTIFFTagSet.PREDICTOR_NONE);
                writeParam.setTIFFCompressor(compressor);
                writeParam.setCompressionType(compressor.getCompressionType());

                final float compressionQuality = Config.instance().preferences().getFloat(PARAM_COMPRESSION_QUALITY, PARAM_COMPRESSION_QUALITY_DEFAULT);
                writeParam.setCompressionQuality(compressionQuality);
            } else {
                throw new IllegalArgumentException("Compression type '" + compressionType + "' is not supported");
            }
        }

        final String tilingWidthProperty = Config.instance().preferences().get(PARAM_TILING_WIDTH, null);
        final String tilingHeightProperty = Config.instance().preferences().get(PARAM_TILING_HEIGHT, null);
        if (StringUtils.isNotNullAndNotEmpty(tilingWidthProperty) && StringUtils.isNotNullAndNotEmpty(tilingHeightProperty)) {
            final int tileWidth = Integer.parseInt(tilingWidthProperty);
            final int tileHeight = Integer.parseInt(tilingHeightProperty);

            writeParam.setTilingMode(TIFFImageWriteParam.MODE_EXPLICIT);
            writeParam.setTiling(tileWidth, tileHeight, 0, 0);
        }

        final boolean forceBigTiff = Config.instance().preferences().getBoolean(PARAM_FORCE_BIGTIFF, false);
        writeParam.setForceToBigTIFF(forceBigTiff);
    }

    private void setWriteIntermediateProduct(boolean intermediate) {
        if (writingDataHasStarted) {
            throw new IllegalStateException("It is not allowed to change the state 'write intermediate product' " +
                                                    "after some data has already been written.");
        }
        withIntermediate = intermediate;
        if (intermediate) {
            intermediateWriter = new DimapProductWriterPlugIn().createWriterInstance();
        } else {
            intermediateWriter = null;
        }
    }

    private void _writeProductNodesImpl() throws IOException {
        outputFile = null;

        final File file;
        if (getOutput() instanceof String) {
            file = new File((String) getOutput());
        } else {
            file = (File) getOutput();
        }

        outputFile = FileUtils.ensureExtension(file, Constants.FILE_EXTENSIONS[0]);
        SystemUtils.LOG.info("writing to output file " + outputFile.getPath());

        deleteOutput();
        updateProductName();

        imageWriter = getTiffImageWriter();

        outputStream = new FileImageOutputStream(outputFile);
        imageWriter.setOutput(outputStream);
    }

    private void updateTilingParameter() {
        if (writeParam.getTilingMode() != TIFFImageWriteParam.MODE_EXPLICIT) {
            final Product sourceProduct = getSourceProduct();
            final MultiLevelImage firstSourceImage = sourceProduct.getBandAt(0).getSourceImage();
            final int tileWidth = firstSourceImage.getTileWidth();
            final int tileHeight = firstSourceImage.getTileHeight();

            writeParam.setTilingMode(TIFFImageWriteParam.MODE_EXPLICIT);
            writeParam.setTiling(tileWidth, tileHeight, 0, 0);
        }
    }

    private TIFFImageWriter getTiffImageWriter() {
        final Iterator<ImageWriter> writerIterator = ImageIO.getImageWritersByFormatName("TIFF");
        while (writerIterator.hasNext()) {
            final ImageWriter writer = writerIterator.next();
            if (writer instanceof TIFFImageWriter) {
                return (TIFFImageWriter) writer;
            }
        }
        throw new IllegalStateException("No appropriate image writer for format BigTIFF found.");
    }


    private ArrayList<Band> getBandsToExport(Product sourceProduct) {
        final int nodeCount = sourceProduct.getNumBands();
        final ArrayList<Band> bandsToWrite = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            final Band band = sourceProduct.getBandAt(i);
            if (shouldWrite(band)) {
                bandsToWrite.add(band);
            }
        }
        return bandsToWrite;
    }

    private int getTargetDataType(Product sourceProduct) {
        final TiffIFD tiffIFD = new TiffIFD(sourceProduct);
        final int maxSourceDataType = tiffIFD.getBandDataType();
        return ImageManager.getDataBufferType(maxSourceDataType);
    }

    private void addDimapMetaField(Product sourceProduct, TIFFImageMetadata iioMetadata) {
        final TIFFTag beamMetaTag = new TIFFTag("BEAM_METADATA", Constants.PRIVATE_BEAM_TIFF_TAG_NUMBER, TIFFTag.TIFF_ASCII);
        final String beamMetadata = getBeamMetadata(sourceProduct);

        final TIFFIFD rootIFD = iioMetadata.getRootIFD();
        final TIFFField beamMetaDataTiffField = new TIFFField(beamMetaTag, TIFFTag.TIFF_ASCII, 1, new String[]{beamMetadata});
        rootIFD.addTIFFField(beamMetaDataTiffField);
    }

    private String getBeamMetadata(final Product product) {
        final StringWriter stringWriter = new StringWriter();
        final DimapHeaderWriter writer = new DimapHeaderWriter(product, stringWriter, "");
        writer.writeHeader();
        writer.close();
        return stringWriter.getBuffer().toString();
    }

    private RenderedImage getImageWithTargetDataType(int targetDataType, Band subsetBand) {
        RenderedImage sourceImage = subsetBand.getSourceImage();
        final int actualTargetBandDataType = sourceImage.getSampleModel().getDataType();
        if (actualTargetBandDataType != targetDataType) {
            sourceImage = FormatDescriptor.create(sourceImage, targetDataType, null);
        }
        return sourceImage;
    }

    private void updateProductName() {
        if (outputFile != null) {
            getSourceProduct().setName(FileUtils.getFilenameWithoutExtension(outputFile));
        }
    }

    private static class BogusAndCheatingColorModel extends ColorModel {
        private SampleModel sampleModel;

        public BogusAndCheatingColorModel(SampleModel sampleModel) {
            super(8, new int[]{8}, ColorSpace.getInstance(ColorSpace.CS_GRAY), false, false, OPAQUE, DataBuffer.TYPE_BYTE);
            this.sampleModel = sampleModel;
        }

        @Override
        public boolean isCompatibleRaster(Raster raster) {
            return isCompatibleSampleModel(raster.getSampleModel());
        }

        @Override
        public boolean isCompatibleSampleModel(SampleModel sm) {
            return sampleModel.getNumBands() == sm.getNumBands() && sampleModel.getDataType() == sm.getDataType();
        }

        @Override
        public int getNumComponents() {
            return sampleModel.getNumBands();
        }

        @Override
        public int getRed(int pixel) {
            return 0;
        }

        @Override
        public int getGreen(int pixel) {
            return 0;
        }

        @Override
        public int getBlue(int pixel) {
            return 0;
        }

        @Override
        public int getAlpha(int pixel) {
            return 0;
        }
    }
}
