package artisynth.models.AHA.rl;


import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.JFrame;

import org.json.JSONException;
import org.json.JSONObject;

import artisynth.core.driver.Main;
import artisynth.core.gui.*;
import artisynth.core.mechmodels.*;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.probes.NumericInputProbe;
import artisynth.core.probes.NumericOutputProbe;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.util.TimeBase;
import artisynth.core.workspace.RootModel;
import artisynth.models.AHA.rl.PointModel2dRl.DemoType;
import maspack.geometry.MeshFactory;
import maspack.geometry.PolygonalMesh;
import maspack.matrix.*;
import maspack.properties.Property;
import maspack.render.*;
import maspack.spatialmotion.SpatialInertia;

public class muscle_arm extends RootModel
{
	public muscle_arm() {
		Log.logging = true;
	}
	
	NetworkHandler networkHandler;
	
   public static final String meshDir = ArtisynthPath.getHomeRelativePath(
      "src/artisynth/demos/mech/geometry/",".");
    protected MechModel model;
    protected FrameMarker end_refpoint, end_point;
    double len = 20;
    Vector3d size = new Vector3d(len/10,len/5,len);
    boolean addCompression = true;
    
   public void build (String[] args) throws IOException {

        model = new MechModel("Arm");
        addModel(model);
             
        model.setIntegrator(MechSystemSolver.Integrator.RungeKutta4);
        model.setMaxStepSize(0.01);

        setupRenderProps();
        
        addRigidBodies();
        addJoint();
        
        addMuscle();
        
//        addAntagonist();
        addEndPoint();
        addEndRefPoint();
        addControlPanel();
        addProbes();
        networkHandler = new NetworkHandler();
		networkHandler.start ();
    }
    
    public void addRigidBodies()
    {
        RigidTransform3d X;
        
        X= new RigidTransform3d();
        X.p.z = len/2;
        addBody("upper",X, "bone.obj");
        
        X = new RigidTransform3d();
        double angle = Math.toRadians (32);
        X.R.setAxisAngle(0,1,0,angle);
        X.p.set(-len/2*Math.sin(angle), 0.0,-len/2*Math.cos(angle));
        
        addBody("lower",X, "bone_square.obj");        
    }
    
    public void addBody(String name, RigidTransform3d pose, 
                String meshName)
    {
        // add a simple rigid body to the simulation
        
        RigidBody rb = new RigidBody();
        rb.setName(name);
        rb.setPose(pose);

        model.addRigidBody(rb);
        
        PolygonalMesh mesh;
        try
        {
            String meshFilename = meshDir + meshName;
            mesh = new PolygonalMesh();
            mesh.read(
               new BufferedReader(
                    new FileReader(
                         new File(meshFilename))));
            rb.setMesh(mesh, meshFilename);
        }
        catch(IOException e)
        {
            System.out.println(e.getMessage());
            mesh = MeshFactory.createBox(size.x, size.y, size.z);
            rb.setMesh(mesh, null);
        }

        rb.setInertia(SpatialInertia.createBoxInertia(
                    10.0, size.x, size.y, size.z));
        
        RenderProps rp = new RenderProps(model.getRenderProps());
        rp.setFaceColor(Color.GRAY);
        rp.setShading(Renderer.Shading.FLAT);
        rb.setRenderProps(rp);

        rb.setFrameDamping (10);
        rb.setRotaryDamping (10000.0);
    }
    
    public void addJoint()
    {
        RigidBody upperArm = model.rigidBodies().get("upper");
        RigidBody lowerArm = model.rigidBodies().get("lower");
        if (upperArm==null || lowerArm==null)
        {
            return;
        }
        
        RevoluteJoint j = new RevoluteJoint();
        j.setName("elbow");
        
        RigidTransform3d TCA = new RigidTransform3d();
        TCA.p.z = len/2;
        TCA.R.setAxisAngle(1,0,0,Math.PI/2);
        RigidTransform3d TCW = new RigidTransform3d();
        TCW.R.setAxisAngle(1,0,0,Math.PI/2);

        j.setBodies (lowerArm, TCA, null, TCW);
        j.setAxisLength(len/3);
        model.addBodyConnector(j);
        
        upperArm.setDynamic(false);
    }
    
