package org.esa.beam.statistics.tools;

import static org.mockito.Mockito.*;

import org.junit.*;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SummaryCSVToolTest {

    private SummaryCSVTool.ShapeFileReader shapeFileReader;
    private Logger logger;
    private SummaryCSVTool summaryCSVTool;
    private File inputDir;
    private File existingShapeFile;

    @Before
    public void setUp() throws Exception {
        shapeFileReader = mock(SummaryCSVTool.ShapeFileReader.class);
        logger = mock(Logger.class);
        existingShapeFile = new File(SummaryCSVToolTest.class.getResource("20070504_out_cwbody_desh_gk3.shp").getFile());
        inputDir = Mockito.spy(existingShapeFile.getParentFile());
        summaryCSVTool = new SummaryCSVTool(logger, shapeFileReader);
    }

    @Test
    public void testThatAnErrorWasLoggedIfAnIOExceptionIsThrownWhileReadingAShapeFile() throws IOException {
        //preparation
        final String exceptionMessage = "Unable To read";
        when(shapeFileReader.read(any(File.class))).thenThrow(new IOException(exceptionMessage));

        //execution
        summaryCSVTool.summarize(inputDir);

        //verification
        verify(logger, times(1)).log(Level.WARNING, exceptionMessage);
    }

    @Test
    public void testThatNoErrorIsLoggedWhenShapeFileNameDoesCorrespondToDatePattern() throws IOException {
        //preparation
        when(shapeFileReader.read(any(File.class))).thenReturn(null);
        when(inputDir.listFiles(any(FilenameFilter.class))).thenReturn(new File[]{new File("20120509_chl.shp")});

        //execution
        summaryCSVTool.summarize(inputDir);

        //verification
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testThatAnErrorIsLoggedWhenShapeFileNameDoesNotCorrespondToDatePattern() {
        //preparation
        when(inputDir.listFiles(any(FilenameFilter.class))).thenReturn(new File[]{new File("blah_20120509.shp")});

        //execution
        summaryCSVTool.summarize(inputDir);

        //verification
        String expectedMessage = "The filename 'blah_20120509.shp' does not match the pattern yyyyMMdd_*.shp.";
        verify(logger, times(1)).log(Level.WARNING, expectedMessage);
    }
}
