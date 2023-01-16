package xyz.sarcly.throwablebrick.util;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

public class BrickEntityHelper {

	
	public static Vec3f getRotation(Vec3d center, Vec3d min, Vec3d max, Vec3f rotation, Vec3d velocity, Vec3f rotaionalVelocity) {
		BrickObj brick = new BrickObj(center, min, max, rotation);
		
		
		return null;
	}
	
	private static class BrickObj{
		private Vec3d center;
		private Vec3d min;
		private Vec3d max;
		private Vec3f rotation;
		
		public BrickObj(Vec3d c, Vec3d min, Vec3d max, Vec3f rot) {
			this.center = c;
			this.min = min;
			this.max = max;
			this.rotation = rot;
		}
		
	}
}
