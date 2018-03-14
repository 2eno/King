package eu.zipf.zeno.kingo;

import android.util.Log;
import android.widget.ToggleButton;

import com.google.android.gms.games.multiplayer.Participant;

import java.util.ArrayList;

public class Game {
    ArrayList<LocalPlayer> players;
    ArrayList<ToggleButton> dices;
    static DiceSet dc;
    int participantCnt;
    public static byte DICE_RESULTS = 10;

    public Game() {
        initialize();

    }

    private void initialize() {
        players = new ArrayList<>();
        dc = new DiceSet(6);
        this.participantCnt = MainActivity.mParticipants.size();
        fillPlayers();
    }

    private void fillPlayers() {

        int i = 0;
        for (Participant p : MainActivity.mParticipants) {

            players.add(new LocalPlayer(p.getDisplayName(), i));
            i++;
        }
    }

    public static void updateDices() {
        dc.getScores();
        //updateUI
    }


    public static void main(String args[]) {
        new Game();

    }

    public static void rollDices() {
        dc.roll();
        Log.d(MainActivity.TAG, "Buttong R0LL");
    }
    public static void rerollDices(byte[] index){
        dc.reroll(index);
    }


    public void broadcastDice() {
       ArrayList<Byte> toBrod = new ArrayList<Byte>();
       toBrod.add(DICE_RESULTS);
       byte[] scores = dc.getScores();
       for(byte b:scores){
           toBrod.add(b);
       }
       MainActivity.broadcast(toBrod);
    }

    public DiceSet getDiceSet(){
        return dc;
    }

}
