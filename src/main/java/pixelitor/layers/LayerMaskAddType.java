/*
 * Copyright 2019 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.layers;

import pixelitor.selection.Selection;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.image.BufferedImage;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;

/**
 * Ways to create a new layer mask
 */
public enum LayerMaskAddType {
    REVEAL_ALL("Reveal All", false) {
        @Override
        BufferedImage getBWImage(Layer layer, int width, int height, Selection selection) {
            // a fully white image
            return createFilledImage(width, height, Color.WHITE, null, null);
        }
    }, HIDE_ALL("Hide All", false) {
        @Override
        BufferedImage getBWImage(Layer layer, int width, int height, Selection selection) {
            // a fully black image
            return createFilledImage(width, height, Color.BLACK, null, null);
        }
    }, REVEAL_SELECTION("Reveal Selection", true) {
        @Override
        BufferedImage getBWImage(Layer layer, int width, int height, Selection selection) {
            // back image, but the selection is white
            return createFilledImage(width, height, Color.BLACK, Color.WHITE, selection.getShape());
        }
    }, HIDE_SELECTION("Hide Selection", true) {
        @Override
        BufferedImage getBWImage(Layer layer, int width, int height, Selection selection) {
            // white image, but the selection is black
            return createFilledImage(width, height, Color.WHITE, Color.BLACK, selection.getShape());
        }
    }, FROM_TRANSPARENCY("From Transparency", false) {
        @Override
        BufferedImage getBWImage(Layer layer, int width, int height, Selection selection) {
            return createMaskFromLayer(layer, true, width, height);
        }
    }, FROM_LAYER("From Layer", false) {
        @Override
        BufferedImage getBWImage(Layer layer, int width, int height, Selection selection) {
            return createMaskFromLayer(layer, false, width, height);
        }
    }, PATTERN ("Pattern", false) { // only for debugging

        @Override
        BufferedImage getBWImage(Layer layer, int width, int height, Selection selection) {
            BufferedImage bi = createFilledImage(width, height, Color.WHITE, null, null);
            Graphics2D g = bi.createGraphics();
            float cx = width / 2.0f;
            float cy = height / 2.0f;
            float radius = Math.min(cx, cy);
            float[] fractions = {0.5f, 1.0f};
            Paint gradient = new RadialGradientPaint(cx, cy, radius, fractions, new Color[]{
                    Color.WHITE, Color.BLACK
            });
            g.setPaint(gradient);
            g.fillRect(0, 0, width, height);
            g.dispose();
            return bi;
        }
    };

    private static BufferedImage createFilledImage(int width, int height, Color bg, Color fg, Shape shape) {
        BufferedImage bwImage = new BufferedImage(width, height, TYPE_BYTE_GRAY);
        Graphics2D g = bwImage.createGraphics();

        // fill background
        g.setColor(bg);
        g.fillRect(0, 0, width, height);

        // fill foreground
        if(fg != null) {
            g.setColor(fg);
            if (shape != null) {
                g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
                g.fill(shape);
            } else {
                g.fillRect(0, 0, width, height);
            }
        }
        g.dispose();
        return bwImage;
    }

    private static BufferedImage createMaskFromLayer(Layer layer,
                                                     boolean onlyTransparency,
                                                     int width, int height) {
        if (layer instanceof ImageLayer) {
            ImageLayer imageLayer = (ImageLayer) layer;
            BufferedImage image = imageLayer.getCanvasSizedSubImage();
            return createMaskFromImage(image, onlyTransparency, width, height);
        } else if (layer instanceof TextLayer) {
            TextLayer textLayer = (TextLayer) layer;
            // the rasterized image is canvas-sized, exactly as we want it
            BufferedImage rasterizedImage = textLayer.createRasterizedImage();
            return createMaskFromImage(rasterizedImage, onlyTransparency, width, height);
        } else {
            // there is nothing better
            return REVEAL_ALL.getBWImage(layer, width, height, null);
        }
    }

    private static BufferedImage createMaskFromImage(BufferedImage image,
                                                     boolean onlyTransparency,
                                                     int width, int height) {
        assert width == image.getWidth();
        assert height == image.getHeight();

        BufferedImage bwImage = new BufferedImage(width, height, TYPE_BYTE_GRAY);
        Graphics2D g = bwImage.createGraphics();

        // fill the background with white so that transparent parts become white
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        if (onlyTransparency) {
            // with DstOut only the source alpha will matter
            g.setComposite(AlphaComposite.DstOut);
        }

        g.drawImage(image, 0, 0, null);
        g.dispose();
        return bwImage;
    }

    private final String guiName;
    private final boolean needsSelection;

    LayerMaskAddType(String guiName, boolean needsSelection) {
        this.guiName = guiName;
        this.needsSelection = needsSelection;
    }

    abstract BufferedImage getBWImage(Layer layer, int width, int height, Selection selection);

    @Override
    public String toString() {
        return guiName;
    }

    /**
     * Returns true if the action needs selection and there is no selection.
     */
    public boolean missingSelection(Selection selection) {
        if(needsSelection) {
            return selection == null;
        } else {
            return false;
        }
    }

    public boolean needsSelection() {
        return needsSelection;
    }
}
