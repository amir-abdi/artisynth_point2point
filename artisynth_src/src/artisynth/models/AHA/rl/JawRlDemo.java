package artisynth.models.AHA.rl;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import org.json.JSONException;
import org.json.JSONObject;

import maspack.geometry.PolygonalMesh;
import maspack.matrix.AxisAngle;
import maspack.matrix.Plane;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.RotationMatrix3d;
import maspack.matrix.Vector3d;
import maspack.properties.Property;
import maspack.render.RenderProps;
import maspack.render.Renderer.LineStyle;
import maspack.util.ReaderTokenizer;
import maspack.widgets.LabeledControl;
import maspack.widgets.ValueChangeEvent;
import maspack.widgets.ValueChangeListener;
import artisynth.core.driver.Main;
import artisynth.core.gui.ControlPanel;
import artisynth.core.mechmodels.AxialSpring;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.MechModel;
import artisynth.core.mechmodels.MechSystemSolver.Integrator;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.PlanarConnector;
import artisynth.core.mechmodels.PointForce;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.mechmodels.BodyConnector;
import artisynth.core.modelbase.ComponentUtils;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.probes.NumericInputProbe;
import artisynth.core.probes.NumericOutputProbe;
import artisynth.core.probes.TracingProbe;
import artisynth.core.util.ArtisynthIO;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.util.ScalableUnits;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.RootModel;
import artisynth.models.dynjaw.FoodBolus;
import artisynth.models.dynjaw.JawDemo;
import artisynth.models.dynjaw.JawModel;
import artisynth.models.dynjaw.JawPanel;

public class JawRlDemo extends JawDemo
{
	protected String probesFilename = "rightchew.art";
	protected String workingDirname = "data/rlDemo/";
	NetworkHandler networkHandler;
	static float MAX_ROTATION = 20;

	Random rand = new Random();	

	@Override
	public StepAdjustment advance(double t0, double t1, int flags)
	{	   	        
//		JSONObject jo_receive = networkHandler.getMessage();
//		if (jo_receive != null)
//		{			
//			try {
//				switch (jo_receive.getString("type")) 
//				{
//				case "reset":
//					//resetRefPosition();					
//					break;
//				case "excitations":
//					//setExcitations(jo_receive);
//					break;
//				case "getState":
//					//sendState();
//					break;
//				default:
//					break;
//				}
//			}
//			catch (JSONException e) {
//				log("Error in advance: " + e.getMessage());
//			}
//		}
		if (t0 == 1)
			setRandomJawCentricRotation();
		return super.advance (t0, t1, flags);
	}
	
	public void log(Object obj)
	{
		System.out.println (obj);
	}


	@Override
	public void build (String[] args) throws IOException {
		super.build (args);

		//networkHandler = new NetworkHandler();
		//networkHandler.start ();
	}

	void setRandomJawCentricRotation()
	{
		//		RigidBody jaw  = myJawModel.rigidBodies().get("jaw");
		//		Vector3d p = new Vector3d(0, 0, 0);
		//		AxisAngle axisAng = new AxisAngle(axis, rand.nextDouble());
		//		RotationMatrix3d R = new RotationMatrix3d(axisAng);
		//		
		//		RigidTransform3d T = new RigidTransform3d(p, R);
		//		jaw.transformPose(T);
		((MyJawModel)myJawModel).setJawCentricRotationRef(rand.nextDouble()*MAX_ROTATION);
	}

	@Override
	protected void loadModel() {
		// create jaw model
		try {
			myJawModel = new MyJawModel("jawmodel", /* fixedLaryngeal */true, 
					/* useComplexJoint */ true, 
					/* curvJoint */true);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("RootJaw() - error to instantiating AmiraJaw");
		}

		pokesToControl = new String[] {					
				"lCondylarAngle", "rCondylarAngle",
				"lCondylarCant", "rCondylarCant",
				"lMedWallAngle", "rMedWallAngle", 
				"lPostWallAngle", "rPostWallAngle", 
				"lBiteAngle", "rBiteAngle", 
				"lBiteCant", "rBiteCant" };
		
		
		myJawModel.createAndAddBody("ref_jaw", "jaw_smooth.obj");
		
		
//		RigidBody refJaw = new RigidBody("ref_jaw");
		
	}

