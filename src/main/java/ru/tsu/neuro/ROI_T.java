
 /*
 * #%L
 * ROI_T plugin for Fiji.
 * %%
 * Copyright (C) 2022 Marina Khodanovich.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
  * @author Marina Khodanovich
 */

package ru.tsu.neuro;

import ij.IJ;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;


import ij.plugin.RGBStackMerge;
import ij.plugin.RGBStackConverter;
import ij.plugin.ChannelSplitter;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.WindowManager;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.gui.Roi;
import ij.gui.Overlay;
import ij.gui.YesNoCancelDialog;
import ij.gui.ImageWindow;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import java.awt.*;
import java.awt.List;
import java.util.*;
import ij.ImageListener;
import bigwarp.*;

 

public class ROI_T implements PlugInFilter, ImageListener {
	protected ImagePlus image;
	public ImagePlus sourceImage;
	public ImagePlus targetImage;
	public enum Direction {INC, DEC};
	private Direction direction;
 	private java.lang.String[] titles;
 	private ArrayList <ImagePlus> images;
	private int sourceRoiCount;
	private int[] roiInexesForMeasurements;
	private Double dScale = 1.0;
	private BigWarp bw;
	private boolean bwComplete = false;
	private boolean bwExit = false;
	
//////////////////////////////////////////////	
	@Override
	public int setup(String arg, ImagePlus imp) {	if (arg.equals("about")) {
		showAbout();
		return DONE;
	}
	image = imp;
	return DOES_ALL;
	}
	
/////////////////////////////////////////////////////////////////////	

	@Override
	public void run(ImageProcessor ip) {
		int count=ij.WindowManager.getImageCount();
		if (count <2){ 
			IJ.error("At least two images should be open!");
			return;
		}
		RoiManager rm;
		rm=ij.plugin.frame.RoiManager.getRoiManager();
		ResultsTable rt = new ij.measure.ResultsTable();
		int roiCount=rm.getCount();
 		if (roiCount<3){
 			IJ.error("You should specify at least 3 ROIs!");
 			return;
 		}
 		if (initDialog()==false)
			return;
 		ArrayList <Roi> rois = new ArrayList <Roi>();				
 		
 		images = new ArrayList <ImagePlus>();			//0 - sourceImage, 1 - targetImage, 2 - modifiedSourceImage, 
 		if (initData(rm, rt, rois, images)==false)
			return;
		if (roiToOverlay(rois, images.get(0))==false) {
			return;
		}
		int directionIndex1 = 0;
		int directionindex2 = 1;
		switch (direction) {
			case INC:
				if (scaleImage (dScale, images,0,false)==false){
					return;
				}	
				directionIndex1=2;
				directionindex2=1;
				break;
			case DEC:
				dScale = 1/dScale;
				if (scaleImage (dScale, images,1,false)==false){
					return;
				}	
				directionIndex1=0;
				directionindex2=2;
				break;
		}
		if (overlayToRois(rois, images.get(directionIndex1))==false)
			return;
		if (makeRGBCompositeImage(rm, rois, images, directionIndex1, ip)==false)
			return;
		bw = null;
		try		{
    		bw = new BigWarp(BigWarpInit.createBigWarpDataFromImages(images.get(directionIndex1), images.get(directionindex2) ), "bigwarp", null );
    		IJ.log("Big Warp registration");
		}
		catch(Exception e){
			IJ.showMessage("ROIT","Big Warp is unavailible");
			return;
			}
		bwComplete = false;
		ImagePlus.addImageListener(this);
		while (!bwComplete) {
			try {
			   Thread.sleep(10);
			} catch(InterruptedException ex){
				
			}
		}		
		if (bwExit) return;
		if (compositeToOverlay(rm, rois, images, 2)== false){
			IJ.showMessage("compositeToOverlay = false");
			return;
		}
		if (direction==Direction.DEC) {
			dScale = 1/dScale;
			if (scaleImage (dScale, images,2,true)==false){
				return;
			}	
		}
		if (overlayToRois(rois, images.get(2))==false){
			return; 
		}
		rm.runCommand("Show None");
		ij.WindowManager.toFront(images.get(1).getWindow());
		rm.toFront();
		int[] roiInexes = new int[rois.size()]; 
		int roiIndex = rm.getCount(); 
		if (!rois.isEmpty()){
			for (int i=0; i<rois.size();i++){
					rois.get(i).setName(rois.get(i).getName()+"-tr");
					rm.add(images.get(1),rois.get(i),-1);
					roiInexes[i] = roiIndex+i;
					rm.selectAndMakeVisible(images.get(1), roiInexes[i]); 
			}
		}
		ImagePlus.removeImageListener(this);
		IJ.log("exited");
	}
//////////////////////////////////////////////////////////////////
	public void imageOpened(ImagePlus imp) {
		IJ.log(imp.getTitle() + " opened");
		YesNoCancelDialog dg = new ij.gui.YesNoCancelDialog (imp.getWindow(), "ROIT", "Choose to complete or continue registration. To exit ROIT press Cancel", "Registration complete", "Continue registration");
		if (dg.yesPressed()==true){
			IJ.log("Registration completed");
			ImagePlus.removeImageListener(this);
			if (bw!=null){
				try {		
					bw.closeAll();	
					bw=null;	
				} catch(Exception e){}
			}
			images.set(2, imp);
			bwComplete= true;
			
		} else
		if (dg.cancelPressed()==true){
			ImagePlus.removeImageListener(this);
			if (bw!=null){
				try {		
					bw.closeAll();	
					bw=null;	
				} catch(Exception e){}
			}
			bwComplete= true;
			imp.close();
			bwExit= true;
		} else {
			IJ.log("Registration continued");
			bwComplete = false;
			imp.close();
		}
	}
	public void imageClosed(ImagePlus imp) {
		if (images.indexOf(imp)==-1) {
		}
	}

