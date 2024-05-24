# volume-medicine

This file is part of the VolumetricAnalysis_Medicine software.

## Introduction

Welcome to the Volumetric Analysis in Medicine!

This software is designed specifically for physicians and researchers, enabling the user to select and visualize anatomical structures on a CT scan and calculate their volume with ease.

VolumetricAnalysis_Medicine intends to provide a greater ability to analyze and describe complex patient anatomy. For the latter, this tool allows the user to describe the selected anatomy, such as but not limited to volume, cubic volume, and dimensions.

## Instructions for Usage 

This plugin *requires a stack* (ie a series of 2D images, called slices, that represent a volume). To open a stack or image sequence in FIJI ImageJ, follow these steps:

### Open a CT Scan:

- Click on "File" > "Import" > "Image Sequence"
- Use the "Browse" button in the pop up to locate the **folder** containing the file sequence
- Select "Ok"

These files *must* be in DICOM format (this is standard in medicine). The code is able to function on any type of anatomic scans (transverse, sagittal, and coronal scans).

### PlugIn Installation:
- 


### Select an Anatomical Structure:
- Run the PlugIn and follow prompts
- You will be prompted to first select a point on the anatomical structure you wish to analyze
- Click this point on the CT scan

### Threshold
Radio density can vary based on subtle differences. Threshold parameter allows you to specify the tolerance of the program.

## Future Directions
Next update will focus on improving run time of the algorithm and a streamlined run process.

## Contact
For support, comments, or feedback, please contact the author:
> Jonathan Collard de Beaufort
> jonathancdb@gmail.com

## License
This software is licensed under the MIT License. See the LICENSE file for more details.

Thank you for using VolumetricAnalysis_Medicine. We hope it enhances your medical practice and research.


