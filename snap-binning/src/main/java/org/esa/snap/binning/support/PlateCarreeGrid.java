/*
 * Copyright (C) 2014 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.binning.support;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.esa.snap.binning.MosaickingGrid;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.common.reproject.ReprojectionOp;
import org.esa.snap.core.image.ImageManager;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a PlanetaryGrid that uses a Plate Carree grid layout.
 *
 * @author Marco Zühlke
 */
public class PlateCarreeGrid implements MosaickingGrid {

    private final int numRows;
    private final int numCols;
    private final double pixelSize;
    private final double[] latBin;
    private GeometryFactory geometryFactory;

    public PlateCarreeGrid() {
        this(SEAGrid.DEFAULT_NUM_ROWS);
    }

    public PlateCarreeGrid(int numRows) {
        if (numRows < 2) {
            throw new IllegalArgumentException("numRows < 2");
        }
        if (numRows % 2 != 0) {
            throw new IllegalArgumentException("numRows % 2 != 0");
        }

        this.numRows = numRows;
        this.numCols = numRows * 2;
        this.pixelSize = 360.0 / numCols;

        this.latBin = new double[numRows];
        for (int row = 0; row < numRows; row++) {
            this.latBin[row] = 90.0 - (row + 0.5) * 180.0 / numRows;
        }
        this.geometryFactory = new GeometryFactory();
    }

    @Override
    public long getBinIndex(double lat, double lon) {
        final long row = getRowIndex(lat);
        final long col = getColIndex(lon);
        return row * numCols + col;
    }

    @Override
    public int getRowIndex(long binIndex) {
        return (int) (binIndex / numCols);
    }

    @Override
    public long getNumBins() {
        return numRows * (long) numCols;
    }

    @Override
    public int getNumRows() {
        return numRows;
    }

    @Override
    public int getNumCols(int row) {
        return numCols;
    }

    @Override
    public long getFirstBinIndex(int row) {
        return row * ((long) numCols);
    }

    @Override
    public double getCenterLat(int row) {
        return latBin[row];
    }

    @Override
    public double[] getCenterLatLon(long binIndex) {
        final int row = getRowIndex(binIndex);
        return new double[]{
                getCenterLat(row),
                getCenterLon((int) (binIndex % numCols))
        };
    }

    public double getCenterLon(int col) {
        return 360.0 * (col + 0.5) / numCols - 180.0;
    }


    public int getColIndex(double lon) {
        if (lon <= -180.0) {
            return 0;
        }
        if (lon >= 180.0) {
            return numCols - 1;
        }
        return (int) ((180.0 + lon) * numCols / 360.0);
    }

    public int getRowIndex(double lat) {
        if (lat <= -90.0) {
            return numRows - 1;
        }
        if (lat >= 90.0) {
            return 0;
        }
        return (numRows - 1) - (int) ((90.0 + lat) * (numRows / 180.0));
    }

    @Override
    public Product reprojectToGrid(Product sourceProduct) {
        final ReprojectionOp repro = new ReprojectionOp();

        repro.setParameter("resampling", "Nearest");
        repro.setParameter("includeTiePointGrids", true);
        repro.setParameter("orientation", 0.0);
        repro.setParameter("pixelSizeX", pixelSize);
        repro.setParameter("pixelSizeY", pixelSize);
        repro.setParameter("crs", DefaultGeographicCRS.WGS84.toString());

        Dimension tileSize = ImageManager.getPreferredTileSize(sourceProduct);
        repro.setParameter("tileSizeX", tileSize.width);
        repro.setParameter("tileSizeY", tileSize.height);

        int width = numCols;
        int height = numRows;
        double x = width / 2.0;
        double y = height / 2.0;

        repro.setParameter("easting", 0.0);
        repro.setParameter("northing", 0.0);
        repro.setParameter("referencePixelX", x);
        repro.setParameter("referencePixelY", y);
        repro.setParameter("width", width);
        repro.setParameter("height", height);

        repro.setSourceProduct(sourceProduct);
        Product targetProduct = repro.getTargetProduct();
        // reprojected products lack time information
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        return targetProduct;
    }


