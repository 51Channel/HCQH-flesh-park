package world.hechao.fleshpark.core;

/** 缆车怎么走：出站慢，食道里稍快，在半路被肉夹停一会儿，空腔里放开跑，到站前慢下来。只看距离和时间，不碰游戏。 */
public final class RideProfile {
    public static final double ACCEL = 0.5, STOP_SECONDS = 15;
    public static final double SPEED_STATION = 0.95, SPEED_THROAT = 0.75, SPEED_CAVITY = 1.75;

    public final double dMouth, dSqueeze, dThroatEnd, dEnd;
    public double d, v, holdLeft;
    public boolean squeezed;

    public RideProfile(Route route) {
        dMouth = route.distAtY(ParkPlan.THROAT_TOP);
        dSqueeze = route.distAtY(ParkPlan.SQUEEZE_Y);
        dThroatEnd = route.distAtY(ParkPlan.THROAT_BOTTOM);
        dEnd = route.length;
    }

    public boolean holding() {
        return holdLeft > 0;
    }

    public boolean done() {
        return d >= dEnd - 0.01 && v < 0.02;
    }

    public void step(double dt) {
        if (holdLeft > 0) {
            holdLeft -= dt;
            v = 0;
            return;
        }
        double target = d < dMouth ? SPEED_STATION : d < dThroatEnd ? SPEED_THROAT : SPEED_CAVITY;
        if (!squeezed) target = Math.min(target, brake(dSqueeze - d));
        target = Math.min(target, brake(dEnd - d));
        v += Math.max(-ACCEL * dt, Math.min(ACCEL * dt, target - v));
        d = Math.min(dEnd, d + v * dt);
        if (!squeezed && d >= dSqueeze - 0.03) {
            d = dSqueeze;
            v = 0;
            squeezed = true;
            holdLeft = STOP_SECONDS;
        }
        if (dEnd - d < 0.01) {
            d = dEnd;
            v = 0;
        }
    }

    private static double brake(double left) {
        return Math.sqrt(Math.max(0, 2 * ACCEL * left)) + 0.04;
    }
}
