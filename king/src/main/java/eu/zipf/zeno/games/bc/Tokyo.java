package eu.zipf.zeno.games.bc;

import java.util.ArrayList;

public class Tokyo {
    private static Player player;

    public Tokyo() {

    }

    public static Player getPlayer() {
        return player;
    }

    public static void setPlayer(Player p) {
        player = p;
    }

    public static boolean isEmpty() {
        return player == null;
    }

    public static void dealDamage(ArrayList<Player> players,int damage) {
        for(int i = 0;i<players.size();i++){
            players.get(i).damage(damage);
        }

    }

}
