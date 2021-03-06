package me.drton.jmavsim;

import me.drton.jmavlib.mavlink.MAVLinkMessage;
import me.drton.jmavlib.mavlink.MAVLinkSchema;
import me.drton.jmavsim.vehicle.AbstractVehicle;

import javax.vecmath.Vector3d;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * MAVLinkHILSystem is MAVLink bridge between AbstractVehicle and autopilot connected via MAVLink.
 * <p/>
 * User: ton Date: 13.02.14 Time: 22:04
 */
public class MAVLinkHILSystem extends MAVLinkSystem {
    // MAVLinkHILSystem has the same sysID as autopilot, but different componentId
    private AbstractVehicle vehicle;
    private boolean gotHeartBeat = false;
    private boolean inited = false;
    private long initTime = 0;
    private long initDelay = 1000;

    /**
     * Create MAVLinkHILSimulator, MAVLink system thet sends simulated sensors to autopilot and passes controls from
     * autopilot to simulator
     *
     * @param sysId       SysId of simulator should be the same as autopilot
     * @param componentId ComponentId of simulator should be different from autopilot
     * @param vehicle
     */
    public MAVLinkHILSystem(MAVLinkSchema schema, int sysId, int componentId, AbstractVehicle vehicle) {
        super(schema, sysId, componentId);
        this.vehicle = vehicle;
    }

    @Override
    public void handleMessage(MAVLinkMessage msg) {
        super.handleMessage(msg);
        long t = System.currentTimeMillis();
        if ("HIL_CONTROLS".equals(msg.getMsgName())) {
            List<Double> control = Arrays.asList(msg.getDouble("roll_ailerons"), msg.getDouble("pitch_elevator"),
                    msg.getDouble("yaw_rudder"), msg.getDouble("throttle"), msg.getDouble("aux1"),
                    msg.getDouble("aux2"), msg.getDouble("aux3"), msg.getDouble("aux4"));
            vehicle.setControl(control);
        } else if ("HEARTBEAT".equals(msg.getMsgName())) {
            if (!gotHeartBeat && sysId == msg.getInt(sysId)) {
                gotHeartBeat = true;
                initTime = t + initDelay;
            }
            if (!inited && t > initTime) {
                System.out.println("Init MAVLink");
                initMavLink();
                inited = true;
            }
            if ((msg.getInt("base_mode") & 128) == 0) {
                vehicle.setControl(Collections.<Double>emptyList());
            }
        } else if ("STATUSTEXT".equals(msg.getMsgName())) {
            System.out.println("MSG: " + msg.getString("text"));
        }
    }

    @Override
    public void update(long t) {
        // Don't call super.update(), because heartbeats already sent by autopilot
        long tu = t * 1000;
        Sensors sensors = vehicle.getSensors();
        // Sensors
        MAVLinkMessage msg_sensor = new MAVLinkMessage(schema, "HIL_SENSOR", sysId, componentId);
        msg_sensor.set("time_usec", tu);
        Vector3d acc = sensors.getAcc();
        msg_sensor.set("xacc", acc.x);
        msg_sensor.set("yacc", acc.y);
        msg_sensor.set("zacc", acc.z);
        Vector3d gyro = sensors.getGyro();
        msg_sensor.set("xgyro", gyro.x);
        msg_sensor.set("ygyro", gyro.y);
        msg_sensor.set("zgyro", gyro.z);
        Vector3d mag = sensors.getMag();
        msg_sensor.set("xmag", mag.x);
        msg_sensor.set("ymag", mag.y);
        msg_sensor.set("zmag", mag.z);
        msg_sensor.set("pressure_alt", sensors.getPressureAlt());
        sendMessage(msg_sensor);
        // GPS
        if (sensors.isGPSUpdated()) {
            GPSPosition gps = sensors.getGPS();
            if (gps != null && gps.position != null && gps.velocity != null) {
                MAVLinkMessage msg_gps = new MAVLinkMessage(schema, "HIL_GPS", sysId, componentId);
                msg_gps.set("time_usec", tu);
                msg_gps.set("lat", (long) (gps.position.lat * 1e7));
                msg_gps.set("lon", (long) (gps.position.lon * 1e7));
                msg_gps.set("alt", (long) (gps.position.alt * 1e3));
                msg_gps.set("vn", (int) (gps.velocity.x * 100));
                msg_gps.set("ve", (int) (gps.velocity.y * 100));
                msg_gps.set("vd", (int) (gps.velocity.z * 100));
                msg_gps.set("eph", (int) (gps.eph * 100));
                msg_gps.set("epv", (int) (gps.epv * 100));
                msg_gps.set("vel", (int) (gps.getSpeed() * 100));
                msg_gps.set("cog", (int) (gps.getCog() / Math.PI * 18000.0));
                msg_gps.set("fix_type", gps.fix);
                msg_gps.set("satellites_visible", 10);
                sendMessage(msg_gps);
            }
        }
    }

    private void initMavLink() {
        // Set HIL mode
        MAVLinkMessage msg = new MAVLinkMessage(schema, "SET_MODE", sysId, componentId);
        msg.set("target_system", sysId);
        msg.set("base_mode", 32);     // HIL, disarmed
        sendMessage(msg);
    }
}
