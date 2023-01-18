package xyz.sarcly.throwablebrick.util;

import xyz.sarcly.throwablebrick.ThrowableBrick;

public class Rotation {
	protected float pitch;
    protected float yaw;
    protected float roll;
    
	protected float prevPitch = 0;
    protected float prevYaw = 0;
    protected float prevRoll = 0;

    public Rotation(float pitch, float yaw, float roll) {
        setRotation(pitch, yaw, roll);
    }
    
    public Rotation(float pitch, float yaw, float roll, float prevPitch, float prevYaw, float prevRoll) {
    	this.pitch = pitch;
    	this.yaw = yaw;
    	this.roll = roll;
    	this.prevPitch = prevPitch;
    	this.prevYaw = prevYaw;
    	this.prevRoll = prevRoll;
    }

    public void setRotation(float pitch, float yaw, float roll) {
    	setPitch(pitch);
    	setYaw(yaw);
    	setRoll(roll);
    }
    
    public void setPitch(float pitch) {
		if (!Float.isFinite(pitch)) {
			ThrowableBrick.LOGGER.error("Invalid entity pitch: " + pitch + ", discarding.");
			this.pitch = 0.0f; 
		} else {
			float p = ((pitch + 180) % 360) - 180;
			if (pitch >= 180) {
				this.prevPitch = p - (pitch - this.prevPitch);
			} else if (pitch < -180) {
				this.prevPitch = p + (pitch - this.prevPitch);
			} else {
				this.prevPitch = this.getPitch();
			}
			this.pitch = p;
		}
	}

	public void setYaw(float yaw) {
		if (!Float.isFinite(yaw)) {
			ThrowableBrick.LOGGER.error("Invalid entity yaw: " + yaw + ", discarding.");
			this.yaw = 0.0f;
		} else {
			float y = ((yaw + 180) % 360) - 180;
			if (yaw >= 180) {
				this.prevYaw = y - (yaw - this.prevYaw);
			} else if (yaw < -180) {
				this.prevYaw = y + (yaw - this.prevYaw);
			} else {
				this.prevYaw = this.getYaw();
			}
			this.yaw = y;
		}
	}

	public void setRoll(float roll) {
		if (!Float.isFinite(roll)) {
			ThrowableBrick.LOGGER.error("Invalid entity roll: " + roll + ", discarding.");
			this.roll = 0.0f;
		} else {
			float r = ((roll + 180) % 360) - 180;
			if (roll >= 180) {
				this.prevRoll = r - (roll - this.prevRoll);
			} else if (roll < -180) {
				this.prevRoll = r + (roll - this.prevRoll);
			} else {
				this.prevRoll = this.roll;
			}
			this.roll = r;
		}
	}
    
    public float getPitch() {
        return this.pitch;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getRoll() {
        return this.roll;
    }
    
    public float getPrevPitch() {
        return this.prevPitch;
    }

    public float getPrevYaw() {
        return this.prevYaw;
    }

    public float getPrevRoll() {
        return this.prevRoll;
    }    
}
