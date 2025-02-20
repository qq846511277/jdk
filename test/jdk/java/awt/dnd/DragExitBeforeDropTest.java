/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.awt.Button;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/*
 * @test
 * @bug 4395290
 * @key headful
 * @summary tests that dragExit() is not called before drop()
 */

public class DragExitBeforeDropTest {
    private static Frame frame;
    private static final DragSourceButton dragSourceButton = new DragSourceButton();
    private static final DropTargetPanel dropTargetPanel = new DropTargetPanel();
    private static volatile Point srcPoint;
    private static volatile Point dstPoint;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            EventQueue.invokeAndWait(DragExitBeforeDropTest::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                Point p = dragSourceButton.getLocationOnScreen();
                Dimension d = dragSourceButton.getSize();
                p.translate(d.width / 2, d.height / 2);
                srcPoint = p;

                p = dropTargetPanel.getLocationOnScreen();
                d = dropTargetPanel.getSize();
                p.translate(d.width / 2, d.height / 2);
                dstPoint = p;
            });

            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            for (; !srcPoint.equals(dstPoint);
                 srcPoint.translate(sign(dstPoint.x - srcPoint.x),
                                    sign(dstPoint.y - srcPoint.y))) {
                robot.mouseMove(srcPoint.x, srcPoint.y);
                robot.delay(10);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();
            robot.delay(1000);

            if (!dropTargetPanel.getStatus()) {
                throw new RuntimeException("The test failed: dragExit()"
                                           + " is called before drop()");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new Frame("DragExitBeforeDropTest");
        frame.setLayout(new GridLayout(2, 1));
        frame.add(dragSourceButton);
        frame.add(dropTargetPanel);
        frame.setLocationRelativeTo(null);
        frame.setSize(300, 400);
        frame.setVisible(true);
    }

    public static int sign(int n) {
        return Integer.compare(n, 0);
    }

    private static class DragSourceButton extends Button implements Serializable,
            Transferable,
            DragGestureListener,
            DragSourceListener {
        private final DataFlavor dataflavor =
                new DataFlavor(Button.class, "DragSourceButton");

        public DragSourceButton() {
            this("DragSourceButton");
        }

        public DragSourceButton(String str) {
            super(str);

            DragSource ds = DragSource.getDefaultDragSource();
            ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY,
                                                  this);
        }

        public void dragGestureRecognized(DragGestureEvent dge) {
            dge.startDrag(null, this, this);
        }

        public void dragEnter(DragSourceDragEvent dsde) {}

        public void dragExit(DragSourceEvent dse) {}

        public void dragOver(DragSourceDragEvent dsde) {}

        public void dragDropEnd(DragSourceDropEvent dsde) {}

        public void dropActionChanged(DragSourceDragEvent dsde) {}

        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException, IOException {

            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }

            Object retObj;

            ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
            ObjectOutputStream ooStream = new ObjectOutputStream(baoStream);
            ooStream.writeObject(this);

            ByteArrayInputStream baiStream =
                    new ByteArrayInputStream(baoStream.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(baiStream);
            try {
                retObj = ois.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e.toString());
            }

            return retObj;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { dataflavor };
        }

        public boolean isDataFlavorSupported(DataFlavor dflavor) {
            return dataflavor.equals(dflavor);
        }
    }

    private static class DropTargetPanel extends Panel implements DropTargetListener {

        final Dimension preferredDimension = new Dimension(200, 100);
        volatile boolean testPassed = true;

        public DropTargetPanel() {
            setDropTarget(new DropTarget(this, this));
        }

        public boolean getStatus() {
            return testPassed;
        }

        public Dimension getPreferredSize() {
            return preferredDimension;
        }

        public void dragEnter(DropTargetDragEvent dtde) {}

        public void dragExit(DropTargetEvent dte) {
            testPassed = false;
        }

        public void dragOver(DropTargetDragEvent dtde) {}

        public void dropActionChanged(DropTargetDragEvent dtde) {}

        public void drop(DropTargetDropEvent dtde) {
            DropTargetContext dtc = dtde.getDropTargetContext();

            if ((dtde.getSourceActions() & DnDConstants.ACTION_COPY) != 0) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
            } else {
                dtde.rejectDrop();
            }

            DataFlavor[] dfs = dtde.getCurrentDataFlavors();
            Component comp = null;

            if(dfs != null && dfs.length >= 1) {
                Transferable transfer = dtde.getTransferable();

                try {
                    comp = (Component)transfer.getTransferData(dfs[0]);
                } catch (Throwable e) {
                    e.printStackTrace();
                    dtc.dropComplete(false);
                }
            }
            dtc.dropComplete(true);
            add(comp);
        }
    }
}



