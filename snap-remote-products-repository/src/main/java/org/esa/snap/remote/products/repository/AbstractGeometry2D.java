package org.esa.snap.remote.products.repository;

import java.awt.geom.Path2D;

/**
 * Created by jcoravu on 10/9/2019.
 */
public abstract class AbstractGeometry2D {

    protected AbstractGeometry2D() {
    }

    public abstract int getPathCount();

    public abstract Path2D.Double getPathAt(int index);

    public abstract String toWKT();
}