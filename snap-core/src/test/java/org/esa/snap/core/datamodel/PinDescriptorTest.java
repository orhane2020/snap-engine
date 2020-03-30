package org.esa.snap.core.datamodel;

import org.locationtech.jts.geom.Point;
import org.esa.snap.core.dataio.DecodeQualification;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import static org.junit.Assert.*;

/**
 * @author Norman Fomferra
 */
public class PinDescriptorTest {

    private PlacemarkDescriptor instance = PinDescriptor.getInstance();

    @Test
    public void testGetInstance() throws Exception {
        assertNotNull(instance);
        assertSame(instance, PinDescriptor.getInstance());
        assertSame(PinDescriptor.class, instance.getClass());
    }

    @Test
    public void testGetBaseFeatureType() throws Exception {
        SimpleFeatureType ft = instance.getBaseFeatureType();
        assertNotNull(ft);
        assertEquals("org.esa.snap.Pin", ft.getTypeName());
        assertEquals(7, ft.getAttributeCount());
        assertEquals("geometry", ft.getGeometryDescriptor().getLocalName());
    }

    @Test
    public void testGetQualification() throws Exception {
        SimpleFeatureType ft = instance.getBaseFeatureType();
        assertEquals(DecodeQualification.INTENDED, instance.getCompatibilityFor(ft));

          // todo - this is not sufficient: in order to return INTENDED, the given featureType must be equal-to or derived-from DEFAULT_FEATURE_TYPE (nf - 2012-04-23)
        assertEquals(DecodeQualification.INTENDED, instance.getCompatibilityFor(createCompatibleFT("org.esa.snap.Pin")));

        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.setName("org.esa.snap.Pineapple");
        assertEquals(DecodeQualification.UNABLE, instance.getCompatibilityFor(ftb.buildFeatureType()));
    }

    @Test
    public void testSetUserData() throws Exception {
        SimpleFeatureType ft1 = createCompatibleFT("org.esa.snap.Pineapple");
        assertEquals(0, ft1.getUserData().size());
        instance.setUserDataOf(ft1);
        assertEquals(2, ft1.getUserData().size());
        assertEquals("geometry", ft1.getUserData().get("defaultGeometry"));
        assertEquals("org.esa.snap.core.datamodel.PinDescriptor", ft1.getUserData().get("placemarkDescriptor"));

        SimpleFeatureType ft2 = createCompatibleFT("org.esa.snap.Pin");
        assertEquals(0, ft2.getUserData().size());
        instance.setUserDataOf(ft2);
        assertEquals(2, ft2.getUserData().size());
        assertEquals("geometry", ft2.getUserData().get("defaultGeometry"));
        assertEquals("org.esa.snap.core.datamodel.PinDescriptor", ft2.getUserData().get("placemarkDescriptor"));

        SimpleFeatureType ft3 = createIncompatibleFT("org.esa.snap.GroundControlPoint");
        assertEquals(0, ft3.getUserData().size());
        instance.setUserDataOf(ft3);
        assertEquals(1, ft3.getUserData().size());
        assertEquals("org.esa.snap.core.datamodel.PinDescriptor", ft2.getUserData().get("placemarkDescriptor"));
    }

    @Test
    public void testCreatePlacemark() throws Exception {
        final SimpleFeatureType ft = instance.getBaseFeatureType();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(ft);
        final SimpleFeature f = fb.buildFeature("id1", new Object[ft.getAttributeCount()]);
        final Placemark placemark = instance.createPlacemark(f);

        assertNotNull(placemark);
        assertSame(f, placemark.getFeature());
        assertSame(instance, placemark.getDescriptor());
    }

    @Test
    public void testDeprecatedProperties() throws Exception {
        final Product product = new Product("n", "t", 1, 1);
        assertSame(product.getPinGroup(), instance.getPlacemarkGroup(product));
        assertEquals("pin", instance.getRoleName());
        assertEquals("pin", instance.getRoleLabel());
        assertNull(instance.getCursorImage());
        assertNotNull(instance.getCursorHotSpot());
        assertEquals("showPinOverlay", instance.getShowLayerCommandId());
    }

    @Test
    public void testUpdatePixelPos() throws Exception {

    }

    @Test
    public void testUpdateGeoPos() throws Exception {

    }

    SimpleFeatureType createCompatibleFT(String name) {
        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.setName(name);
        ftb.add("geometry", Point.class);
        ftb.setDefaultGeometry("geometry");
        return ftb.buildFeatureType();
    }


    SimpleFeatureType createIncompatibleFT(String name) {
        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.setName(name);
        ftb.add("geometry", Float.class);
        return ftb.buildFeatureType();
    }
}
