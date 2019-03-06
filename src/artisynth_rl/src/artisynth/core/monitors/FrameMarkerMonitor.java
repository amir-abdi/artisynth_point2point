package artisynth.core.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.modelbase.MonitorBase;

public class FrameMarkerMonitor extends MonitorBase {

	public FrameMarker myFrameMarker = new FrameMarker();
	public PrintStream out;

	public FrameMarkerMonitor(FrameMarker fm) {
		myFrameMarker = fm;
	}

	public FrameMarker getMyFrameMarker() {
		return myFrameMarker;
	}

	public void setMyFrameMarker(FrameMarker myFrameMarker) {
		this.myFrameMarker = myFrameMarker;
	}

	public void openFile(File f) throws FileNotFoundException {
		out = new PrintStream(f);
		writeHeader();
	}

	public void closeFile() {
		writeFooter();
		out.close();
	}

	public void writeHeader() {
	}

	public void writeFooter() {
	}

	@Override
	public void apply(double t0, double t1) {
		if ((int) t0 % 2 == 1) {
			out.println((int) t0 + "," + myFrameMarker.getPosition().x + ","
					+ myFrameMarker.getPosition().y + ","
					+ myFrameMarker.getPosition().z);
		}
	}
}