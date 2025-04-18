This GitHub repository contains the the code, models, and graphical user interface as described in the publication: LINK

Specifically, in the files above, we provide the code for iPlayer, a tool to facilitate time windowed annotation of video. The directly downloadable compiled versions of iPlayer are available under "Releases".

## How do I download iPlayer?
To download the ready-to-use version of iPlayer:

1. From the GitHub page, the most up-to-date version is located in the "Releases" section (right side panel).
   
2. Download the zip file for your operating system (Windows, MacOS).
3. Once downloaded, unzip and open the file.
4. Double click on "iPlayer.app" (MacOS) or "iPlayer.exe" (Windows) to open the tool. 

*Note.* On MacOS, you may have to allow by following these instructions: https://support.apple.com/en-ca/guide/mac-help/mh40617/mac. On Windows, you may have to select "More info" then "Run anyway" on the pop-up window.

## How do I use iPlayer?
Please read the publication for a full description of the tool, how to use the tool, and how to customize the annotation framework. In brief, to use iPlayer:

1. Customize your XML file (in the same folder as iPlayer) with your annotation framework and keyboard shortcuts

2. Open iPlayer
3. Load your video file (File>Load video)
4. Either: generate a new output .csv file (File>Save as) or load a previously started .csv (File>Load csv)
5. Use pre-defined keyboard shortcuts to annotate each window
6. Use enter key or arrow keys to advance to the next window
7. Save output frequently while annotating by using File>Save or keyboard shortcuts


## How do I cite iPlayer?
Please cite any use of iPlayer by citing the originating publication: Elyse Letts, Damien Masson, Joyce Obeid. (2025) iPlayer: an open-source tool for time-windowed video annotation of human physical activity and behaviour. *medRxiv.* LINK


## How to run iPlayer from the code

You will need gradle, Java, and JavaFX installed on your machine (for example, you can use [Azul Zulu Builds](https://www.azul.com/downloads/?package=jdk-fx#zulu) that can come with JavaFX already), then:

```
.\gradlew run
```