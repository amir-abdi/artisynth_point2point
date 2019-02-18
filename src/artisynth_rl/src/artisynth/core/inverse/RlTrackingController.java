/**
 * Copyright (c) 2014, by the Authors: Amir Abdi (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package artisynth.core.inverse;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import artisynth.core.inverse.ForceTarget;
import artisynth.core.inverse.MotionForceInverseData;
import artisynth.core.inverse.MotionTargetTerm;
import artisynth.core.inverse.TargetFrame;
import artisynth.core.inverse.TargetPoint;
import artisynth.core.inverse.TrackingController;
import artisynth.core.mechmodels.ExcitationComponent;
import artisynth.core.mechmodels.MechSystemBase;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.Point;
import artisynth.core.mechmodels.PointList;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.modelbase.ComponentList;
import artisynth.core.modelbase.ComponentListImpl;
import artisynth.core.modelbase.ModelComponent;
import artisynth.core.modelbase.ReferenceList;
import artisynth.core.modelbase.RenderableComponentList;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.workspace.RootModel;
import artisynth.models.rl.InverseModel;
import artisynth.models.rl.Log;
import artisynth.models.rl.NetworkHandler;
import maspack.matrix.VectorNd;

/**
 * The RL (reinforcement Learning) controller for computing muscle activations
 * based on a set of kinematic target trajectories.
 * <p>
 * 
 * Terminology: <br>
 * <table summary="">
 * <tbody>
 * <tr>
 * <td>"Targets"</td>
 * <td>are trajectories of points/frames that we wish our model to follow</td>
 * </tr>
 * <tr>
 * <td>"Markers" or "Sources"</td>
 * <td>are the points/frames in the model we want to follow the target
 * trajectories</td>
 * </tr>
 * <tr>
 * <td>"Excitation"</td>
 * <td>we actually mean, "activation", since we are currently ignoring the
 * excitation dynamics</td>
 * </tr>
 * </tbody>
 * </table>
 * <p>
 * 
 * 
 * @author Amir Abdi (amirabdi@ece.ubc.ca) based on the TrackingController by
 *         Ian Stavness
 *
 */
public class RlTrackingController extends TrackingController {

	protected int port;
	NetworkHandler networkHandler;
	InverseModel myInverseModel;

	/**
	 * Creates a tracking controller for a given mech system
	 * 
	 * @param m mech system, typically your "MechModel"
	 */
	public RlTrackingController(MechSystemBase m, InverseModel model) {
		this(m, model, null, 8097);
	}

	/**
	 * Set the RootModel
	 */
	public void setInverseModel(InverseModel m) {
		myInverseModel = m;
	}

	/**
	 * Creates and names a tracking controller for a given mech system
	 * 
	 * @param m    mech system, typically your "MechModel"
	 * @param name name to give the controller
	 */
	public RlTrackingController(MechSystemBase m, InverseModel model,
			String name, int port) {
		super(m, name);
		setMech(m);
		setName(name);
		setInverseModel(model);

		this.port = port;
		networkHandler = new NetworkHandler(port);
		networkHandler.start();

		// amirabdi: explicitely setting costFunction to null
		myCostFunction = null;

		// amirabdi: ??
		myComponents = new ComponentListImpl<ModelComponent>(
				ModelComponent.class, this);

		// list of target points that store/show the location of motion targets
		// for
		// points
		targetPoints = new PointList<TargetPoint>(TargetPoint.class,
				"targetPoints");
		// always show this component, even if it's empty:
		targetPoints.setNavpanelVisibility(NavpanelVisibility.ALWAYS);
		add(targetPoints);

		// list of target points that store/show the location of motion targets
		// for
		// bodies
		targetFrames = new RenderableComponentList<TargetFrame>(
				TargetFrame.class, "targetFrames");
		// always show this component, even if it's empty:
		targetFrames.setNavpanelVisibility(NavpanelVisibility.ALWAYS);
		add(targetFrames);

		// amirabdi: what are target forces and what role do they play?!!
		// list of target reaction forces that store/show the target forces
		targetForces = new RenderableComponentList<ForceTarget>(
				ForceTarget.class, "targetForces");
		// always show this component, even if it's empty:
		targetForces.setNavpanelVisibility(NavpanelVisibility.ALWAYS);
		add(targetForces);

		// reference lists to point to the various dynamic components that are
		// sources
		// these components are all expected to be of type MotionTargetComponent
		sourcePoints = new ReferenceList("sourcePoints");
		sourceFrames = new ReferenceList("sourceFrames");
		add(sourcePoints);
		add(sourceFrames);

		// amirabdi: not sure what to set useMyExciters
		if (useMyExciters) {
			myExciters = new MuscleExciter("exciters");
			add(myExciters);
		} else {
			// list of excitations that store the computed excitations from the
			// tracking
			// simulation
			exciters = new ComponentList<ExcitationComponent>(
					ExcitationComponent.class, "excitationSources");
			// always show this component, even if it's empty:
			exciters.setNavpanelVisibility(NavpanelVisibility.ALWAYS);
			add(exciters);
		}

		// amirabdi: still the same ambiguity for forces
		myMotionForceData = new MotionForceInverseData(this);

		myMotionTerm = new MotionTargetTerm(this);

		// amirabdi: I guess this is needed
		setExcitationBounds(0d, 1d);
	}