    public void addMuscle()
    {
        RigidBody upperArm = model.rigidBodies().get("upper");
        RigidBody lowerArm = model.rigidBodies().get("lower");
        if (upperArm==null || lowerArm==null)
        {
            return;
        }
        
        Point3d markerBodyPos = new Point3d(-size.x/2,0,(size.z/2.0)/1.2);
        FrameMarker u = new FrameMarker();
        model.addFrameMarker(u, upperArm, markerBodyPos);
        u.setName("upperAttachment");
        
        markerBodyPos = new Point3d(size.x/2,0,-(size.z/2.0)/2);
        FrameMarker l = new FrameMarker();
        model.addFrameMarker(l,lowerArm, markerBodyPos);
        l.setName("lowerAttachment");
        
        Muscle muscle = new Muscle("muscle");
        muscle.setPeckMuscleMaterial(40.0, 22.0, 30, 0.2, 0.5, 0.1);
        muscle.setFirstPoint(u);
        muscle.setSecondPoint(l);
        
        RenderProps rp = new RenderProps(model.getRenderProps());
        rp.setLineStyle(Renderer.LineStyle.SPINDLE);
        rp.setLineRadius(len/20);
        //rp.setLineSlices(10);
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setLineColor(Color.RED);
        muscle.setRenderProps(rp);
        
        model.addAxialSpring(muscle);
        
        if (addCompression)
        {
           markerBodyPos = new Point3d(size.x/2,0,+size.z/2.0);
           FrameMarker l2 = new FrameMarker();
           model.addFrameMarker(l2, lowerArm, markerBodyPos);
           l2.setName("lowerAttachmentCompressor");
           
           double len = u.getPosition().distance(l2.getPosition());
           AxialSpring s = new AxialSpring(10, 0, 50);
           s.setFirstPoint(u);
           s.setSecondPoint(l2);
           model.addAxialSpring(s);
           RenderProps props = new RenderProps();
           props.setLineStyle(Renderer.LineStyle.CYLINDER);
           props.setLineRadius(0.0);
           s.setRenderProps(props);
        }
    }
        
