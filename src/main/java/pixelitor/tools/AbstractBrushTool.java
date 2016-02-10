/*
 * Copyright 2016 Laszlo Balazs-Csiki
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

package pixelitor.tools;

import org.jdesktop.swingx.combobox.EnumComboBoxModel;
import pixelitor.Composition;
import pixelitor.filters.gui.AddDefaultButton;
import pixelitor.filters.gui.FilterSetting;
import pixelitor.filters.gui.RangeParam;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.gui.PixelitorWindow;
import pixelitor.gui.utils.OKDialog;
import pixelitor.gui.utils.SliderSpinner;
import pixelitor.layers.ImageLayer;
import pixelitor.tools.brushes.Brush;
import pixelitor.tools.brushes.BrushAffectedArea;
import pixelitor.tools.brushes.SymmetryBrush;
import pixelitor.utils.ImageSwitchListener;

import javax.swing.*;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static pixelitor.Composition.ImageChangeActions.HISTOGRAM;
import static pixelitor.gui.utils.SliderSpinner.TextPosition.WEST;

/**
 * Abstract superclass for tools like brush, erase, clone.
 */
public abstract class AbstractBrushTool extends Tool implements ImageSwitchListener {
    private static final int MIN_BRUSH_RADIUS = 1;
    public static final int MAX_BRUSH_RADIUS = 100;
    public static final int DEFAULT_BRUSH_RADIUS = 10;

    private boolean respectSelection = true; // false while tracing a selection

    private JComboBox<BrushType> typeSelector;

    protected Graphics2D graphics;
    private final RangeParam brushRadiusParam = new RangeParam("Radius", MIN_BRUSH_RADIUS, DEFAULT_BRUSH_RADIUS, MAX_BRUSH_RADIUS, AddDefaultButton.NO, WEST);

    private final EnumComboBoxModel<Symmetry> symmetryModel = new EnumComboBoxModel<>(Symmetry.class);

    protected Brush brush;
    private SymmetryBrush symmetryBrush;
    protected BrushAffectedArea brushAffectedArea;

    private boolean firstMouseDown = true; // for the first click don't draw lines even if it is a shift-click
    private JButton brushSettingsButton;

    DrawStrategy drawStrategy;

    AbstractBrushTool(char activationKeyChar, String name, String iconFileName, String toolMessage, Cursor cursor) {
        super(activationKeyChar, name, iconFileName, toolMessage,
                cursor, true, true, false, ClipStrategy.IMAGE_ONLY);
        ImageComponents.addImageSwitchListener(this);
        initBrushVariables();
    }

    protected void initBrushVariables() {
        symmetryBrush = new SymmetryBrush(
                this, BrushType.values()[0], getSymmetry(), getRadius());
        brush = symmetryBrush;
        brushAffectedArea = symmetryBrush.getAffectedArea();
    }

    protected void addTypeSelector() {
        typeSelector = new JComboBox<>(BrushType.values());
        settingsPanel.addWithLabel("Type:", typeSelector, "brushTypeSelector");
        typeSelector.addActionListener(e -> {
            closeToolDialog();

            BrushType brushType = (BrushType) typeSelector.getSelectedItem();
            symmetryBrush.brushTypeChanged(brushType, getRadius());

            brushRadiusParam.setEnabled(brushType.sizeCanBeSet(), FilterSetting.EnabledReason.APP_LOGIC);

            brushSettingsButton.setEnabled(brushType.hasSettings());
        });

        // make sure all values are visible without a scrollbar
        typeSelector.setMaximumRowCount(BrushType.values().length);
    }

    protected void addSizeSelector() {
        SliderSpinner brushSizeSelector = (SliderSpinner) brushRadiusParam.createGUI();
        settingsPanel.add(brushSizeSelector);
        brushRadiusParam.setAdjustmentListener(this::setupDrawingRadius);
        setupDrawingRadius();
    }

    protected void addSymmetryCombo() {
        JComboBox<Symmetry> symmetryCombo = new JComboBox<>(symmetryModel);
        settingsPanel.addWithLabel("Mirror:", symmetryCombo, "symmetrySelector");
        symmetryCombo.addActionListener(e -> symmetryBrush.symmetryChanged(
                getSymmetry(), getRadius()));
    }

    protected void addBrushSettingsButton() {
        brushSettingsButton = settingsPanel.addButton("Brush Settings",
                e -> {
                    BrushType brushType = (BrushType) typeSelector.getSelectedItem();
                    JPanel p = brushType.getSettingsPanel(this);
                    toolDialog = new OKDialog(PixelitorWindow.getInstance(), p, "Brush Settings");
                });

        brushSettingsButton.setEnabled(false);
    }

    @Override
    public void mousePressed(MouseEvent e, ImageComponent ic) {
        boolean withLine = withLine(e);
        double x = userDrag.getStartX();
        double y = userDrag.getStartY();

        newMousePoint(ic.getComp(), x, y, withLine);
        firstMouseDown = false;

        if (withLine) {
            brushAffectedArea.updateAffectedCoordinates(x, y);
        } else {
            brushAffectedArea.initAffectedCoordinates(x, y);
        }
    }

    protected boolean withLine(MouseEvent e) {
        return !firstMouseDown && e.isShiftDown();
    }

    @Override
    public void mouseDragged(MouseEvent e, ImageComponent ic) {
        double x = userDrag.getEndX();
        double y = userDrag.getEndY();

        // at this point x and y are already scaled according to the zoom level
        // (unlike e.getX(), e.getY())

        newMousePoint(ic.getComp(), x, y, false);
    }

