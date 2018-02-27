package artisynth.models.AHA.rl;

import java.io.IOException;

public class PointModelGenericRl extends PointModel2dRl
{
	int port = 6010;
	int num_particles = 12;
	public void build (String[] args) throws IOException
	{				
		parseArgs(args);
		super.build(myDemoType, args);		
	}
	private void parseArgs(String[] args) {
		for (int i = 0; i< args.length; i+=2)
		{
			if (args[i].equals("-demoType"))
			{				
				switch (Integer.parseInt(args[i+1])) 
				{
				case 2:
					myDemoType = DemoType.Point2d;
					break;
				case 3:
					myDemoType = DemoType.Point3d;
					break;				
				}
				args[i] = "";
				args[i+1] = "";
			} 
			else if (args[i].equals("-num"))
			{
				num_particles = Integer.parseInt(args[i+1]);
				muscleLabels = new String[num_particles];
				for (int m = 0 ; m < num_particles; ++m)
					muscleLabels[m] = "m" + Integer.toString(m);

				switch (num_particles)
				{
				case 4:					
				case 8:
				case 12:
				case 16:
				}
				args[i] = "";
				args[i+1] = "";
			}
			else if (args[i].equals("-port"))
			{
				port = Integer.parseInt(args[i+1]);				
			}
			else if (args[i].equals("-muscleOptLen"))
			{
				muscleOptLen = Double.parseDouble(args[i+1]);
			}
		}		
	}
	
	

}