	void addMuscleControlPanel()
	{			
		ControlPanel panel = new ControlPanel("activations", "");
		for (AxialSpring s : myJawModel.axialSprings ())
		{
			if (s instanceof Muscle) {
				Muscle m = (Muscle)s;
				String name = (m.getName()==null?"m"+m.getNumber():m.getName().toUpperCase());
				panel.addWidget(name, m, "excitation", 0.0, 1.0);
			}
		}
		addControlPanel (panel);
	}

	@Override
	public void loadControlPanel(RootModel root) {
		String panelNames[] = new String[] { "misc", "damping", "muscles",
		"joints" };
		loadControlPanel(root, panelNames);
		addMuscleControlPanel();
	}

	@Override
	public void addJawOptions(ControlPanel panel)
	{
		if (panel == null)
			return;
		//JawPanel.createJawLarynxPanel(myJawModel, panel);
	}

	@Override
	public void setupJawModel()
	{
		//loadBoluses();

		// show only jaw rigidbody, maxilla, and hyoid
		myJawModel.showMeshFaces(false);
		RenderProps.setVisible(myJawModel.rigidBodies().get("jaw"), true);
		RenderProps.setVisible(myJawModel.rigidBodies().get("maxilla"), true);
		RenderProps.setVisible(myJawModel.rigidBodies().get("hyoid"), true);
		RenderProps.setVisible(myJawModel.rigidBodies().get("ref_jaw"), true);
		
		RenderProps.setFaceColor(myJawModel.rigidBodies().get("ref_jaw"), 
				Color.GREEN);

		// integrator settings
		myJawModel.setMaxStepSize(0.001);		
		myJawModel.setIntegrator(Integrator.BackwardEuler);
		//myJawModel.setMaxStepSize(0.0001);
		//myJawModel.setIntegrator(Integrator.SymplecticEuler);

		// damping settings
		setDampingParams(10, 100, 0.001);
		setJawDamping (20, 200);		

		myJawModel.setThMemStiffness(385.6);
		myJawModel.setCtrMemStiffness(385.6);	

		//addIncisorForce();
		//createIncisorPointForce();
	}	

	@Override
	public void attach(DriverInterface driver) {
		probesFilename = "rightchew.art";
		workingDirname = "data/rlDemo/";
		setWorkingDir();

		if (getControlPanels().size() == 0)  
			loadControlPanel(this);

		this.setViewerEye(new Point3d(0.0, -268.0, -23.0));
		this.setViewerCenter(new Point3d(0.0, 44.0, 55.0));
		setIncisorVisible();

	}

	@Override
	public void setWorkingDir() {
		if (workingDirname == null) return;
		// set default working directory to repository location
		File workingDir = new File (
				ArtisynthPath.getSrcRelativePath(JawRlDemo.class, workingDirname));
		ArtisynthPath.setWorkingDir(workingDir);
		if (debug) {
			System.out.println("Set working directory to "
					+ ArtisynthPath.getWorkingDir().getAbsolutePath());
		}
	}

	@Override
	public void loadProbes() {
		workingDirname = "data/rlDemo/";
		if (probesFilename == null || !myInputProbes.isEmpty()
				|| !myOutputProbes.isEmpty()) return;
		String probeFileFullPath = ArtisynthPath.getWorkingDir().getPath() + "/"
				+ probesFilename;
		System.out.println("Loading Probes from File: " + probeFileFullPath);

		try {
			scanProbes(
					ArtisynthIO.newReaderTokenizer(probeFileFullPath));
		} catch (Exception e) {
			System.out.println("Error reading probe file");
			e.printStackTrace();
		}
	}

}
