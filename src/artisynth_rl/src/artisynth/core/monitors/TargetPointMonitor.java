package artisynth.core.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import artisynth.core.inverse.TargetPoint;
import artisynth.core.modelbase.MonitorBase;

public class TargetPointMonitor extends MonitorBase {

	private TargetPoint myTargetPoint = new TargetPoint();
	private PrintStream out;

	public TargetPointMonitor(TargetPoint fm) {
		myTargetPoint = fm;
	}

	public TargetPoint getMyTargetPoint() {
		return myTargetPoint;
	}

	public void setMyTargetPoint(TargetPoint myFrameMarker) {
		this.myTargetPoint = myFrameMarker;
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
			out.println((int) t0 + "," + myTargetPoint.getPosition().x + ","
					+ myTargetPoint.getPosition().y + ","
					+ myTargetPoint.getPosition().z);
		}
	}
}