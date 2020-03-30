/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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
package org.esa.snap.core.dataio.dimap.spi;

import org.esa.snap.core.datamodel.Product;
import org.jdom.Element;

import java.awt.Dimension;

/**
 * Interface to implemented by clients who know how to read objects from
 * and write object to BEAM-DIMAP XML.
 *
 * <p><i>Note that this class is not yet public API. Interface may change in future releases.</i>
 *
 * @author Marco Peters
 * @version $Revision$ $Date$
 */
public interface DimapPersistable {
    Object createObjectFromXml(Element element, Product product, Dimension regionRasterSize);
    Element createXmlFromObject(Object object);
}
