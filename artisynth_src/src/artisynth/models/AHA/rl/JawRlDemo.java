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
import maspack.render.Renderer;
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
	protected MechModel model;
	NetworkHandler networkHandler;
	static float MAX_ROTATION = 20;

	Random rand = new Random();	

	@Override
	public StepAdjustment advance(double t0, double t1, int flags)
	{	   	        
		JSONObject jo_receive = networkHandler.getMessage();
		if (jo_receive != null)
		{			
			try {
				switch (jo_receive.getString("type")) 
				{
				case "reset":
					//resetRefPosition();					
					break;
				case "excitations":
					//setExcitations(jo_receive);
					break;
				case "getState":
					sendState();
					break;
				default:
					break;
				}
			}
			catch (JSONException e) {
				log("Error in advance: " + e.getMessage());
			}
		}
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
		addMidPoint();
		addleftPoint();
		addrightPoint();
		addrefrightPoint();
		addrefleftPoint();
		addrefMidPoint();
		//sendState();
		networkHandler = new NetworkHandler();
		networkHandler.start ();
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
		((MyJawModel)myJawModel).setJawCentricRotation(rand.nextDouble()*MAX_ROTATION);
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
		
				RigidBody refJaw = new RigidBody("ref_jaw");
		
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

	
    public void addMidPoint()
    {
        //RigidBody lowerArm = model.rigidBodies().get("lower");
        RigidBody jaw  = ((MyJawModel)myJawModel).rigidBodies().get("jaw");
        System.out.println(jaw);
        if (jaw==null)
        {
            return;
        }
        
        FrameMarker midPoint = new FrameMarker();
        midPoint.setName("MidPoint");
        midPoint.setFrame(jaw);
        midPoint.setLocation(new Point3d(0 ,-47.9584 ,41.7642)); // location of the frame marker 
        ((MyJawModel)myJawModel).addFrameMarker(midPoint);
        //lowerArm.addMarker(endPoint);
        
        RenderProps rp = new RenderProps(((MyJawModel)myJawModel).getRenderProps());
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setPointColor(Color.RED);
        rp.setPointRadius(2.0);   // radius of the frame marker
        midPoint.setRenderProps(rp);
    }
	
    public void addrefMidPoint()
    {
        //RigidBody lowerArm = model.rigidBodies().get("lower");
        RigidBody ref_jaw  = ((MyJawModel)myJawModel).rigidBodies().get("ref_jaw");
        System.out.println(ref_jaw);
        if (ref_jaw==null)
        {
            return;
        }
        
        FrameMarker Refjaw_midPoint = new FrameMarker();
        Refjaw_midPoint.setName("RefJawMidPoint");
        Refjaw_midPoint.setFrame(ref_jaw);
        Refjaw_midPoint.setLocation(new Point3d(0 ,-47.9584 ,41.7642)); // location of the frame marker 
        ((MyJawModel)myJawModel).addFrameMarker(Refjaw_midPoint);
        //lowerArm.addMarker(endPoint);
        
        RenderProps rp = new RenderProps(((MyJawModel)myJawModel).getRenderProps());
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setPointColor(Color.RED);
        rp.setPointRadius(2.0);   // radius of the frame marker
        Refjaw_midPoint.setRenderProps(rp);
    }
    
    
    public void addleftPoint()
    {
        //RigidBody lowerArm = model.rigidBodies().get("lower");
        RigidBody jaw  = ((MyJawModel)myJawModel).rigidBodies().get("jaw");
        System.out.println(jaw);
        if (jaw==null)
        {
            return;
        }
        
        FrameMarker lPoint = new FrameMarker();
        lPoint.setName("LeftPoint");
        lPoint.setFrame(jaw);
        lPoint.setLocation(new Point3d(-20.9584 ,-47.9584 ,41.7642)); // location of the frame marker 
        ((MyJawModel)myJawModel).addFrameMarker(lPoint);
        //lowerArm.addMarker(endPoint);
        
        RenderProps rp = new RenderProps(myJawModel.getRenderProps());
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setPointColor(Color.RED);
        rp.setPointRadius(2.0);   // radius of the frame marker
        lPoint.setRenderProps(rp);
    }
	
    public void addrefleftPoint()
    {
        //RigidBody lowerArm = model.rigidBodies().get("lower");
        RigidBody ref_jaw  = ((MyJawModel)myJawModel).rigidBodies().get("ref_jaw");
        System.out.println(ref_jaw);
        if (ref_jaw==null)
        {
            return;
        }
        
        FrameMarker Refjaw_lPoint = new FrameMarker();
        Refjaw_lPoint.setName("RefJawLeftPoint");
        Refjaw_lPoint.setFrame(ref_jaw);
        Refjaw_lPoint.setLocation(new Point3d(-20.9584 ,-47.9584 ,41.7642)); // location of the frame marker 
        ((MyJawModel)myJawModel).addFrameMarker(Refjaw_lPoint);
        //lowerArm.addMarker(endPoint);
        
        RenderProps rp = new RenderProps(myJawModel.getRenderProps());
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setPointColor(Color.RED);
        rp.setPointRadius(2.0);   // radius of the frame marker
        Refjaw_lPoint.setRenderProps(rp);
    }
    
    public void addrightPoint()
    {
        //RigidBody lowerArm = model.rigidBodies().get("lower");
        RigidBody jaw  = ((MyJawModel)myJawModel).rigidBodies().get("jaw");
        System.out.println(jaw);
        if (jaw==null)
        {
            return;
        }
        
        FrameMarker rPoint = new FrameMarker();
        rPoint.setName("RightPoint");
        rPoint.setFrame(jaw);
        rPoint.setLocation(new Point3d(21.591799, -34.198157, 39.897917)); // location of the frame marker 
        ((MyJawModel)myJawModel).addFrameMarker(rPoint);
        //lowerArm.addMarker(endPoint);
        
        RenderProps rp = new RenderProps(((MyJawModel)myJawModel).getRenderProps());
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setPointColor(Color.RED);
        rp.setPointRadius(2.0);   // radius of the frame marker
        rPoint.setRenderProps(rp);
    }
    
    public void addrefrightPoint()
    {
        //RigidBody lowerArm = model.rigidBodies().get("lower");
        RigidBody ref_jaw  = ((MyJawModel)myJawModel).rigidBodies().get("ref_jaw");
        System.out.println(ref_jaw);
        if (ref_jaw==null)
        {
            return;
        }
        
        FrameMarker Refjaw_rPoint = new FrameMarker();
        Refjaw_rPoint.setName("RefJawRightPoint");
        Refjaw_rPoint.setFrame(ref_jaw);
        Refjaw_rPoint.setLocation(new Point3d(21.591799, -34.198157, 39.897917)); // location of the frame marker 
        ((MyJawModel)myJawModel).addFrameMarker(Refjaw_rPoint);
        //lowerArm.addMarker(endPoint);
        
        RenderProps rp = new RenderProps(((MyJawModel)myJawModel).getRenderProps());
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setPointColor(Color.RED);
        rp.setPointRadius(2.0);   // radius of the frame marker
        Refjaw_rPoint.setRenderProps(rp);
    }
    
    
    private void sendState()
	{
//		try {
//		Thread.sleep(200);
//		} catch (InterruptedException e) {
//			Log.log("Error in sleep sendState: " + e.getMessage());
//		}
		JSONObject jo_send_state = new JSONObject ();
		//RigidBody midPoint = ((MyJawModel)myJawModel).rigidBodies ().get ("MidPoint");
		//RigidBody Refjaw_midPoint = ((MyJawModel)myJawModel).rigidBodies ().get ("RefJawMidPoint");
		FrameMarker midPoint = ((MyJawModel)myJawModel).frameMarkers ().get ("MidPoint");
		FrameMarker Refjaw_midPoint = ((MyJawModel)myJawModel).frameMarkers ().get ("RefJawMidPoint");
		FrameMarker lPoint = ((MyJawModel)myJawModel).frameMarkers ().get ("LeftPoint");
		FrameMarker Refjaw_lPoint = ((MyJawModel)myJawModel).frameMarkers ().get ("RefJawLeftPoint");
		FrameMarker rPoint = ((MyJawModel)myJawModel).frameMarkers ().get ("RightPoint");
		FrameMarker Refjaw_rPoint = ((MyJawModel)myJawModel).frameMarkers ().get ("RefJawRightPoint");
		
		try {
			//System.out.println(midPoint.getPosition ());
			jo_send_state.put ("type", "state");
			jo_send_state.put ("MidPoint", midPoint.getPosition ());
			jo_send_state.put ("RefJawMidPoint", Refjaw_midPoint.getPosition ());
			jo_send_state.put ("LeftPoint", lPoint.getPosition ());
			jo_send_state.put ("RefJawLeftPoint", Refjaw_lPoint.getPosition ());
			jo_send_state.put ("RightPoint", rPoint.getPosition ());
			jo_send_state.put ("RefJawRightPoint", Refjaw_rPoint.getPosition ());
			//jo_send_state.put ("RefJawMidPoint", body_follower.getPosition ());
			//          print(jo.toString ());
			networkHandler.send (jo_send_state);			
		}
		catch (JSONException e)
		{
			System.out.println("Error in send: " + e.getMessage ());          
		} 
	}

    @Override
	public void finalize() throws Throwable
	{
		networkHandler.closeConnection();
		super.finalize();

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
		addMidPoint();

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
