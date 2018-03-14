package eu.zipf.zeno.kingo;

import java.util.ArrayList;

public class Tokyo {
    private static LocalPlayer localPlayer;

    public Tokyo() {

    }

    public static LocalPlayer getLocalPlayer() {
        return localPlayer;
    }

    public static void setLocalPlayer(LocalPlayer p) {
        localPlayer = p;
    }

    public static boolean isEmpty() {
        return localPlayer == null;
    }

    public static void dealDamage(ArrayList<LocalPlayer> localPlayers, int damage) {
        for(int i = 0; i< localPlayers.size(); i++){
            localPlayers.get(i).damage(damage);
        }

    }

}
