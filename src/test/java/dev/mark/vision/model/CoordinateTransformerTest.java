package dev.mark.vision.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateTransformerTest {

    @Test
    void testDenormalizePointCenter() {
        ScreenDimension screen = new ScreenDimension(1536, 864, 1.0);
        CoordinateTransformer transformer = new CoordinateTransformer(screen);

        ScreenPoint center = transformer.denormalizePoint(500, 500);
        assertEquals(768, center.x());
        assertEquals(432, center.y());
    }

    @Test
    void testDenormalizePointClamping() {
        ScreenDimension screen = new ScreenDimension(1536, 864, 1.0);
        CoordinateTransformer transformer = new CoordinateTransformer(screen);

        ScreenPoint clampedUpper = transformer.denormalizePoint(1200, 1500);
        assertTrue(clampedUpper.x() < 1536);
        assertTrue(clampedUpper.y() < 864);
        assertEquals(1535, clampedUpper.x());
        assertEquals(863, clampedUpper.y());

        ScreenPoint clampedLower = transformer.denormalizePoint(-100, -50);
        assertEquals(0, clampedLower.x());
        assertEquals(0, clampedLower.y());
    }

    @Test
    void testDenormalizeBox() {
        ScreenDimension screen = new ScreenDimension(1000, 500, 1.0);
        CoordinateTransformer transformer = new CoordinateTransformer(screen);

        // Normalized: ymin=100, xmin=200, ymax=300, xmax=600
        BoundingBox box = transformer.denormalizeBox(100, 200, 300, 600);

        assertEquals(200, box.x());
        assertEquals(50, box.y());
        assertEquals(400, box.width());
        assertEquals(100, box.height());
        assertEquals(400, box.center().x());
        assertEquals(100, box.center().y());
    }

    @Test
    void testBoundingBoxIoU() {
        BoundingBox boxA = new BoundingBox(100, 100, 100, 100); // Area 10000
        BoundingBox boxB = new BoundingBox(150, 100, 100, 100); // Area 10000, Intersects [150, 100, 50, 100] -> Area 5000

        assertTrue(boxA.intersects(boxB));
        BoundingBox inter = boxA.intersection(boxB);
        assertNotNull(inter);
        assertEquals(5000, inter.area());

        // Union: 10000 + 10000 - 5000 = 15000. IoU = 5000 / 15000 = 0.3333...
        double iou = boxA.intersectionOverUnion(boxB);
        assertEquals(1.0 / 3.0, iou, 0.001);

        BoundingBox disjoint = new BoundingBox(500, 500, 50, 50);
        assertFalse(boxA.intersects(disjoint));
        assertEquals(0.0, boxA.intersectionOverUnion(disjoint));
    }
}
