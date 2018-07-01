/*
 * Copyright 2018 Laszlo Balazs-Csiki and Contributors
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

package pixelitor.tools.brushes;

import pixelitor.tools.shapes.StrokeType;
import pixelitor.tools.util.PPoint;

/**
 * A brush that paints with vector-based "perfect" circles
 */
public class IdealBrush extends StrokeBrush {

    public IdealBrush(int radius) {
        super(radius, StrokeType.BASIC);
    }

    @Override
    public void drawStartShape(PPoint p) {
        double x = p.getImX();
        double y = p.getImY();

        targetG.fillOval((int) x - radius, (int) y - radius, diameter, diameter);
    }
}
