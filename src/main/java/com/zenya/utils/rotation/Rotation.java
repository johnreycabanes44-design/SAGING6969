package com.zenya.utils.rotation;

/**
 * A yaw/pitch pair in degrees, in Minecraft's convention: yaw grows clockwise
 * from south, pitch is positive looking down.
 *
 * <p>Held as doubles because the maths that produces them (atan2, wrapDegrees)
 * runs in double precision; callers narrow to float only when handing the values
 * back to the player entity.
 */
public record Rotation(double yaw, double pitch) {
}