    private void sendState()
	{
//		try {
//		Thread.sleep(200);
//		} catch (InterruptedException e) {
//			Log.log("Error in sleep sendState: " + e.getMessage());
//		}
		JSONObject jo_send_state = new JSONObject ();
		FrameMarker end_refpoint = model.frameMarkers ().get ("endRefPoint");
		FrameMarker end_point = model.frameMarkers ().get ("endPoint");
		try {
			jo_send_state.put ("type", "state");
			jo_send_state.put ("ref_pos", end_refpoint.getPosition ());
			jo_send_state.put ("follow_pos", end_point.getPosition ());
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

	private void setExcitations (JSONObject jo_rec) throws JSONException // TODO: set Muscle activations that came for network
	{
		//Random rand = new Random ();
		//double value = rand.nextDouble ();
		for(double i = 0; i < 1.0; i+=0.25) //while (value) 
		{   
			
			//setExcitation (double i);
			
			//m instanceof Muscle;
			//AxialSpring m = model.axialSprings(;)
			AxialSpring m = model.axialSprings().get(1); // 1? any integer would work??
			{
				if (m instanceof Muscle)               
					((Muscle)m).setExcitation (i);            
			}
		}
		log("Exications filled");

	}

	public StepAdjustment advance(double t0, double t1, int flags)
	{	   	        
		JSONObject jo_receive = networkHandler.getMessage();
		if (jo_receive != null)
		{			
			try {
				switch (jo_receive.getString("type")) 
				{
				case "reset":
					//artisynth.core.driver.Main.getMain().waitForStop();
//					artisynth.core.driver.Main.getMain().pause();
//					artisynth.core.driver.Main.getMain().reset();
//					artisynth.core.driver.Main.getMain().play();
					resetRefPosition();	   // ToDO				
					break;
				case "excitations":
					setExcitations(jo_receive);
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
		return super.advance (t0, t1, flags);
	}

	private void resetRefPosition() 
	{	   
		RigidBody end_pointref = model.rigidBodies ().get ("end_pointref");
		Point3d pos = getRandomTarget (new Point3d (0, 0,0), 2.89);  // Muscle point radius(point model 2d) = 2.89               
		end_pointref .setPosition (pos);
	}

//	private void sendState()
//	{
////		try {
////		Thread.sleep(200);
////		} catch (InterruptedException e) {
////			Log.log("Error in sleep sendState: " + e.getMessage());
////		}
//		JSONObject jo_send_state = new JSONObject ();
//		RigidBody end_refpoint = model.rigidBodies ().get ("end_refpoint");
//		RigidBody end_point = model.rigidBodies ().get ("end_point");
//		try {
//			jo_send_state.put ("type", "state");
//			jo_send_state.put ("ref_pos", end_refpoint.getPosition ());
//			jo_send_state.put ("follow_pos", end_point.getPosition ());
//			//          print(jo.toString ());
//			networkHandler.send (jo_send_state);			
//		}
//		catch (JSONException e)
//		{
//			System.out.println("Error in send: " + e.getMessage ());          
//		} 
//	}

	
// TODO : setting muscle activation    
//	private void setExcitations (JSONObject jo_rec) throws JSONException
//	{
//		for (String label : muscleLabels) 
//		{       
//			AxialSpring m = model.axialSprings().get (label);
//			{
//				if (m instanceof Muscle)               
//					((Muscle)m).setExcitation (jo_rec.getDouble (label));            
//			}
//		}
//		log("Exications filled");
//
//	}

	public void log(Object obj)
	{
		System.out.println (obj);
	}

	
	public Point3d getRandomTarget(Point3d center, double radius)
	{		
		Random rand = new Random ();      
		Vector3d targetVec = new Vector3d(rand.nextDouble ()-0.5, 
				rand.nextDouble ()-0.5, 
				rand.nextDouble ()-0.5);
		//if (myDemoType == DemoType.Point2d)
			targetVec.y = 0; 	 
		//if (myDemoType == DemoType.Point1d)   // coordinates needs to be done
			targetVec.z = 5.345; 	 
		
		targetVec.scale (radius*2);
		Point3d targetPnt = new Point3d (targetVec.x, targetVec.y, targetVec.z);
		return targetPnt;
	}
	
	
    public void addAntagonist()
    {
        RigidBody lowerArm = model.rigidBodies().get("lower");
        if (lowerArm==null)
        {
            return;
        }
        
        Point3d markerBodyPos = new Point3d(-size.x/2,0,0);
//      Point3d markerBodyPos = new Point3d(-size.x,0,-(size.z/2.0)/1.2);
        FrameMarker marker = new FrameMarker();
        model.addFrameMarker(marker, lowerArm, markerBodyPos);
        
        Particle fixed = new Particle(1.0,new Point3d(-size.z/4,0,-size.z/2.0));
//        Particle fixed = new Particle(1.0,new Point3d(size.z/4,0,size.z));        
        fixed.setDynamic(false);
        model.addParticle(fixed);

        AxialSpring spring = new AxialSpring(100.0, 2.0, 0.0 );
        spring.setFirstPoint(marker);
        spring.setSecondPoint(fixed);
        
        RenderProps rp = new RenderProps(model.getRenderProps());
        rp.setLineStyle(Renderer.LineStyle.SPINDLE);
        rp.setShading(Renderer.Shading.FLAT);
        rp.setLineColor(Color.WHITE);
        spring.setRenderProps(rp);
        
        model.addAxialSpring(spring);
        
    }
    
    public void addLoad()
    {
        RigidBody lowerArm = model.rigidBodies().get("lower");
        if (lowerArm==null)
        {
            return;
        }
        
        double mass = 1.0;
        Particle load = new Particle(mass,new Point3d(-14.14,0,-14.14));
        load.setName("load");
//        Particle load = new Particle(mass,new Point3d(0,0,0));
        
        RenderProps rp = new RenderProps(model.getRenderProps());
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setPointColor(Color.ORANGE);
        rp.setPointRadius(len/20);
        load.setRenderProps(rp);
        
        model.addParticle(load);
        model.attachPoint(load, lowerArm, new Point3d(0,0,len/2));
        
    }
    
    public void addEndPoint()
    {
        RigidBody lowerArm = model.rigidBodies().get("lower");
        System.out.println(lowerArm);
        if (lowerArm==null)
        {
            return;
        }
        
        FrameMarker endPoint = new FrameMarker();
        endPoint.setName("endPoint");
        endPoint.setFrame(lowerArm);
        endPoint.setLocation(new Point3d(0,0,-len/2));
        model.addFrameMarker(endPoint);
        //lowerArm.addMarker(endPoint);
        
        RenderProps rp = new RenderProps(model.getRenderProps());
        rp.setShading(Renderer.Shading.SMOOTH);
        rp.setPointColor(Color.ORANGE);
        rp.setPointRadius(len/20);
        endPoint.setRenderProps(rp);
    }

    public void addEndRefPoint()
    {
        RigidBody upperArm = model.rigidBodies().get("upper");
        if (upperArm==null)
        {
            return;
        }
        
        FrameMarker endRefPoint = new FrameMarker();
        endRefPoint.setName("endRefPoint");
        endRefPoint.setFrame(upperArm);
        endRefPoint.setLocation(new Point3d(-20.2991926, 0, -8.480481)); //-10.598385 0 -16.960962
        model.addFrameMarker(endRefPoint);
        //lowerArm.addMarker(endPoint);
        
        RenderProps rpref = new RenderProps(model.getRenderProps());
        rpref.setShading(Renderer.Shading.SMOOTH);
        rpref.setPointColor(Color.GREEN);
        rpref.setPointRadius(len/20);
        endRefPoint.setRenderProps(rpref);
    }

    
    
    
    public void setupRenderProps()
    {
       // set render properties for model
       
       RenderProps rp = new RenderProps();
       rp.setPointStyle(Renderer.PointStyle.SPHERE);
       rp.setPointColor(Color.LIGHT_GRAY);
       rp.setPointRadius(0.0);
       rp.setLineStyle(Renderer.LineStyle.SPINDLE);
       rp.setLineColor(Color.WHITE);
       rp.setLineRadius(0.4);
       model.setRenderProps(rp);
    }
    
    protected ControlPanel panel;

    public void addControlPanel ()
    {
        panel = new ControlPanel("Muscle Control", "");
        panel.addWidget (
           "Activation", model, "axialSprings/muscle:excitation", 0.0, 1.0);
        addControlPanel (panel);
   }
    
    public void addProbes()
    {
        NumericInputProbe ip;
        NumericOutputProbe op;
        double rate = 0.01;
        try 
        {
           String path = ArtisynthPath.getHomeRelativePath(
              "src/artisynth/demos/mech/", ".");
           ip = new NumericInputProbe(model,
        	 "axialSprings/muscle:excitation",
        	 path+"muscleArmActivation.txt");
           ip.setStartStopTimes (0, 10.0);
           ip.setName("Muscle Activation");
           //ip.setActive (false);
           addInputProbe(ip);
           
           
//            op = new NumericOutputProbe(model,
//            	 "axialSprings/muscle/forceNorm",
//            	 "muscleForce.txt", rate);
//            op.setName("Muscle Force");
//            op.setStartStopTimesSec (0, 10.0);
//            addOutputProbe(op);
           
//            op = new NumericOutputProbe(model,
//               	 "frameMarkers/endPoint/displacement",
//               	 "displacement.txt", rate);
//            op.setName("End Point Displacement");
//            op.setStartStopTimesSec (0, 10.0);
//           addOutputProbe(op);
        }
        catch (Exception e)
        {
           System.out.println(e.getMessage());
        }
        
    }
}