	/**
	 * Applies the controller, gets the next set of muscle activations from the
	 * agent through network connection
	 */
	@Override
	public void apply(double t0, double t1) {
		JSONObject jo_receive = networkHandler.getMessage();
		// TODO: Not sure yet whether to empty the queu in one time step?
		// or empty until reaching a setExcitation thingy?
		if (jo_receive != null)
			applyMessage(jo_receive);
		// System.out.println("dt = "+(t1-t0)+" h = "+ TimeBase.round(t1 - t0));
		if (getDebug()) {
			System.out.println("\n--- t = " + t1 + " ---"); // cleans up the
															// console
		}

		if (!isEnabled()) {
			return;
		}

		// amirabdi: set exications using such call...
		// setExcitations(myExcitations, 0);
	}

	public void applyMessage(JSONObject jo_receive) {
		try {
			log("advance: jo_receive = " + jo_receive.getString("type"));
			switch (jo_receive.getString("type")) {
			case "reset":
				myInverseModel.resetTargetPosition();
				break;
			case "setExcitations":
				setExcitations(jo_receive.getJSONArray("excitations"));
				break;
			case "getState":
				sendState();
				break;
			case "getStateSize":
				sendStateSize();
				break;
			default:
				Log.log("Unknown packet type: " + jo_receive.getString("type"));
				break;
			}
		} catch (JSONException e) {
			log("Error in advance: " + e.getMessage());
		}

	}

	private void sendStateSize() {
		Log.log("Sending state size");
		int state_size = 0;

		// destination, ref, target
		ArrayList<MotionTargetComponent> targets = myMotionTerm.getTargets(); 
		
		// real current position
		ArrayList<MotionTargetComponent> sources = myMotionTerm.getSources(); 

		for (MotionTargetComponent c : targets) {
			state_size += c.getVelStateSize();
			state_size += c.getPosStateSize();
		}

		for (MotionTargetComponent c : sources) {
			state_size += c.getVelStateSize();
			state_size += c.getPosStateSize();
		}

		state_size += numExcitations();
		Log.log("state_size: " + state_size);
		JSONObject jo = new JSONObject();
		try {
			jo.put("type", "stateSize");
			jo.put("stateSize", state_size);
			log(jo);
			networkHandler.send(jo);
		} catch (JSONException e) {
			System.out.println("Error in send: " + e.getMessage());
		}
	}

	public void sendState() {
		ArrayList<MotionTargetComponent> targets = myMotionTerm.getTargets(); 
		ArrayList<MotionTargetComponent> sources = myMotionTerm.getSources(); 

		VectorNd exs = new VectorNd(new double[numExcitations()]);
		int idx = getExcitations(exs, 0);		
		assert idx == numExcitations() + 1;

		try {
			JSONObject jo = new JSONObject();
			
			JSONArray targets_array = point2JsonArray(targets);
			jo.put("targets", targets_array);

			JSONArray sources_array = point2JsonArray(sources);
			jo.put("sources", sources_array);
			
			jo.put("type", "state");			
			jo.put("excitations", exs.getBuffer());
			
			networkHandler.send(jo);
		} catch (JSONException e) {
			Log.log("Error in send: " + e.getMessage());
		}
	}

	private JSONArray point2JsonArray(
			ArrayList<MotionTargetComponent> components) throws JSONException {
		JSONArray jarray = new JSONArray();
		for (MotionTargetComponent c : components) {

			VectorNd stateVec = new VectorNd(new double[6]);
			((Point) c).getState(stateVec, 0);
			
			JSONObject jo_temp = new JSONObject();
			jo_temp.put("posVel", stateVec.getBuffer());
			jo_temp.put("name", c.getName());

			jarray.put(jo_temp);
		}

		return jarray;
	}

	public void setExcitations(JSONArray jsonArrayExications)
			throws JSONException {
		double[] excitations = new double[numExcitations()];
		//log("setExcitations");
		for (int i = 0; i < jsonArrayExications.length(); ++i) {
			//log(i);
			excitations[i] = jsonArrayExications.getDouble(i);
		}
		myExcitations.set(excitations);
		setExcitations(myExcitations, 0);
	}

	public void log(Object obj) {
		System.out.println(obj);
	}
}
