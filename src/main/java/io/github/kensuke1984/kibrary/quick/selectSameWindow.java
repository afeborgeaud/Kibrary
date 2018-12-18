package io.github.kensuke1984.kibrary.quick;

import io.github.kensuke1984.kibrary.timewindow.TimewindowInformation;
import io.github.kensuke1984.kibrary.timewindow.TimewindowInformationFile;
import io.github.kensuke1984.kibrary.util.Utilities;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class selectSameWindow {

	public static void main(String[] args) throws IOException {
		Path targetwindowPath = Paths.get(args[0]);
		Path otherwindowPath = Paths.get(args[1]);
		
		Set<TimewindowInformation> targetwindows = TimewindowInformationFile.read(targetwindowPath);
		Set<TimewindowInformation> otherwindows = TimewindowInformationFile.read(otherwindowPath);
		
		Set<TimewindowInformation> selectedwindow = new HashSet<>();
		
		for (TimewindowInformation window : targetwindows) {
			TimewindowInformation selected = otherwindows.stream().parallel().filter(tw -> tw.getGlobalCMTID().equals(window.getGlobalCMTID())
					&& tw.getStation().equals(window.getStation())).findFirst().get();
			selectedwindow.add(selected);
		}
		
		Path outpath = Paths.get("timewindow" + Utilities.getTemporaryString() + ".dat");
		TimewindowInformationFile.write(selectedwindow, outpath);
		
	}

}