    public Rectangle[] getDataSliceRectangles(Geometry productGeometry, Dimension tileSize) {
        Rectangle productBoundingBox = computeBounds(productGeometry);
        Rectangle gridAlignedBoundingBox = MosaickingGrid.alignToTileGrid(productBoundingBox, tileSize);
        final int xStart = gridAlignedBoundingBox.x / tileSize.width;
        final int yStart = gridAlignedBoundingBox.y / tileSize.height;
        final int width = gridAlignedBoundingBox.width / tileSize.width;
        final int height = gridAlignedBoundingBox.height / tileSize.height;
        List<Rectangle> rectangles = new ArrayList<>(width * height);

        for (int y = yStart; y < yStart + height; y++) {
            for (int x = xStart; x < xStart + width; x++) {
                Geometry tileGeometry = getTileGeometry(x, y, tileSize);
                Geometry intersection = productGeometry.intersection(tileGeometry);
                if (!intersection.isEmpty() && intersection.getDimension() == 2) {
                    Rectangle tileRect = new Rectangle(x * tileSize.width, y * tileSize.height, tileSize.width,
                                                       tileSize.height);
                    rectangles.add(productBoundingBox.intersection(tileRect));
                }
            }
        }
        return rectangles.toArray(new Rectangle[0]);
    }

    @Override
    public GeoCoding getGeoCoding(Rectangle outputRegion) {
        try {
            return new CrsGeoCoding(DefaultGeographicCRS.WGS84, outputRegion.width, outputRegion.height,
                                    -180.0D + this.pixelSize * outputRegion.x,
                                    90.0D - this.pixelSize * outputRegion.y,
                                    this.pixelSize, this.pixelSize, 0.0D, 0.0D);
        } catch (TransformException | FactoryException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Rectangle computeBounds(Geometry roiGeometry) {
        Rectangle region = new Rectangle(numCols, numRows);
        if (roiGeometry != null) {
            final Coordinate[] coordinates = roiGeometry.getBoundary().getCoordinates();
            double gxmin = Double.POSITIVE_INFINITY;
            double gxmax = Double.NEGATIVE_INFINITY;
            double gymin = Double.POSITIVE_INFINITY;
            double gymax = Double.NEGATIVE_INFINITY;
            for (Coordinate coordinate : coordinates) {
                gxmin = Math.min(gxmin, coordinate.x);
                gxmax = Math.max(gxmax, coordinate.x);
                gymin = Math.min(gymin, coordinate.y);
                gymax = Math.max(gymax, coordinate.y);
            }
            final int x = (int) Math.floor((180.0 + gxmin) / pixelSize);
            final int y = (int) Math.floor((90.0 - gymax) / pixelSize);
            final int width = (int) Math.ceil((gxmax - gxmin) / pixelSize);
            final int height = (int) Math.ceil((gymax - gymin) / pixelSize);
            final Rectangle unclippedOutputRegion = new Rectangle(x, y, width, height);
            region = unclippedOutputRegion.intersection(region);
        }
        return region;
    }

    private Geometry getTileGeometry(int tileX, int tileY, Dimension tileSize) {
        int tileWidth = tileSize.width;
        int tileHeight = tileSize.height;
        double x1 = tileXToDegree(tileX, tileWidth);
        double x2 = tileXToDegree(tileX + 1, tileWidth);
        double y1 = tileYToDegree(tileY, tileHeight);
        double y2 = tileYToDegree(tileY + 1, tileHeight);

        return geometryFactory.toGeometry(new Envelope(x1, x2, y1, y2));
    }

    private double tileXToDegree(int tileX, int tileWidth) {
        return (tileX * tileWidth * 360.0 / numCols) - 180.0;
    }

    private double tileYToDegree(int tileY, int tileHeight) {
        return 90.0 - (tileY * tileHeight * 180.0 / numRows);
    }

}
