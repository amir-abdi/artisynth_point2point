package artisynth.core.inverse;

import java.util.ArrayList;

import maspack.matrix.Vector3d;

public interface InverseModel {
	public void resetTargetPosition();
	// public double getReward();

	public Vector3d getInitPosition();
}
