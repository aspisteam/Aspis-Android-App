package aspisteam.aspis;

import com.google.firebase.database.IgnoreExtraProperties;

import gov.nasa.worldwind.geom.Position;

@IgnoreExtraProperties
public class FireSpot {

    public long ID;
    public long createdAt;
    public boolean active;
    public double lat;
    public double lon;

    public FireSpot() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    public FireSpot(long ID, long createdAt, boolean active, double lat, double lon) {
        this.ID = ID;
        this.createdAt = createdAt;
        this.active = active;
        this.lat = lat;
        this.lon = lon;
    }

    public String toString(){
        return "|ID: "+ID+" Lat: "+lat+" Long: "+lon+"|";
    }

    public Position getPosition(){
        return new Position(lat,lon,0);
    }

}