    @Override
    public void mouseReleased(MouseEvent e, ImageComponent ic) {
        if (graphics == null) {
            // we can get here if the mousePressed was an Alt-press, therefore
            // consumed by the color picker. Nothing was drawn, therefore
            // there is no need to save a backup, we can just return
            // TODO is this true after all the refactorings?
            return;
        }

        Composition comp = ic.getComp();
        finishBrushStroke(comp);
    }

    private void finishBrushStroke(Composition comp) {
        int radius = getRadius();
        ToolAffectedArea affectedArea = new ToolAffectedArea(comp,
                brushAffectedArea.getRectangleAffectedByBrush(radius), false);
        saveSubImageForUndo(drawStrategy.getOriginalImage(comp), affectedArea);

        if (graphics != null) {
            graphics.dispose();
        }
        graphics = null;

        drawStrategy.finishBrushStroke(comp);

        comp.getActiveMaskOrImageLayer().updateIconImage();

        comp.imageChanged(HISTOGRAM);
    }

    public void drawBrushStrokeProgrammatically(Composition comp, Point start, Point end) {
        prepareProgrammaticBrushStroke(comp, start);

        Brush paintingBrush = getPaintingBrush();
        paintingBrush.onDragStart(start.x, start.y);
        paintingBrush.onNewMousePoint(end.x, end.y);

        finishBrushStroke(comp);
    }

    protected void prepareProgrammaticBrushStroke(Composition comp, Point start) {
        drawStrategy.prepareBrushStroke(comp);
        ImageLayer layer = comp.getActiveMaskOrImageLayer();
        graphics = createGraphicsForNewBrushStroke(comp, layer);
    }

    /**
     * Creates the global Graphics2D object graphics.
     */
    private Graphics2D createGraphicsForNewBrushStroke(Composition comp, ImageLayer layer) {
        Composite composite = getComposite();
        Graphics2D g = drawStrategy.createDrawGraphics(layer, composite);
        initializeGraphics(g);
        if (respectSelection) {
            comp.applySelectionClipping(g, null);
        }

        brush.setTarget(comp, g);
        return g;
    }

    /**
     * An opportunity to do extra tool-specific
     * initializations in the subclasses
     */
    protected void initializeGraphics(Graphics2D g) {
    }

    // overridden in brush tools with blending mode
    protected Composite getComposite() {
        return null;
    }

    /**
     * Called from mousePressed, mouseDragged
     */
    private void newMousePoint(Composition comp, double x, double y, boolean connectClickWithLine) {
        if (graphics == null) { // a new brush stroke has to be initialized
            drawStrategy.prepareBrushStroke(comp);
            ImageLayer imageLayer = comp.getActiveMaskOrImageLayer();
            graphics = createGraphicsForNewBrushStroke(comp, imageLayer);
            graphics.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

            if (connectClickWithLine) {
                brush.onNewMousePoint(x, y);
            } else {
                brush.onDragStart(x, y);
            }
        } else {
            brush.onNewMousePoint(x, y);
        }
    }

    private void setupDrawingRadius() {
//        System.out.println("AbstractBrushTool::setupDrawingRadius: CALLED " +
//                "for " + this.getClass().getName());

        brush.setRadius(getRadius());
    }

    @Override
    protected void toolStarted() {
        super.toolStarted();
        resetState();
    }

    @Override
    public void noOpenImageAnymore() {

    }

    @Override
    public void newImageOpened(Composition comp) {
        resetState();
    }

    @Override
    public void activeImageHasChanged(ImageComponent oldIC, ImageComponent newIC) {
        resetState();
    }

    private void resetState() {
        firstMouseDown = true;
        respectSelection = true;
    }

    /**
     * Traces the given shape with the current brush tool
     */
    public void trace(Composition comp, Shape shape) {
        try {
            respectSelection = false;

            drawStrategy.prepareBrushStroke(comp);

            ImageLayer imageLayer = comp.getActiveMaskOrImageLayer();
            graphics = createGraphicsForNewBrushStroke(comp, imageLayer);

            doTraceAfterSetup(shape);

            finishBrushStroke(comp);
        } finally {
            resetState();
        }
    }

    private void doTraceAfterSetup(Shape shape) {
        int startingX = 0;
        int startingY = 0;

        PathIterator fpi = new FlatteningPathIterator(shape.getPathIterator(null), 1.0);
        float[] coords = new float[2];
        while (!fpi.isDone()) {
            int type = fpi.currentSegment(coords);
            int x = (int) coords[0];
            int y = (int) coords[1];
            brushAffectedArea.updateAffectedCoordinates(x, y);

            switch (type) {
                case PathIterator.SEG_MOVETO:
                    startingX = x;
                    startingY = y;

                    brush.onDragStart(x, y);

                    break;
                case PathIterator.SEG_LINETO:
                    brush.onNewMousePoint(x, y);

                    break;
                case PathIterator.SEG_CLOSE:
                    brush.onNewMousePoint(startingX, startingY);
                    break;
                default:
                    throw new IllegalArgumentException("type = " + type);
            }

            fpi.next();
        }
    }

    public void increaseBrushSize() {
        brushRadiusParam.increaseValue();
    }

    public void decreaseBrushSize() {
        brushRadiusParam.decreaseValue();
    }

    protected Symmetry getSymmetry() {
        return symmetryModel.getSelectedItem();
    }

    protected int getRadius() {
        int value = brushRadiusParam.getValue();

        // because of a JDK bug, sometimes it is possible to drag the slider to negative values
        if (value < MIN_BRUSH_RADIUS) {
            value = MIN_BRUSH_RADIUS;
            brushRadiusParam.setValue(MIN_BRUSH_RADIUS);
        }

        return value;
    }

    @Override
    protected boolean doColorPickerForwarding() {
        return true;
    }

    protected Brush getPaintingBrush() {
        return brush;
    }
}
