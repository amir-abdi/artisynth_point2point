package artisynth.core.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import artisynth.core.mechmodels.RigidBody;
import artisynth.core.modelbase.MonitorBase;
import maspack.spatialmotion.Wrench;

public class RigidBodyMonitor extends MonitorBase {

	private RigidBody myRigidBody = new RigidBody();
	private PrintStream out;

	public RigidBodyMonitor(RigidBody rb) {
		myRigidBody = rb;
	}

	public RigidBody getMyRigidBody() {
		return myRigidBody;
	}

	public void setMyRigidBody(RigidBody myRigidBody) {
		this.myRigidBody = myRigidBody;
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

		// position and velocity
		// myRigidBody.getOrientation();
		// forces
		if (out != null) {
			Wrench F = myRigidBody.getForce();
			Wrench FF = F;
			myRigidBody.getBodyForce(FF);
			out.println(t0 + " 2 " + myRigidBody.getPosition() + " 6 "
					+ myRigidBody.getVelocity() + " 10 " + F + " 17 " + FF
					+ " 24 ");
		}
	}
}