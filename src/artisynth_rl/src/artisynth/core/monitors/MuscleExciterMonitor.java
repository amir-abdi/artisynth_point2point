package artisynth.core.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.modelbase.MonitorBase;

public class MuscleExciterMonitor extends MonitorBase {

	private MuscleExciter mymex = new MuscleExciter();
	private PrintStream out;

	public MuscleExciterMonitor(MuscleExciter mex) {
		mymex = mex;
	}

	public MuscleExciter getMymex() {
		return mymex;
	}

	public void setMymex(MuscleExciter mex) {
		this.mymex = mex;
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
		if (out != null) {
			double E = mymex.getExcitation();

			if ((int) t0 % 2 == 1) {
				out.println((int) t0 + "," + E);
			}
		}
	}
}