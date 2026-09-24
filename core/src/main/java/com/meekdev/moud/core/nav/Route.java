package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public record Route(Status status, List<Waypoint> waypoints) {

    public enum Status { SUCCESS, CLOSEST, NO_PATH, TOO_FAR }

    public static final Route NO_PATH = new Route(Status.NO_PATH, List.of());
    public static final Route TOO_FAR = new Route(Status.TOO_FAR, List.of());

    public boolean found() {
        return status == Status.SUCCESS || status == Status.CLOSEST;
    }

    public List<Waypoint> ahead() {
        return waypoints.size() > 1 ? waypoints.subList(1, waypoints.size()) : waypoints;
    }

    public List<Vector3> positions() {
        List<Vector3> out = new ArrayList<>();
        for (Waypoint waypoint : ahead()) out.add(waypoint.position());
        return out;
    }
}