	public void imageUpdated(ImagePlus imp) {
		if (images.indexOf(imp)==-1) {
		}
	}
/////////////////////////////////////////////////////////////////////////
	private boolean initDialog() {
		GenericDialog gd = new GenericDialog("ROIT");
		java.lang.String[] items = {"MRI to histology", "Histology to MRI"}; 
		gd.addRadioButtonGroup("Direction of ROI transformation", items, 2, 1, items[0]);
		String[] choice = {"1","2"};
		gd.addChoice("The number of ROIs for transformation", choice, choice[0]);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		sourceRoiCount=gd.getNextChoiceIndex()+1;
		if (gd.getNextRadioButton()==items[0]){
			direction = Direction.INC;
		}else{
			direction = Direction.DEC;
		}
		return true;
	}
//////////////////////////////////////////////////////////////////////////	
	private boolean initData (RoiManager rm, ResultsTable rt, ArrayList<Roi> rois, ArrayList<ImagePlus> images) {
		String str1 = "";
		String str2 = "";
		switch (direction) {
			case INC:
				str1 = " (MRI)";
				str2 = " (Histology)";
				break;
			case DEC:
				str1 = " (Histology)";
				str2 = " (MRI)";
				break;
		}
		titles=ij.WindowManager.getImageTitles();
		GenericDialog gd = new GenericDialog("ROI transformation");
		java.awt.Font font=gd.getFont();
		double fontSize=font.getSize()*1.2;
		java.awt.Font font_new = new Font(font.getFontName(), font.BOLD, (int)fontSize);
 		gd.addMessage("Images:", font_new, Color.black);
 		gd.addImageChoice("Source image"+str1, titles[0]);
 		gd.addImageChoice("Target image"+str2, titles[1]);
 		int roiCount=rm.getCount();
		List lineRoiTitles = new List(); 
		List lineRoiIndexes = new List(); 
		List areaRoiTitles = new List(); 
		List areaRoiIndexes = new List(); 
		for (int i = 0; i < roiCount; i++) {
		
			if (rm.getRoi(i).isLine()==true) {
				lineRoiTitles.add(rm.getRoi(i).getName());
				lineRoiIndexes.add(String.valueOf(i));
			} 
			else if (rm.getRoi(i).isArea()==true) {
				areaRoiTitles.add(rm.getRoi(i).getName());
				areaRoiIndexes.add(String.valueOf(i));
			}
		}
		if (lineRoiTitles.getItemCount()<2) {
			IJ.showMessage("ROIT","Less than 2 line ROIs are specified");
			return false;
		}
 		gd.addMessage("Linear sizes of objects", font_new, Color.black);
		gd.addChoice("Source line"+str1, lineRoiTitles.getItems(), lineRoiTitles.getItem(0));
		gd.addChoice("Target line"+str2, lineRoiTitles.getItems(), lineRoiTitles.getItem(1));
		if (areaRoiTitles.getItemCount()<1) {
			IJ.showMessage("ROIT","Less than 1 area ROIs are specified");
			return false;
		} else {
			gd.addMessage("ROIs for transformation:", font_new, Color.black);
			gd.addChoice("Source ROI"+str1, areaRoiTitles.getItems(), areaRoiTitles.getItem(1));
			if ((sourceRoiCount==2) && (areaRoiTitles.getItemCount()>1)) {
				gd.addChoice("Source ROI-2"+str1, areaRoiTitles.getItems(), areaRoiTitles.getItem(1));
			}
		}
		gd.centerDialog(true);
		gd.showDialog(); 
		if (gd.wasCanceled())
			return false;
		images.add(gd.getNextImage());
		sourceImage=images.get(0);
		images.add(gd.getNextImage());
		targetImage=images.get(1);
		
		int lineSourceChoice=gd.getNextChoiceIndex();
		int lineSourceIndex=Integer.valueOf(lineRoiIndexes.getItem(lineSourceChoice));
		int lineTargetChoice=gd.getNextChoiceIndex();
		int lineTargetIndex=Integer.valueOf(lineRoiIndexes.getItem(lineTargetChoice));
		dScale=getScale(rm, lineSourceIndex, lineTargetIndex, sourceImage, targetImage);
		if (dScale==null) {
			return false; 
		}  
		int roiChoice=gd.getNextChoiceIndex();
		Roi roi1 = rm.getRoi(Integer.valueOf(areaRoiIndexes.getItem(roiChoice)));
		if (checkRoiSize(rm, roi1, sourceImage)==false) {
			return false; 
		} else{
			rois.add(roi1);
		}
		if (sourceRoiCount==2) {
			roiChoice=gd.getNextChoiceIndex();
			Roi roi2 = rm.getRoi(Integer.valueOf(areaRoiIndexes.getItem(roiChoice)));
			if (checkRoiSize(rm, roi2, sourceImage)==false) {
				return false; 
			} else{
				rois.add(roi2);  
			}
		}		
		return true;
	}
///////////////////////////////////////////////////////////////	
	private Double getScale(RoiManager rm, int lineSourceIndex, int lineTargetIndex, ImagePlus sourceImage, ImagePlus targetImage) {
		//Checking if source line is out of source image
		sourceImage.setRoi(rm.getRoi(lineSourceIndex),false);
		Rectangle rectS=rm.getRoi(lineSourceIndex).getBounds();
		if (((int)rectS.getWidth()>sourceImage.getWidth())||((int)rectS.getHeight()>sourceImage.getHeight())) {
			IJ.showMessage("ROIT","Source straight line is out of Source Image!");
			return null;
		}
		//Checking if target line is out of target image
		targetImage.setRoi(rm.getRoi(lineTargetIndex),false);
		Rectangle rectT=rm.getRoi(lineTargetIndex).getBounds();
		IJ.log("Check ROIs...");
		if (((int)rectT.getWidth()>targetImage.getWidth())||((int)rectT.getHeight()>targetImage.getHeight())) {
			IJ.showMessage("ROIT","Target straight line is out of Target Image!");
			return null;
		}
		//Checking sizes of source and target image vs direction
		switch (direction) {
			case INC:
				if ((sourceImage.getWidth()>targetImage.getWidth())||(sourceImage.getHeight()>targetImage.getHeight())) {
				IJ.showMessage("ROIT","Source image is larger than target image! Wrong for MRI-to-histology ROI transformation");
				return null;
				}
				break;
			case DEC: 
				if ((sourceImage.getWidth()<targetImage.getWidth())||(sourceImage.getHeight()<targetImage.getHeight())) {
				IJ.showMessage("ROIT","Source image is less than target image! Wrong for histology-to-MRI ROI transformation");
				return null;
				}
				break;
		}
		/// getting dScale
		double dSource=ij.IJ.getValue(sourceImage,"Perim."); 
		if (Double.isNaN(dSource)==true) {
			IJ.showMessage("ROIT","Source straight line is NaN!");
			return null;
		}
		double dTarget=ij.IJ.getValue(targetImage,"Perim.");
		if (Double.isNaN(dTarget)==true) {
			IJ.showMessage("ROIT","Target straight line is NaN!");
			return null;	
		}
		dSource=sourceImage.getCalibration().getRawX(dSource);
		dTarget=targetImage.getCalibration().getRawX(dTarget);
		dScale=dTarget/dSource;
		return dScale;
	}
       ////////////////////////////////////////////////////////////////
	boolean checkRoiSize(RoiManager rm, Roi roi, ImagePlus sourceImage) {
		Rectangle rectS=roi.getBounds();
		if (((int)rectS.getWidth()>sourceImage.getWidth())||((int)rectS.getHeight()>sourceImage.getHeight())) {
			IJ.showMessage("ROIT","Source ROI is out of Source Image!");
			return false;
		}
		return true; 
	}
	////////////////////////////////////////////////////////////////
	boolean roiToOverlay(ArrayList<Roi> rois, ImagePlus myImage) {
		ij.gui.Overlay overlay = new Overlay();
		if (myImage.getOverlay()!=null) {
			myImage.getOverlay().clear();
		}
		if (rois.isEmpty()){
			IJ.showMessage("ROIT","No ROIs to create overlay!");
			return false;
		} else {
			for (int i=0; i<rois.size(); i++){
				overlay.add(rois.get(i),rois.get(i).getName());
			}
		}
		myImage.setOverlay(overlay);			
		return true; 
	}
	/////////////////////////////////////////////////////////////
	boolean overlayToRois(ArrayList<Roi> rois, ImagePlus myImage) {
		Overlay overlay=myImage.getOverlay();
		if (overlay==null){
			IJ.showMessage("ROIT","overlay=null, image: "+myImage.getTitle());	
			return false;
		}
		if (rois.size()!=overlay.size()){
			IJ.showMessage("ROIT","overlays number does not match ROIs number");	
			return false;
		} 
		for (int i=0; i<overlay.size(); i++){
			rois.set(i,overlay.get(i));
		}
		int size = overlay.size();
		for (int i=0; i<size; i++){
			overlay.remove(i);
			size = size-1;
		}
		return true;  
	}
	/////////////////////////////////////////////////////////////
	boolean scaleImage(double scFactor, ArrayList<ImagePlus> images, int imageIndex, boolean replace) {
		int dstWidth = (int)Math.round(images.get(imageIndex).getWidth()*scFactor);
		int dstHeight = (int)Math.round(images.get(imageIndex).getHeight()*scFactor);
		if ((dstWidth==0)||(dstHeight==0)){
			IJ.showMessage("ROIT","Wrong parameters, scaling factor: "+String.valueOf(scFactor));
			return false; 
		}
		ImagePlus scaledImage = images.get(imageIndex).resize(dstWidth, dstHeight, "bilinear");
		IJ.showMessage("ROIT","Scale image.."+ images.get(imageIndex).getTitle()+", Scaling factor: "+ String.valueOf(dScale));
		scaledImage.setTitle("Scaled image "+images.get(imageIndex).getTitle());
		if (!replace) {
			images.add(scaledImage);
			return true; 
		}else {
			images.set(imageIndex, scaledImage);
			return true; 
		}
	}
	/////////////////////////////////////////////////////////////
	boolean makeRGBCompositeImage(RoiManager rm, ArrayList<Roi> rois, ArrayList<ImagePlus> images, int imageIndex, ImageProcessor ip) {
			if (rois.size()==0){
				IJ.showMessage("ROIT"," No ROIs!");
				return false;
			}
			ImagePlus [] channelImages = new ImagePlus[rois.size()+1];
			channelImages[0] = images.get(imageIndex).duplicate();;
			ImagePlus img = images.get(imageIndex);
			ip = channelImages[0].getProcessor();
	//////////////////decreasing bit depth to 8-bit
			if (ip.getBitDepth()>8) {
				ip.convertToByte(true);
			}
	//////////////////Getting masks
			ImageProcessor mask = new ByteProcessor(ip,true);
			Roi roi;
			for (int i=0; i<(rois.size()); i++){
				roi = rois.get(i);
				channelImages[0].setRoi(roi,false);
				
				mask = channelImages[0].createRoiMask();
				if (mask==null){
					return false;	
				}				
				channelImages[i+1] = new ImagePlus("mask"+String.valueOf(i), mask);
			}
			IJ.log("Masks are got, channelImages.count = "+String.valueOf(channelImages.length));
	/////////changing channel order 0<->1, creating composite image and convert to RGB
			ImagePlus tmpImage = channelImages[0];
			channelImages[0] = channelImages[1];
			channelImages[1] = tmpImage; //main image index is 1, green; ROIs - 0 - red, 2- blue
			RGBStackMerge merge;
			merge = new ij.plugin.RGBStackMerge();
			ImagePlus composite = merge.mergeChannels(channelImages, true); 
			RGBStackConverter converter = new ij.plugin.RGBStackConverter();
			converter.convertToRGB(composite);
			images.set(imageIndex,composite);
			IJ.log("Composite image: "+images.get(imageIndex).getTitle());
		return true; 
	}
	/////////////////////////////////////////////////////////////
	boolean compositeToOverlay(RoiManager rm, ArrayList<Roi> rois, ArrayList<ImagePlus> images, int imageIndex) {
		if (rois.isEmpty()){
			IJ.showMessage("ROIT","No ROIs to create overlay!");
			return false;
		}
		
		ChannelSplitter spl = new ij.plugin.ChannelSplitter();
		ImagePlus [] channels =  spl.split(images.get(imageIndex));
		if (channels.length<2){
			IJ.showMessage("ROIT","Wrong composite!"+String.valueOf(channels.length)+" channels");
			return false;
		}
		int baseIndex = 1;
		ImageProcessor ip;
		Roi roi;
		int roiIndex=0;
		String roiName = "Undefined";
		ThresholdToSelection thr = new ij.plugin.filter.ThresholdToSelection();
		for (int i=0; (i<channels.length)&&(i!=baseIndex); i++){
			ip = channels[i].getProcessor();
			ip.setThreshold(1,255, ip.NO_LUT_UPDATE);	
			roi = thr.convert(ip) ;
			channels[baseIndex].setRoi(roi, false);
			roiName = rois.get(roiIndex).getName();
			roi.setName(roiName);
			rois.set(roiIndex, roi);
			roiIndex=roiIndex+1;
		}
        if (roiToOverlay(rois, channels[baseIndex])==false){
        	return false;
        }
        images.set(2, channels[baseIndex]);
		return true; 
	}
////////////////////////////////////////////////////////////////////////////

	public void showAbout() {
		IJ.showMessage("ROIT",
				"This plugin is designed for MRI-to-histology or histology-to-MRI transformation of region of interest. "
			);
	}

	
}
