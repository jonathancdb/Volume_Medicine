/*
    This file is part of VolumetricAnalysis_Medicine.

    VolumetricAnalysis_Medicine is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    VolumetricAnalysis_Medicine is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with VolumetricAnalysis_Medicine. If not, see <http://www.gnu.org/licenses/>.


 VolumetricAnalysis_Medicine.java

 This script allows the user to select a point on a CT scan,
 finds the entire volume of the selected point, and isolates it.
 The plugin then calculates volume and dimensions, as well as visualizes
 the volume. This is intended for medical use as a tool for researchers and
 doctors to visualize complex anatomy.

 Author: Jonathan Collard de Beaufort, jonathancdb@gmail.com
 May 2024

 If you download and/or use this script, please email me. I am curious to hear
 from physicians and researchers on their experience.
 
 To cite this file: 
 
 Collard de Beaufort, J. (2024). Anatomical Isolation in CT Scans for DICOM: PlugIn for ImageJ. Zenodo. https://doi.org/10.5281/zenodo.11265937

*/

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.util.DicomTools;
import ij3d.Image3DUniverse;


import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class VolumetricAnalysis_Medicine implements PlugInFilter {
    private ImagePlus imp;
    private ImageProcessor ip; // Declare ip as an instance variable
    private int rescIntercept;
    private int rescSlope;
    private int W, H, numberSlices;
    private byte[] alreadyVisited;
    private double voxelWidth, voxelHeight, voxelDepth;
    private int lowRange;
    private int highRange;
    private int userDefinedHU;
    private int pixelSpacing;
    private double volume;

    @Override
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        return DOES_ALL;
    }

    @Override
    public void run(ImageProcessor ip) {
        this.ip = ip;

        if (imp == null) {
            IJ.error("No image opened!");
            return;
        }

        W = imp.getWidth();
        H = imp.getHeight();
        numberSlices = imp.getStackSize();
        alreadyVisited = new byte[W * H * numberSlices];

        String voxelSpacingStr = DicomTools.getTag(imp, "0028,0030");
        if (voxelSpacingStr != null) {
            String[] splitText = voxelSpacingStr.split("\\\\");
            voxelWidth = Double.parseDouble(splitText[0]);
            voxelDepth = Double.parseDouble(splitText[1]);
        }

        String sliceThicknessStr = DicomTools.getTag(imp, "0018,0050");
        if (sliceThicknessStr != null) {
            voxelHeight = Double.parseDouble(sliceThicknessStr);
        }

        String rescaleInterceptStr = DicomTools.getTag(imp, "0028,1052");
        String rescaleSlopeStr = DicomTools.getTag(imp, "0028,1053");
        rescIntercept = Integer.parseInt(rescaleInterceptStr.trim());
        rescSlope = Integer.parseInt(rescaleSlopeStr.trim());

        ImageCanvas canvas = imp.getCanvas();
        if (canvas == null) {
            IJ.error("No canvas found!");
            return;
        }

        IJ.showMessage("Click on the image", "Please click on the image to record the pixel coordinates.");

        // addMouseListener = captures a click
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                int offScreenX = canvas.offScreenX(x);
                int offScreenY = canvas.offScreenY(y);

                canvas.removeMouseListener(this);
                IJ.log("--- Recording Pixel Coordinate ---");
                IJ.log("Selected Pixel Coordinate: " + offScreenX + " " + offScreenY);

                logMetaData(imp);

                int noiseSensitivity = askForNoiseSensitivity();
                if (noiseSensitivity < 0) {
                    IJ.error("Invalid input. Please enter a number greater than 1");
                    return;
                }

                // Determine the HU value of the selected point and the threshold range
                int pixel = ip.getPixel(offScreenX, offScreenY);
                userDefinedHU = rescIntercept + rescSlope * pixel;
                lowRange = userDefinedHU - noiseSensitivity;
                highRange = userDefinedHU + noiseSensitivity;

                IJ.log("Selected HU: " + userDefinedHU);
                IJ.log("Threshold range: " + lowRange + " to " + highRange);

                // Test pixel logging
                //IJ.log("Test pixel: " + isSelection(new int[]{201, 273, 1}));

                ArrayList<int[]> seedArray = new ArrayList<>();
                seedArray.add(new int[]{offScreenX, offScreenY, imp.getCurrentSlice() - 1});

                ArrayList<int[]> airVoxels = generation(seedArray, 100);
                IJ.log("Voxels Found: " + airVoxels.size());

                // Create and show the new image
                ImagePlus filteredImage = IJ.createImage("Isolated Anatomy", "RGB Color", W, H, numberSlices);
                ImageProcessor processor = filteredImage.getProcessor();

                int colorGrad = 1;
                for (int[] voxel : airVoxels) {
                    int vx = voxel[0];
                    int vy = voxel[1];
                    int vz = voxel[2];

                    int val = 255 - (int) (0.001 * colorGrad);
                    int[] rgbVal = new int[]{val, 0, val};

                    filteredImage.setSlice(vz + 1);
                    processor.putPixel(vx, vy, rgbVal);
                    colorGrad++;
                }

                filteredImage.show();
                
				double[] pixelSpacing = {voxelWidth, voxelDepth};
                double volume = volumeCalculator(airVoxels.size(), pixelSpacing, voxelHeight);
                IJ.log("Volume: " + volume + "mm^3");

            }
        });
    }

    private int askForNoiseSensitivity() {
        GenericDialog gd = new GenericDialog("Noise Sensitivity");
        gd.addNumericField("Enter an integer (recommended: 200):", 200, 0);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return -1;
        }

        int noiseSensitivity = (int) gd.getNextNumber();
        if (noiseSensitivity < 1) {
            return -1;
        }
        return noiseSensitivity;
    }

    public void logMetaData(ImagePlus imp) {
        IJ.log(" ");
        IJ.log("Fetching Image Metadata...");

        String imgRowString = DicomTools.getTag(imp, "0028,0010").trim(); // Image Rows
        String imgColString = DicomTools.getTag(imp, "0028,0011").trim(); // Image Columns
        String pixSpacing = DicomTools.getTag(imp, "0028,0030").trim();   // Pixel Spacing
        String rescInterceptStr = DicomTools.getTag(imp, "0028,1052").trim();   // Rescale Intercept
        String rescSlopeStr = DicomTools.getTag(imp, "0028,1053").trim();   // Rescale Slope
        
        int pixelSpacing = convertPixelSpacingToInt(pixSpacing);
		
        try {
            int imgRow = Integer.parseInt(imgRowString);
            int imgCol = Integer.parseInt(imgColString);
            rescIntercept = Integer.parseInt(rescInterceptStr);
            rescSlope = Integer.parseInt(rescSlopeStr);

			// NOTE: Commented out log update -- unnecessary for average user
            /*IJ.log("Image Rows: " + imgRow + "\n" +
                   "Image Columns: " + imgCol + "\n" +
                   "Pixel Spacing: " + pixSpacing + "\n" +
                   "Rescale Intercept: " + rescIntercept + "\n" +
                   "Rescale Slope: " + rescSlope);
            */
        } catch (NumberFormatException e) {
            IJ.error("Error parsing DICOM metadata: " + e.getMessage());
        }
       
    }

    private int getIndex(int x, int y, int z) {
        return z * (W * H) + y * W + x;
    }

    private boolean isAlreadyVisited(int x, int y, int z) {
        return alreadyVisited[getIndex(x, y, z)] != 0;
    }

    private void setAlreadyVisited(int x, int y, int z) {
        alreadyVisited[getIndex(x, y, z)] = 1;
    }

    private boolean isSelection(int[] voxel) {
        if (voxel.length != 3) {
            IJ.error("Point without 3 coordinates.");
            return false;
        }
        int x = voxel[0];
        int y = voxel[1];
        int z = voxel[2];
        imp.setSlice(z + 1);
        int pixel = imp.getProcessor().getPixel(x, y);
        int HU = rescIntercept + rescSlope * pixel;

        return HU >= lowRange && HU <= highRange;
    }

    private ArrayList<int[]> appendIfNotDuplicate(ArrayList<int[]> originalList, ArrayList<int[]> listToAppend) {
        for (int[] point : listToAppend) {
            boolean duplicate = false;
            for (int[] existing : originalList) {
                if (existing[0] == point[0] && existing[1] == point[1] && existing[2] == point[2]) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                originalList.add(point);
            }
        }
        return originalList;
    }

    private ArrayList<int[]> bioVenture(ArrayList<int[]> cList) {
        ArrayList<int[]> airway = new ArrayList<>();
		
        for (int[] point : cList) {
            int x = point[0];
            int y = point[1];
            int z = point[2];

            if (!isAlreadyVisited(x, y, z)) {
                setAlreadyVisited(x, y, z);
                
                if (isSelection(new int[]{x, y, z})) {
                    int[][] neighbors = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
                    for (int[] disp : neighbors) {
                        int x2 = x + disp[0];
                        int y2 = y + disp[1];
                        int z2 = z + disp[2];
                        if (x2 >= 0 && x2 < W && y2 >= 0 && y2 < H && z2 >= 0 && z2 < numberSlices) {
                            if (isSelection(new int[]{x2, y2, z2}) && !isAlreadyVisited(x2, y2, z2)) {
                                airway.add(new int[]{x2, y2, z2});
                            }
                        }
                    }
                }
            }
        }
        return airway;
    }

    private ArrayList<int[]> generation(ArrayList<int[]> seed, int threshold) {
    	ArrayList<int[]> prevGen = seed;
    	ArrayList<int[]> newSearch = seed;
    	ArrayList<int[]> cumulative = new ArrayList<>();
    	while (newSearch.size() >= 0 && newSearch.size() < prevGen.size() * threshold) {
        	
        	prevGen = new ArrayList<>(newSearch);
        	ArrayList<int[]> nextGen = bioVenture(prevGen);
        	cumulative = appendIfNotDuplicate(cumulative, nextGen);
        	newSearch = nextGen;
    	}
    	return cumulative;
	}
	
	    public static int convertPixelSpacingToInt(String pixSpacing) {
        if (pixSpacing == null || pixSpacing.isEmpty()) {
            throw new IllegalArgumentException("Pixel Spacing is null or empty");
        }

        // Debug print to check the raw pixSpacing value
        System.out.println("Raw Pixel Spacing: " + pixSpacing);

        // Split the string to get the first part of the pixel spacing
        String[] parts = pixSpacing.split("\\\\");
        if (parts.length < 1) {
            throw new IllegalArgumentException("Invalid Pixel Spacing format");
        }

        // Debug print to check the parsed parts
        System.out.println("Parsed parts: ");
        for (String part : parts) {
            System.out.println(part);
        }

        try {
            // Convert the first part to a double
            double pixelSpacingDouble = Double.parseDouble(parts[0].trim());
            // Debug print to check the parsed double value
            System.out.println("Parsed double value: " + pixelSpacingDouble);
            // Convert the double to an integer
            return (int) pixelSpacingDouble;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Pixel Spacing is not a valid number", e);
        }
    }
    
	 public static double volumeCalculator(int numVoxels, double[] pixelSpacing, double sliceThickness) {
        double voxelVolume = pixelSpacing[0] * pixelSpacing[1] * sliceThickness;
        return numVoxels * voxelVolume;
    }
	
}